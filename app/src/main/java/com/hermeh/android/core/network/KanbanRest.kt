package com.hermeh.android.core.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

sealed class KanbanFailure(message: String) : Exception(message) {
    class PluginAbsent : KanbanFailure("Kanban isn’t available on this server.")
    class NotFound(detail: String?) : KanbanFailure(detail ?: "This Card no longer exists on this Board.")
    class Refused(val code: Int, detail: String) : KanbanFailure(detail)
}

data class KanbanBoardDto(
    val slug: String,
    val name: String?,
    val description: String?,
    val archived: Boolean,
    val isCurrent: Boolean,
    val total: Int,
)

data class KanbanCardDto(
    val id: String,
    val title: String?,
    val status: String?,
    val body: String?,
    val assignee: String?,
    val tenant: String?,
    val priority: Int?,
    val commentCount: Int?,
    val parents: Int?,
    val children: Int?,
    val createdAgeSeconds: Long?,
    val startedAgeSeconds: Long?,
    val createdAt: Double?,
    val updatedAt: Double?,
    val workspaceKind: String?,
    val skills: List<String>,
    val maxRuntimeSeconds: Long?,
    val currentRunId: String?,
    val blockReason: String?,
)

data class KanbanColumnDto(val name: String, val cards: List<KanbanCardDto>)

data class KanbanBoardSnapshotDto(val columns: List<KanbanColumnDto>, val assignees: List<String>, val latestEventId: Long)

data class KanbanCommentDto(val id: String, val author: String?, val body: String?, val createdAt: Double?)

data class KanbanRunDto(val id: String, val status: String?, val profile: String?, val startedAt: Double?)

data class KanbanDetailDto(
    val card: KanbanCardDto,
    val comments: List<KanbanCommentDto>,
    val parents: List<String>,
    val children: List<String>,
    val runs: List<KanbanRunDto>,
)

data class KanbanLogDto(val exists: Boolean, val content: String, val truncated: Boolean, val sizeBytes: Long)

data class KanbanDispatchDto(
    val spawned: Int,
    val promoted: Int,
    val reclaimed: Int,
    val skippedUnassigned: Int,
    val skippedNonspawnable: Int,
    val autoBlocked: Int,
    val timedOut: Int,
    val crashed: Int,
)

data class KanbanCreateBody(
    val title: String,
    val body: String?,
    val assignee: String?,
    val priority: Int?,
    val workspaceKind: String?,
    val triage: Boolean,
    val idempotencyKey: String,
    val skills: List<String>?,
    val maxRuntimeSeconds: Long?,
)

class KanbanRest(private val client: OkHttpClient, private val rest: HermesRest) {
    private val host get() = rest.host

    suspend fun config(): JsonObject {
        return try {
            send(get(url("config"))).obj() ?: throw HermesFailure.Unsupported()
        } catch (missing: KanbanFailure.NotFound) {
            throw KanbanFailure.PluginAbsent()
        }
    }

    suspend fun boards(): List<KanbanBoardDto> {
        val root = send(get(url("boards"))).obj()
        return root.list("boards").mapNotNull { element ->
            val row = element.obj() ?: return@mapNotNull null
            val slug = row.str("slug")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            KanbanBoardDto(
                slug = slug,
                name = row.str("name")?.takeIf { it.isNotBlank() },
                description = row.str("description"),
                archived = row.bool("archived") ?: false,
                isCurrent = row.bool("is_current") ?: (root.str("current") == slug),
                total = row.long("total")?.toInt() ?: 0,
            )
        }
    }

    suspend fun board(board: String): KanbanBoardSnapshotDto {
        val root = send(get(url("board", listOf("board" to board, "include_archived" to "false")))).obj()
        val columns = root.list("columns").mapNotNull { element ->
            val column = element.obj() ?: return@mapNotNull null
            val name = column.str("name") ?: return@mapNotNull null
            KanbanColumnDto(name, column.list("tasks").mapNotNull { it.obj()?.let(::card) })
        }
        return KanbanBoardSnapshotDto(
            columns = columns,
            assignees = root.list("assignees").mapNotNull { it.textOrNull() ?: it.obj().str("name") },
            latestEventId = root.long("latest_event_id") ?: 0,
        )
    }

