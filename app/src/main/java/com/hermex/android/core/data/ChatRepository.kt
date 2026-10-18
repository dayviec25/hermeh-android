package com.hermex.android.core.data

import com.hermex.android.core.data.db.TranscriptDao
import com.hermex.android.core.data.db.TranscriptRowEntity
import com.hermex.android.core.model.ApprovalChoice
import com.hermex.android.core.model.AuthState
import com.hermex.android.core.model.BranchResult
import com.hermex.android.core.model.ConnectionState
import com.hermex.android.core.network.HermesFailure
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import com.hermex.android.core.model.ProfileName
import com.hermex.android.core.model.RuntimeId
import com.hermex.android.core.model.StoredKey
import com.hermex.android.core.model.ToolStatus
import com.hermex.android.core.model.TranscriptItem
import com.hermex.android.core.network.GatewayEnvelope
import com.hermex.android.core.network.GatewayEventParser
import com.hermex.android.core.network.HermesGateway
import com.hermex.android.core.network.HermesJson
import com.hermex.android.core.network.HermesRest
import com.hermex.android.core.network.arr
import com.hermex.android.core.network.bool
import com.hermex.android.core.network.child
import com.hermex.android.core.network.double
import com.hermex.android.core.network.list
import com.hermex.android.core.network.long
import com.hermex.android.core.network.obj
import com.hermex.android.core.network.str
import com.hermex.android.core.network.textOrNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class AttachedSession(
    val runtime: RuntimeId,
    val storedKey: StoredKey,
    val running: Boolean,
    val model: String?,
    val provider: String?,
    val title: String?,
    val cwd: String? = null,
)

data class ModelOption(val id: String, val provider: String, val providerName: String)

data class ModelCatalog(val active: String?, val activeProvider: String?, val groups: List<Pair<String, List<ModelOption>>>)

data class Replay(val latestSeq: Long, val events: List<GatewayEnvelope>)

