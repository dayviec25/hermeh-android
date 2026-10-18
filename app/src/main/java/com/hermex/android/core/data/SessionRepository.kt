package com.hermex.android.core.data

import com.hermex.android.core.data.db.SessionDao
import com.hermex.android.core.data.db.SessionEntity
import com.hermex.android.core.model.DeleteOutcome
import com.hermex.android.core.model.DuplicateResult
import com.hermex.android.core.model.ProfileName
import com.hermex.android.core.model.ProfileSummary
import com.hermex.android.core.model.ProjectLane
import com.hermex.android.core.model.SessionSummary
import com.hermex.android.core.model.StoredKey
import com.hermex.android.core.network.HermesFailure
import com.hermex.android.core.network.HermesGateway
import com.hermex.android.core.network.HermesRest
import com.hermex.android.core.network.SessionRow
import com.hermex.android.core.network.bool
import com.hermex.android.core.network.list
import com.hermex.android.core.network.long
import com.hermex.android.core.network.obj
import com.hermex.android.core.network.str
import com.hermex.android.core.network.textOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

data class PageResult(val added: Int, val hasMore: Boolean)

class SessionRepository(
    private val auth: AuthRepository,
    private val dao: SessionDao,
    private val gateway: HermesGateway,
    private val ownRuntimes: () -> Set<String>,
    private val onRuntimeClosed: (String) -> Unit,
) {
    fun sessions(serverId: String, profile: String?, archived: Boolean = false): Flow<List<SessionSummary>> {
        val source = if (profile == null) dao.observe(serverId, archived) else dao.observeProfile(serverId, profile, archived)
        return source.map { rows -> rows.map { it.toDomain() } }
    }

    suspend fun refresh(serverId: String, profile: String, upTo: Int, archived: Boolean = false): PageResult {
        val seen = HashSet<String>()
        val rows = mutableListOf<SessionEntity>()
        var offset = 0
        var hasMore = false
        auth.authorized { rest ->
            while (true) {
                val page = rest.sessions(profile, offset, HermesRest.HOME_PAGE, archived).rows
                val fresh = page.filter { seen.add(it.id) }
                rows += fresh.filter { it.archived == archived }.map { it.toEntity(serverId, profile) }
                hasMore = page.size >= HermesRest.HOME_PAGE && fresh.isNotEmpty()
                offset += HermesRest.HOME_PAGE
                if (!hasMore || offset >= upTo) break
            }
        }
        dao.replace(serverId, profile, archived, rows)
        return PageResult(rows.size, hasMore)
    }

    suspend fun loadPage(serverId: String, profile: String, offset: Int, archived: Boolean = false): PageResult {
        val page = auth.authorized { rest -> rest.sessions(profile, offset, HermesRest.HOME_PAGE, archived) }
        val rows = page.rows.filter { it.archived == archived }.map { it.toEntity(serverId, profile) }
        dao.upsert(rows)
        return PageResult(page.rows.size, page.rows.size >= HermesRest.HOME_PAGE)
    }

    suspend fun loadAllProfiles(serverId: String, archived: Boolean = false): PageResult {
        val names = profiles().map { it.name.value }.ifEmpty { listOf(auth.activeProfile.value) }
        val rows = auth.authorized { rest ->
            names.flatMap { name -> rest.sessions(name, 0, HermesRest.SESSION_PAGE, archived).rows.filter { it.archived == archived }.map { it.toEntity(serverId, name) } }
        }
        dao.replace(serverId, null, archived, rows)
        return PageResult(rows.size, hasMore = false)
    }

    suspend fun profiles(): List<ProfileSummary> = auth.authorized { rest ->
        rest.profiles().mapNotNull { row ->
            val name = row.str("name")?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            ProfileSummary(
                name = ProfileName(name),
                displayName = row.str("display_name")?.takeIf { it.isNotBlank() } ?: name.replaceFirstChar { it.uppercase() },
                model = row.str("model")?.takeIf { it.isNotBlank() },
                isDefault = row.bool("is_default") ?: false,
            )
        }
    }

    suspend fun projects(profile: String): List<ProjectLane> {
        val result = gateway.call("projects.tree", buildJsonObject { put("profile", profile) }).obj()
        return result.list("projects").mapNotNull { element ->
            val row = element.obj() ?: return@mapNotNull null
            if (row.bool("isNoProject") == true) return@mapNotNull null
            val id = row.str("id")?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val keys = row.list("sessionIds").mapNotNull { it.textOrNull()?.let(::StoredKey) }.toSet()
            ProjectLane(
                id = id,
                label = row.str("label")?.takeIf { it.isNotBlank() } ?: id.substringAfterLast('/'),
                color = row.str("color"),
                path = row.str("path"),
                isAuto = row.bool("isAuto") ?: false,
                sessionKeys = keys,
                sessionCount = row.long("sessionCount")?.toInt() ?: keys.size,
            )
        }.sortedBy { it.isAuto }
    }

    suspend fun createProject(profile: String, name: String, folder: String) {
        try {
            gateway.call("projects.create", buildJsonObject {
                put("profile", profile)
                put("name", name)
                put("folders", buildJsonArray { add(JsonPrimitive(folder)) })
                put("primary_path", folder)
            })
        } catch (failure: HermesFailure.Rpc) {
            if (failure.code == 5063) throw HermesFailure.Rpc(5063, "Another project already owns that folder.")
            throw failure
        }
    }

    suspend fun search(query: String, profile: String): List<SessionSummary> = auth.authorized { rest ->
        rest.searchSessions(query, profile).map { it.toEntity("", profile).toDomain() }
    }

    suspend fun setPinned(serverId: String, key: StoredKey, value: Boolean) =
        mutate(serverId, key, "pinned", JsonPrimitive(value)) { it.copy(pinned = value) }

    suspend fun setUnread(serverId: String, key: StoredKey, value: Boolean) =
        mutate(serverId, key, "unread", JsonPrimitive(value)) { it.copy(unread = value) }

    suspend fun setArchived(serverId: String, key: StoredKey, value: Boolean) =
        mutate(serverId, key, "archived", JsonPrimitive(value)) { it.copy(archived = value) }

    suspend fun rename(serverId: String, key: StoredKey, title: String) {
        val trimmed = title.trim()
        val previous = dao.get(serverId, key.value) ?: return
        dao.upsert(listOf(previous.copy(title = trimmed.ifEmpty { null })))
        try {
            val reply = auth.authorized { rest -> rest.updateSession(key.value, previous.profile, "title", JsonPrimitive(trimmed)) }
            val kept = reply.str("title")
            if (kept != null) dao.upsert(listOf(previous.copy(title = kept.ifEmpty { null })))
        } catch (error: Throwable) {
            dao.upsert(listOf(previous))
            throw error
        }
    }

    suspend fun duplicate(serverId: String, key: StoredKey, profile: String, title: String): DuplicateResult {
        val id = newSessionId()
        auth.authorized { rest ->
            val export = rest.exportSession(key.value, profile)
            val copy = withContext(Dispatchers.Default) { copyOf(export, id) } ?: throw HermesFailure.Unsupported()
            if (copy["messages"].let { it as? JsonArray }.orEmpty().size > 10_000) throw HermesFailure.Refused(413, TOO_LARGE)
            val body = buildJsonObject {
                put("sessions", JsonArray(listOf(copy)))
                put("profile", profile)
            }
            val result = try {
                rest.importSessions(body)
            } catch (failure: HermesFailure.Rejected) {
                if (failure.code == 413) throw HermesFailure.Refused(413, TOO_LARGE)
                throw failure
            }
            val imported = result.list("imported_ids").mapNotNull { it.textOrNull() }
            if (id !in imported) throw HermesFailure.Unsupported()
        }
        var kept: String? = null
        for (attempt in 1..10) {
            val candidate = copyTitle(title, attempt)
            try {
                val reply = auth.authorized { rest -> rest.updateSession(id, profile, "title", JsonPrimitive(candidate)) }
                kept = reply.str("title")?.takeIf { it.isNotEmpty() } ?: candidate
                break
            } catch (refused: HermesFailure.Refused) {
                continue
            } catch (error: HermesFailure) {
                break
            }
        }
        return DuplicateResult(StoredKey(id), kept)
    }

    suspend fun delete(serverId: String, key: StoredKey, profile: String): DeleteOutcome {
        val live = runCatching { gateway.call("session.active_list", JsonObject(emptyMap())).obj().list("sessions") }
            .getOrDefault(emptyList())
            .mapNotNull { it.obj() }
        val attached = ownRuntimes()
        val own = live.filter { it.str("session_key") == key.value && it.str("id") in attached }
        if (own.any { it.str("status") in BUSY_STATES }) return DeleteOutcome.BusyHere
        own.mapNotNull { it.str("id") }.forEach { runtime ->
            gateway.call("session.close", buildJsonObject { put("session_id", runtime) })
            onRuntimeClosed(runtime)
        }
        try {
            gateway.call("session.delete", buildJsonObject {
                put("session_id", key.value)
                put("profile", profile)
            })
        } catch (failure: HermesFailure.Rpc) {
            if (failure.code == 4023) return DeleteOutcome.OpenElsewhere
            throw failure
        }
        dao.delete(serverId, key.value)
        return DeleteOutcome.Deleted
    }

    suspend fun clearAll() = dao.clearAll()

    private suspend fun mutate(
        serverId: String,
        key: StoredKey,
        field: String,
        value: JsonPrimitive,
        transform: (SessionEntity) -> SessionEntity,
    ) {
        val previous = dao.get(serverId, key.value) ?: return
        dao.upsert(listOf(transform(previous)))
        try {
            auth.authorized { rest -> rest.updateSession(key.value, previous.profile, field, value) }
        } catch (error: Throwable) {
            dao.upsert(listOf(previous))
            throw error
        }
    }

    private fun SessionRow.toEntity(serverId: String, fallbackProfile: String) = SessionEntity(
        serverId = serverId,
        key = id,
        profile = profile ?: fallbackProfile,
        title = title?.takeIf { it.isNotBlank() },
        preview = preview.orEmpty(),
        messageCount = messageCount,
        lastActive = lastActive ?: 0.0,
        pinned = pinned,
        archived = archived,
        unread = unread,
        isActive = isActive,
        model = model,
        parentKey = parentSessionId,
        cwd = cwd,
    )

    private fun SessionEntity.toDomain() = SessionSummary(
        key = StoredKey(key),
        profile = ProfileName(profile),
        title = title ?: preview.takeIf { it.isNotBlank() } ?: "New chat",
        preview = preview,
        messageCount = messageCount,
        lastActive = Instant.ofEpochMilli((lastActive * 1000).toLong()),
        pinned = pinned,
        archived = archived,
        unread = unread,
        isActive = isActive,
        model = model,
        parentKey = parentKey?.let(::StoredKey),
        cwd = cwd,
    )

    companion object {
        private val BUSY_STATES = setOf("working", "starting", "streaming", "waiting")
        private const val TOO_LARGE = "This session is too large to duplicate."

        fun copyOf(export: JsonObject, id: String): JsonObject? {
            val messages = export["messages"] as? JsonArray ?: return null
            val fields = export.filterKeys { !it.startsWith("_lineage_") && it != "parent_session_id" && it != "timings" }.toMutableMap()
            fields["id"] = JsonPrimitive(id)
            fields["title"] = JsonNull
            fields["archived"] = JsonPrimitive(false)
            fields["pinned"] = JsonPrimitive(false)
            fields["messages"] = JsonArray(messages.map { message -> JsonObject((message as? JsonObject).orEmpty().filterKeys { it != "id" }) })
            return JsonObject(fields)
        }

        fun newSessionId(now: LocalDateTime = LocalDateTime.now()): String =
            now.format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss", Locale.US)) + "_" +
                UUID.randomUUID().toString().replace("-", "").take(6)

        fun copyTitle(title: String, attempt: Int): String {
            val suffix = if (attempt == 1) " (copy)" else " (copy $attempt)"
            return title.take(100 - suffix.length).trim() + suffix
        }
    }
}