    suspend fun assignees(board: String): List<String> {
        val root = send(get(url("assignees", listOf("board" to board)))).obj()
        return root.list("assignees").mapNotNull { it.obj().str("name") ?: it.textOrNull() }
    }

    suspend fun detail(board: String, id: String): KanbanDetailDto {
        val root = send(get(url("tasks/$id", listOf("board" to board)))).obj() ?: throw HermesFailure.Unsupported()
        val task = root.child("task")?.let(::card) ?: throw KanbanFailure.NotFound(null)
        val links = root.child("links")
        return KanbanDetailDto(
            card = task,
            comments = root.list("comments").mapNotNull { element ->
                val row = element.obj() ?: return@mapNotNull null
                KanbanCommentDto(
                    id = row.str("id") ?: return@mapNotNull null,
                    author = row.str("author"),
                    body = row.str("body"),
                    createdAt = row.double("created_at"),
                )
            },
            parents = links.list("parents").mapNotNull(::linkId),
            children = links.list("children").mapNotNull(::linkId),
            runs = root.list("runs").mapNotNull { element ->
                val row = element.obj() ?: return@mapNotNull null
                KanbanRunDto(
                    id = row.str("id") ?: row.str("run_id") ?: return@mapNotNull null,
                    status = row.str("status") ?: row.str("outcome"),
                    profile = row.str("profile") ?: row.str("assignee"),
                    startedAt = row.double("started_at"),
                )
            },
        )
    }

    suspend fun log(board: String, id: String, tailBytes: Int = LOG_TAIL_BYTES): KanbanLogDto {
        val root = send(get(url("tasks/$id/log", listOf("board" to board, "tail" to tailBytes.toString())))).obj()
        return KanbanLogDto(
            exists = root.bool("exists") ?: false,
            content = root.str("content").orEmpty(),
            truncated = root.bool("truncated") ?: false,
            sizeBytes = root.long("size_bytes") ?: 0,
        )
    }

    suspend fun create(board: String, request: KanbanCreateBody): KanbanCardDto {
        val body = buildJsonObject {
            put("title", request.title)
            if (request.body != null) put("body", request.body)
            put("assignee", request.assignee?.let(::JsonPrimitive) ?: JsonNull)
            if (request.priority != null) put("priority", request.priority)
            if (request.workspaceKind != null) put("workspace_kind", request.workspaceKind)
            put("triage", request.triage)
            put("idempotency_key", request.idempotencyKey)
            if (!request.skills.isNullOrEmpty()) putJsonArray("skills") { request.skills.forEach { add(JsonPrimitive(it)) } }
            if (request.maxRuntimeSeconds != null) put("max_runtime_seconds", request.maxRuntimeSeconds)
        }
        return mutation(send(post(url("tasks", listOf("board" to board)), body)))
    }

    suspend fun update(board: String, id: String, fields: JsonObject): KanbanCardDto =
        mutation(send(patch(url("tasks/$id", listOf("board" to board)), fields)))

    suspend fun comment(board: String, id: String, text: String) {
        send(post(url("tasks/$id/comments", listOf("board" to board)), buildJsonObject { put("body", text) }))
    }

    suspend fun dispatch(board: String, dryRun: Boolean): KanbanDispatchDto {
        val query = listOf("board" to board, "dry_run" to dryRun.toString(), "max" to "8")
        val root = send(post(url("dispatch", query), JsonObject(emptyMap()))).obj() ?: throw HermesFailure.Unsupported()
        fun count(key: String): Int = when (val value = root[key]) {
            is JsonArray -> value.size
            is JsonPrimitive -> value.content.toIntOrNull() ?: 0
            else -> 0
        }
        return KanbanDispatchDto(
            spawned = count("spawned"),
            promoted = count("promoted"),
            reclaimed = count("reclaimed"),
            skippedUnassigned = count("skipped_unassigned"),
            skippedNonspawnable = count("skipped_nonspawnable"),
            autoBlocked = count("auto_blocked"),
            timedOut = count("timed_out"),
            crashed = count("crashed"),
        )
    }