class ChatRepository(
    private val auth: AuthRepository,
    private val gateway: HermesGateway,
    private val cache: TranscriptDao,
    val connection: StateFlow<ConnectionState>,
) {
    val events: Flow<GatewayEnvelope> get() = gateway.events
    val status get() = gateway.status

    private val runtimes: MutableSet<String> = ConcurrentHashMap.newKeySet()

    val attachedRuntimes: Set<String> get() = runtimes.toSet()

    fun forgetRuntime(runtime: String) {
        runtimes.remove(runtime)
    }

    private fun serverId(): String? = (auth.state.value as? AuthState.SignedIn)?.server?.id

    suspend fun cachedHistory(key: StoredKey): List<TranscriptItem> {
        val server = serverId() ?: return emptyList()
        val rows = cache.rows(server, key.value).mapNotNull { entity ->
            runCatching { HermesJson.parseToJsonElement(entity.json) }.getOrNull().obj()
        }
        return withContext(Dispatchers.Default) { mapHistory(rows) }
    }

    suspend fun history(key: StoredKey, profile: ProfileName): List<TranscriptItem> {
        val rows = fetchRows(key, profile)
        serverId()?.let { server ->
            val entities = rows.mapIndexed { index, row ->
                TranscriptRowEntity(server, key.value, rowKey(row, index), index, row.toString())
            }
            cache.replace(server, key.value, entities)
        }
        return withContext(Dispatchers.Default) { mapHistory(rows) }
    }

    suspend fun branch(runtime: RuntimeId, count: Int?): BranchResult {
        val result = gateway.call("session.branch", buildJsonObject {
            put("session_id", runtime.value)
            if (count != null) put("count", count)
        }).obj()
        val key = result.str("stored_session_id")?.takeIf { it.isNotEmpty() } ?: throw HermesFailure.Unsupported()
        result.str("session_id")?.let(runtimes::add)
        return BranchResult(StoredKey(key), result.str("title"))
    }

    private suspend fun fetchRows(key: StoredKey, profile: ProfileName): List<JsonObject> {
        return auth.authorized { rest ->
            val pages = ArrayDeque<List<JsonObject>>()
            var offset = 0
            while (true) {
                val page = rest.messages(key.value, profile.value, offset)
                pages.addFirst(page)
                if (page.size < HermesRest.TRANSCRIPT_PAGE || offset >= MAX_HISTORY) break
                offset += HermesRest.TRANSCRIPT_PAGE
            }
            pages.flatten()
        }
    }

    private fun rowKey(row: JsonObject, index: Int): String =
        row.long("id")?.toString() ?: row.long("row_id")?.toString() ?: "i$index"


    suspend fun create(profile: ProfileName, model: String?, provider: String?): AttachedSession {
        val result = gateway.call("session.create", buildJsonObject {
            put("profile", profile.value)
            put("source", "hermex")
            if (model != null && provider != null) {
                put("model", model)
                put("provider", provider)
            }
        }).obj()
        val info = result.child("info")
        return AttachedSession(
            runtime = RuntimeId(requireNotNull(result.str("session_id")) { "session.create without session_id" }),
            storedKey = StoredKey(requireNotNull(result.str("stored_session_id")) { "session.create without stored_session_id" }),
            running = false,
            model = info.str("model"),
            provider = info.str("provider"),
            title = info.str("title"),
            cwd = info.str("cwd"),
        ).also { runtimes.add(it.runtime.value) }
    }

    suspend fun resume(key: StoredKey, profile: ProfileName): AttachedSession {
        val result = gateway.call("session.resume", buildJsonObject {
            put("profile", profile.value)
            put("session_id", key.value)
            put("close_on_disconnect", false)
            put("source", "hermex")
            put("omit_messages", true)
        }).obj()
        val info = result.child("info")
        return AttachedSession(
            runtime = RuntimeId(requireNotNull(result.str("session_id")) { "session.resume without session_id" }),
            storedKey = StoredKey(result.str("session_key") ?: result.str("stored_session_id") ?: key.value),
            running = result.bool("running") ?: info.bool("running") ?: false,
            model = info.str("model"),
            provider = info.str("provider"),
            title = info.str("title") ?: result.str("title"),
            cwd = info.str("cwd"),
        ).also { runtimes.add(it.runtime.value) }
    }

    suspend fun eventsSince(runtime: RuntimeId, lastSeen: Long): Replay {
        val result = gateway.call("session.events.since", buildJsonObject {
            put("session_id", runtime.value)
            put("last_seen", lastSeen)
        }).obj()
        val events = result.list("events").mapNotNull { it.obj()?.let(GatewayEventParser::parseEvent) }
        return Replay(result.long("latest_seq") ?: lastSeen, events)
    }

    suspend fun submit(runtime: RuntimeId, text: String): String? {
        val result = gateway.call("prompt.submit", buildJsonObject {
            put("session_id", runtime.value)
            put("text", text)
            put("queued", true)
        }).obj()
        return result.str("status")
    }

    suspend fun interrupt(runtime: RuntimeId) {
        gateway.call("session.interrupt", buildJsonObject { put("session_id", runtime.value) })
    }

    suspend fun respondApproval(runtime: RuntimeId, requestId: String, choice: ApprovalChoice): Long? {
        val result = gateway.call("approval.respond", buildJsonObject {
            put("session_id", runtime.value)
            put("request_id", requestId)
            put("choice", choice.wire)
        }).obj()
        return result.long("resolved")
    }

    suspend fun answer(envelopeId: String, field: String, value: String) {
        gateway.call("request.answer", buildJsonObject {
            put("id", envelopeId)
            put("result", buildJsonObject { put(field, value) })
        })
    }

    suspend fun modelOptions(runtime: RuntimeId?, profile: ProfileName): ModelCatalog {
        val result = gateway.call("model.options", buildJsonObject {
            if (runtime != null) put("session_id", runtime.value)
            put("profile", profile.value)
        }).obj()
        val seenProviders = HashSet<String>()
        val groups = result.list("providers").mapNotNull { element ->
            val row = element.obj() ?: return@mapNotNull null
            val slug = row.str("slug")?.takeIf { it.isNotEmpty() && seenProviders.add(it) } ?: return@mapNotNull null
            if (row.bool("authenticated") == false) return@mapNotNull null
            val unavailable = row.list("unavailable_models").mapNotNull { it.textOrNull() }.toSet()
            val name = row.str("name") ?: slug
            val models = row.list("models").mapNotNull { it.textOrNull() }
                .filter { it.isNotEmpty() && it !in unavailable }
                .distinct()
                .map { ModelOption(it, slug, name) }
            if (models.isEmpty()) null else name to models
        }
        return ModelCatalog(result.str("model"), result.str("provider"), groups)
    }

    suspend fun setModel(runtime: RuntimeId, profile: ProfileName, option: ModelOption): Boolean {
        fun safe(value: String) = value.isNotEmpty() && value.none { it.isWhitespace() } && !value.contains("--")
        if (!safe(option.id) || !safe(option.provider)) return false
        gateway.call("config.set", buildJsonObject {
            put("session_id", runtime.value)
            put("profile", profile.value)
            put("scope", "session")
            put("key", "model")
            put("value", "${option.id} --provider ${option.provider} --session")
            put("confirm_expensive_model", false)
        })
        return true
    }

    suspend fun setReasoning(runtime: RuntimeId, profile: ProfileName, level: String) {
        gateway.call("config.set", buildJsonObject {
            put("session_id", runtime.value)
            put("profile", profile.value)
            put("scope", "session")
            put("key", "reasoning")
            put("value", level)
        })
    }

    private fun mapHistory(rows: List<JsonObject>): List<TranscriptItem> {
        val items = mutableListOf<TranscriptItem>()
        val toolIndex = HashMap<String, Int>()
        for (row in rows) {
            val rowId = row.long("id")?.toString() ?: row.long("row_id")?.toString() ?: items.size.toString()
            val content = row.str("content") ?: row.str("text") ?: ""
            when (row.str("role")) {
                "user" -> if (content.isNotBlank()) items += TranscriptItem.User("u-$rowId", content, steer = false)
                "assistant" -> {
                    val reasoning = row.str("reasoning")?.takeIf { it.isNotBlank() }
                        ?: row.str("reasoning_content")?.takeIf { it.isNotBlank() }
                    if (reasoning != null) items += TranscriptItem.Thinking("r-$rowId", reasoning)
                    if (content.isNotBlank()) items += TranscriptItem.Assistant("a-$rowId", content, streaming = false)
                    row["tool_calls"].arr()?.forEachIndexed { index, element ->
                        val call = element.obj() ?: return@forEachIndexed
                        val function = call.child("function")
                        val callId = call.str("id") ?: call.str("call_id") ?: "$rowId-$index"
                        val argsJson = function.str("arguments") ?: "{}"
                        toolIndex[callId] = items.size
                        items += TranscriptItem.Tool(
                            id = "t-$callId",
                            name = function.str("name") ?: "tool",
                            context = toolContext(argsJson),
                            argsJson = argsJson,
                            status = ToolStatus.Done(null),
                            output = null,
                        )
                    }
                }
                "tool" -> {
                    val callId = row.str("tool_call_id") ?: continue
                    val index = toolIndex[callId] ?: continue
                    val tool = items[index] as? TranscriptItem.Tool ?: continue
                    val parsed = runCatching { HermesJson.parseToJsonElement(content) }.getOrNull().obj()
                    val error = parsed.str("error")?.takeIf { it.isNotBlank() }
                    items[index] = tool.copy(
                        output = parsed?.let { it.str("output") ?: content } ?: content,
                        status = if (error != null) ToolStatus.Failed(error) else ToolStatus.Done(parsed.double("duration_s")),
                    )
                }
            }
        }
        return items
    }

    companion object {
        private const val MAX_HISTORY = 400

        fun toolContext(argsJson: String): String {
            val args = runCatching { HermesJson.parseToJsonElement(argsJson) }.getOrNull().obj() ?: return ""
            for (key in listOf("command", "path", "file_path", "query", "url", "pattern", "goal")) {
                args.str(key)?.let { return it }
            }
            return args.values.firstNotNullOfOrNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }.orEmpty()
        }
    }
}
