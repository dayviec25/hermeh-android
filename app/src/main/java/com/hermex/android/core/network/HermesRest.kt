package com.hermex.android.core.network

import com.hermex.android.core.model.HermesUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

data class HostStatus(
    val version: String?,
    val installId: String?,
    val authRequired: Boolean,
    val authProviders: List<String>,
    val authFlows: List<String>,
)

data class SessionRow(
    val id: String,
    val title: String?,
    val preview: String?,
    val messageCount: Int,
    val lastActive: Double?,
    val profile: String?,
    val pinned: Boolean,
    val archived: Boolean,
    val unread: Boolean,
    val isActive: Boolean,
    val model: String?,
    val parentSessionId: String?,
)

class HermesRest(
    private val client: OkHttpClient,
    val baseUrl: HttpUrl,
    val headers: List<Pair<String, String>>,
) {
    val host: String get() = baseUrl.host

    fun url(path: String, query: List<Pair<String, String>> = emptyList()): HttpUrl {
        val builder = baseUrl.newBuilder().addPathSegments(path)
        query.forEach { (k, v) -> builder.addQueryParameter(k, v) }
        return builder.build()
    }

    fun gatewayUrl(): String {
        val http = url("api/ws").toString()
        return when {
            http.startsWith("https://") -> "wss://" + http.removePrefix("https://")
            else -> "ws://" + http.removePrefix("http://")
        }
    }

    suspend fun status(): HostStatus {
        val body = exchange(get(url("api/status")), publicProbe = true)
        val root = body.obj() ?: throw HermesFailure.NotDashboard(host)
        return HostStatus(
            version = root.str("version"),
            installId = root.str("install_id"),
            authRequired = root.bool("auth_required") ?: false,
            authProviders = root.list("auth_providers").mapNotNull { it.textOrNull() },
            authFlows = root.list("auth_flows").mapNotNull { it.textOrNull() },
        )
    }

    suspend fun passwordLogin(username: String, password: String) {
        val body = buildJsonObject {
            put("provider", "basic")
            put("username", username)
            put("password", password)
        }
        exchange(post(url("auth/password-login"), body))
    }

    suspend fun me(): HermesUser {
        val root = exchange(get(url("api/auth/me"))).obj()
        return HermesUser(
            userId = root.str("user_id").orEmpty(),
            displayName = root.str("display_name").orEmpty(),
            provider = root.str("provider").orEmpty(),
        )
    }

    suspend fun wsTicket(): String {
        val root = exchange(post(url("api/auth/ws-ticket"), JsonObject(emptyMap()))).obj()
        return root.str("ticket")?.takeIf { it.isNotEmpty() } ?: throw HermesFailure.Unsupported()
    }

    suspend fun activeProfile(): String {
        val root = exchange(get(url("api/profiles/active"))).obj()
        return root.str("active")?.takeIf { it.isNotEmpty() } ?: "default"
    }

    suspend fun sessions(profile: String, offset: Int): List<SessionRow> {
        val query = listOf(
            "profile" to profile,
            "order" to "recent",
            "archived" to "exclude",
            "limit" to SESSION_PAGE.toString(),
            "offset" to offset.toString(),
            "min_messages" to "1",
            "exclude_sources" to "cron,kanban,oneshot,subagent,tool",
        )
        val root = exchange(get(url("api/sessions", query))).obj()
        val rows = root?.get("sessions").arr() ?: throw HermesFailure.Unsupported()
        return rows.mapNotNull { element ->
            val row = element.obj() ?: return@mapNotNull null
            val id = row.str("id") ?: return@mapNotNull null
            SessionRow(
                id = id,
                title = row.str("title"),
                preview = row.str("preview"),
                messageCount = row.long("message_count")?.toInt() ?: 0,
                lastActive = row.double("last_active") ?: row.double("started_at"),
                profile = row.str("profile") ?: row.str("profile_name"),
                pinned = row.bool("pinned") ?: false,
                archived = row.bool("archived") ?: false,
                unread = row.bool("unread") ?: false,
                isActive = row.bool("is_active") ?: false,
                model = row.str("model"),
                parentSessionId = row.str("parent_session_id"),
            )
        }
    }

    suspend fun messages(key: String, profile: String, offset: Int): List<JsonObject> {
        val query = listOf(
            "profile" to profile,
            "order" to "latest",
            "limit" to TRANSCRIPT_PAGE.toString(),
            "offset" to offset.toString(),
            "include_compacted" to "true",
        )
        val root = exchange(get(url("api/sessions/$key/messages", query))).obj()
        return root.list("messages").mapNotNull { it.obj() }
    }

    private fun get(url: HttpUrl): Request = request(url).get().build()

    private fun post(url: HttpUrl, body: JsonObject): Request =
        request(url).post(body.toString().toRequestBody(JSON)).build()

    private fun request(url: HttpUrl): Request.Builder {
        val builder = Request.Builder().url(url).header("Accept", "application/json")
        headers.forEach { (name, value) -> builder.header(name, value) }
        return builder
    }

    private suspend fun exchange(request: Request, publicProbe: Boolean = false): JsonElement? =
        withContext(Dispatchers.IO) {
            val response = try {
                client.newCall(request).execute()
            } catch (error: java.io.IOException) {
                throw HermesFailure.Transport(host, error)
            }
            response.use { handle(it, publicProbe) }
        }

    private fun handle(response: Response, publicProbe: Boolean): JsonElement? {
        val text = response.body?.string().orEmpty()
        val parsed = runCatching { HermesJson.parseToJsonElement(text) }.getOrNull()
        val code = response.code
        if (response.request.url.host != host) throw HermesFailure.Blocked()
        if (code in 300..399) throw HermesFailure.Blocked()
        if (publicProbe) {
            when {
                code == 200 && parsed is JsonObject -> return parsed
                code == 200 || code == 404 -> throw HermesFailure.NotDashboard(host)
                code == 401 && parsed is JsonObject -> throw HermesFailure.NotDashboard(host)
                code == 401 -> throw HermesFailure.Blocked()
                else -> throw HermesFailure.Rejected(code, host)
            }
        }
        if (code !in 200..299) throw HermesFailure.Rejected(code, host)
        return parsed
    }

    companion object {
        const val SESSION_PAGE = 100
        const val TRANSCRIPT_PAGE = 100
        private val JSON = "application/json".toMediaType()

        fun normalize(input: String): HttpUrl? {
            var text = input.trim()
            if (text.isEmpty()) return null
            if (!text.contains("://")) text = "http://$text"
            val url = text.toHttpUrlOrNull() ?: return null
            if (url.username.isNotEmpty() || url.password.isNotEmpty() || url.query != null) return null
            val trimmed = url.encodedPath.trimEnd('/')
            return url.newBuilder().encodedPath(if (trimmed.isEmpty()) "/" else "$trimmed/").fragment(null).build()
        }

        fun display(url: HttpUrl): String = url.toString().trimEnd('/')
    }
}