    private fun mutation(root: JsonElement?): KanbanCardDto =
        root.obj().child("task")?.let(::card) ?: throw HermesFailure.Unsupported()

    private fun linkId(element: JsonElement): String? = element.textOrNull() ?: element.obj().let { it.str("id") ?: it.str("task_id") }

    private fun card(row: JsonObject): KanbanCardDto? {
        val id = row.str("id")?.takeIf { it.isNotBlank() } ?: return null
        val age = row.child("age")
        val links = row.child("link_counts")
        return KanbanCardDto(
            id = id,
            title = row.str("title"),
            status = row.str("status"),
            body = row.str("body"),
            assignee = row.str("assignee")?.takeIf { it.isNotBlank() },
            tenant = row.str("tenant")?.takeIf { it.isNotBlank() },
            priority = row.long("priority")?.toInt(),
            commentCount = row.long("comment_count")?.toInt(),
            parents = links.long("parents")?.toInt(),
            children = links.long("children")?.toInt(),
            createdAgeSeconds = age.long("created_age_seconds"),
            startedAgeSeconds = age.long("started_age_seconds"),
            createdAt = row.double("created_at"),
            updatedAt = row.double("updated_at") ?: row.double("completed_at") ?: row.double("started_at"),
            workspaceKind = row.str("workspace_kind"),
            skills = row.list("skills").mapNotNull { it.textOrNull() },
            maxRuntimeSeconds = row.long("max_runtime_seconds"),
            currentRunId = row.str("current_run_id"),
            blockReason = row.str("block_reason") ?: row.str("last_failure_error"),
        )
    }

    private fun url(path: String, query: List<Pair<String, String>> = emptyList()): HttpUrl =
        rest.url("api/plugins/kanban/$path", query)

    private fun get(url: HttpUrl): Request = request(url).get().build()

    private fun post(url: HttpUrl, body: JsonObject): Request = request(url).post(json(body)).build()

    private fun patch(url: HttpUrl, body: JsonObject): Request = request(url).patch(json(body)).build()

    private fun json(body: JsonObject): RequestBody = body.toString().toRequestBody(JSON)

    private fun request(url: HttpUrl): Request.Builder {
        val builder = Request.Builder().url(url).header("Accept", "application/json")
        rest.headers.forEach { (name, value) -> builder.header(name, value) }
        return builder
    }

    private suspend fun send(request: Request): JsonElement? = withContext(Dispatchers.IO) {
        val response = try {
            client.newCall(request).execute()
        } catch (error: java.io.IOException) {
            throw HermesFailure.Transport(host, error)
        }
        response.use {
            val text = it.body?.string().orEmpty()
            val parsed = runCatching { HermesJson.parseToJsonElement(text) }.getOrNull()
            val code = it.code
            if (it.request.url.host != host || code in 300..399) throw HermesFailure.Blocked()
            val detail = parsed.obj().let { root -> root.str("detail") ?: root.child("detail").str("message") }
            when {
                code in 200..299 -> stripHostKeys(parsed)
                code == 404 -> throw KanbanFailure.NotFound(detail)
                (code == 400 || code == 409 || code == 422) && !detail.isNullOrBlank() -> throw KanbanFailure.Refused(code, detail)
                else -> throw HermesFailure.Rejected(code, host)
            }
        }
    }

    companion object {
        const val LOG_TAIL_BYTES = 65_536
        private val JSON = "application/json".toMediaType()
        private val HOST_KEYS = setOf(
            "workspace_path", "stored_path", "path", "db_path", "default_workdir", "new_path", "worker_pid", "claim_lock",
        )

        fun stripHostKeys(element: JsonElement?): JsonElement? = when (element) {
            is JsonObject -> JsonObject(element.filterKeys { it !in HOST_KEYS }.mapValues { (_, value) -> stripHostKeys(value) ?: JsonNull })
            is JsonArray -> JsonArray(element.map { stripHostKeys(it) ?: JsonNull })
            else -> element
        }
    }
}
