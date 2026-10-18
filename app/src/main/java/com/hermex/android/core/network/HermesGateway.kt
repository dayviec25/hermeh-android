package com.hermex.android.core.network

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

sealed interface GatewayStatus {
    data object Idle : GatewayStatus
    data object Connecting : GatewayStatus
    data class Ready(val generation: Int, val replayEpoch: String) : GatewayStatus
    data class Reconnecting(val attempt: Int, val message: String?) : GatewayStatus
    data class Failed(val message: String) : GatewayStatus
}

data class GatewayTarget(val url: String, val ticket: String, val headers: List<Pair<String, String>>)

class HermesGateway(
    private val client: OkHttpClient,
    private val scope: CoroutineScope,
    private val connector: suspend () -> GatewayTarget,
) {
    private val _status = MutableStateFlow<GatewayStatus>(GatewayStatus.Idle)
    val status: StateFlow<GatewayStatus> = _status.asStateFlow()

    private val _events = MutableSharedFlow<GatewayEnvelope>(extraBufferCapacity = 512)
    val events: SharedFlow<GatewayEnvelope> = _events.asSharedFlow()

    private var loop: Job? = null
    private var socket: WebSocket? = null
    private var generation = 0
    private val ids = AtomicLong(1)
    private val pending = ConcurrentHashMap<Long, CompletableDeferred<JsonElement>>()
    private val wake = Channel<Unit>(Channel.CONFLATED)

    @Synchronized
    fun start() {
        if (loop?.isActive == true) return
        loop = scope.launch { runLoop() }
    }

    @Synchronized
    fun stop() {
        loop?.cancel()
        loop = null
        socket?.close(1000, null)
        socket = null
        failPending(HermesFailure.Unsupported())
        _status.value = GatewayStatus.Idle
    }

    fun reconnectNow() {
        val open = socket
        if (open != null) open.cancel() else wake.trySend(Unit)
    }

    suspend fun call(method: String, params: JsonObject, timeoutMs: Long = CALL_TIMEOUT_MS): JsonElement {
        val ready = withTimeout(READY_WAIT_MS) { status.first { it is GatewayStatus.Ready } }
        check(ready is GatewayStatus.Ready)
        return send(method, params, timeoutMs)
    }

    private suspend fun send(method: String, params: JsonObject, timeoutMs: Long): JsonElement {
        val ws = socket ?: throw HermesFailure.Transport("gateway", null)
        val id = ids.getAndIncrement()
        val deferred = CompletableDeferred<JsonElement>()
        pending[id] = deferred
        val frame = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", id)
            put("method", method)
            put("params", params)
        }
        if (!ws.send(frame.toString())) {
            pending.remove(id)
            throw HermesFailure.Transport("gateway", null)
        }
        return try {
            withTimeout(timeoutMs) { deferred.await() }
        } finally {
            pending.remove(id)
        }
    }

    private suspend fun runLoop() {
        var attempt = 0
        while (scope.isActive) {
            _status.value = if (attempt == 0) GatewayStatus.Connecting else _status.value
            val outcome = try {
                connectOnce { attempt = 0 }
                null
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (failure: HermesFailure.SignInExpired) {
                _status.value = GatewayStatus.Failed(failure.message.orEmpty())
                return
            } catch (failure: HermesFailure.UpgradeRefused) {
                _status.value = GatewayStatus.Failed(failure.message.orEmpty())
                return
            } catch (error: Throwable) {
                error.message
            }
            socket = null
            failPending(HermesFailure.Transport("gateway", null))
            attempt += 1
            _status.value = GatewayStatus.Reconnecting(attempt, outcome)
            withTimeoutOrNull(BACKOFF_SECONDS[(attempt - 1).coerceAtMost(BACKOFF_SECONDS.lastIndex)] * 1000L) { wake.receive() }
        }
    }

    private suspend fun connectOnce(onReady: () -> Unit) = coroutineScope {
        val target = connector()
        val signals = Channel<Signal>(Channel.UNLIMITED)
        val request = Request.Builder()
            .url(target.url)
            .header("Sec-WebSocket-Protocol", "$SUBPROTOCOL, hermes-gateway-ticket.${target.ticket}")
            .apply { target.headers.forEach { (k, v) -> header(k, v) } }
            .build()
        ids.set(1)
        val ws = client.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                signals.trySend(Signal.Text(text))
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
                signals.trySend(Signal.Closed(code))
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                signals.trySend(Signal.Closed(code))
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                signals.trySend(Signal.Failure(t, response?.code))
            }
        })
        socket = ws
        try {
            val epoch = withTimeout(HANDSHAKE_TIMEOUT_MS) {
                var found: String? = null
                while (found == null) {
                    when (val signal = signals.receive()) {
                        is Signal.Text -> found = readyEpoch(signal.text)
                        is Signal.Closed -> throw HermesFailure.Transport("gateway", null)
                        is Signal.Failure -> throw upgradeFailure(signal)
                    }
                }
                found
            }
            val lastFrameAt = AtomicLong(System.currentTimeMillis())
            val reader = launch {
                for (signal in signals) {
                    when (signal) {
                        is Signal.Text -> {
                            lastFrameAt.set(System.currentTimeMillis())
                            route(signal.text)
                        }
                        is Signal.Closed, is Signal.Failure -> break
                    }
                }
                ws.cancel()
            }
            runCatching {
                send("client.capabilities", buildJsonObject { put("server_requests", true) }, CALL_TIMEOUT_MS)
            }.onFailure { if (it is HermesFailure.Transport) throw it }
            generation += 1
            onReady()
            _status.value = GatewayStatus.Ready(generation, epoch)
            val heartbeat = launch {
                var beat = 1
                while (isActive) {
                    delay(PING_INTERVAL_MS)
                    if (System.currentTimeMillis() - lastFrameAt.get() > SILENCE_LIMIT_MS) {
                        ws.cancel()
                        break
                    }
                    val ping = buildJsonObject {
                        put("jsonrpc", "2.0")
                        put("id", "heartbeat-${beat++}")
                        put("method", "gateway.ping")
                        putJsonObject("params") {}
                    }
                    ws.send(ping.toString())
                }
            }
            reader.join()
            heartbeat.cancel()
        } finally {
            ws.cancel()
        }
    }

    private fun upgradeFailure(signal: Signal.Failure): Throwable {
        val code = signal.code ?: return HermesFailure.Transport("gateway", signal.error)
        return when {
            code == 408 || code == 429 || code >= 500 -> HermesFailure.Rejected(code, "gateway")
            code == 401 -> HermesFailure.Rejected(code, "gateway")
            else -> HermesFailure.UpgradeRefused(code)
        }
    }

    private fun readyEpoch(text: String): String? {
        val root = runCatching { HermesJson.parseToJsonElement(text) }.getOrNull().obj() ?: return null
        val params = root.child("params")
        if (root.str("method") != "event" || params.str("type") != "gateway.ready") return null
        return params.child("payload").str("replay_epoch") ?: ""
    }

    private suspend fun route(text: String) {
        val root = runCatching { HermesJson.parseToJsonElement(text) }.getOrNull().obj() ?: return
        val method = root.str("method")
        val idElement = root["id"] as? JsonPrimitive
        when {
            method == "event" -> root.child("params")?.let { _events.emit(GatewayEventParser.parseEvent(it)) }
            idElement != null && idElement.isString && method != null ->
                _events.emit(GatewayEventParser.parseServerRequest(idElement.content, method, root.child("params")))
            idElement != null && idElement.isString -> Unit
            idElement != null -> {
                val id = idElement.content.toLongOrNull() ?: return
                val deferred = pending.remove(id) ?: return
                val error = root.child("error")
                val result = root["result"]
                when {
                    error != null -> deferred.completeExceptionally(
                        HermesFailure.Rpc(error.long("code")?.toInt() ?: 0, error.str("message") ?: "Hermes refused the request."),
                    )
                    result == null || result is JsonNull -> deferred.completeExceptionally(HermesFailure.Unsupported())
                    else -> deferred.complete(result)
                }
            }
        }
    }

    private fun failPending(error: Throwable) {
        pending.values.forEach { it.completeExceptionally(error) }
        pending.clear()
    }

    private sealed interface Signal {
        data class Text(val text: String) : Signal
        data class Closed(val code: Int) : Signal
        data class Failure(val error: Throwable, val code: Int?) : Signal
    }

    companion object {
        const val SUBPROTOCOL = "hermes-gateway-v1"
        const val CALL_TIMEOUT_MS = 30_000L
        const val READY_WAIT_MS = 20_000L
        const val HANDSHAKE_TIMEOUT_MS = 15_000L
        const val PING_INTERVAL_MS = 15_000L
        const val SILENCE_LIMIT_MS = 45_000L
        val BACKOFF_SECONDS = longArrayOf(1, 2, 4, 8)
    }
}
