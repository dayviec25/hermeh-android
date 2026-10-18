package com.hermex.android.core.data

import com.hermex.android.core.model.CardDetail
import com.hermex.android.core.model.CardDraft
import com.hermex.android.core.model.CardId
import com.hermex.android.core.model.DispatchResult
import com.hermex.android.core.model.KanbanBoard
import com.hermex.android.core.model.KanbanBoardSnapshot
import com.hermex.android.core.model.KanbanCard
import com.hermex.android.core.model.KanbanComment
import com.hermex.android.core.model.KanbanRun
import com.hermex.android.core.model.KanbanStatus
import com.hermex.android.core.model.WorkerLog
import com.hermex.android.core.model.WorkspaceKind
import com.hermex.android.core.network.KanbanCardDto
import com.hermex.android.core.network.KanbanCreateBody
import com.hermex.android.core.network.KanbanRest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.util.UUID

data class KanbanOverview(
    val boards: List<KanbanBoard>,
    val board: KanbanBoard,
    val snapshot: KanbanBoardSnapshot,
    val profiles: List<String>,
)

class KanbanRepository(private val auth: AuthRepository) {

    private suspend fun <T> kanban(block: suspend (KanbanRest) -> T): T =
        auth.authorized { rest -> block(KanbanRest(auth.httpClient, rest)) }

    suspend fun overview(preferredSlug: String?): KanbanOverview = kanban { api ->
        api.config()
        val boards = api.boards().filter { !it.archived }.map {
            KanbanBoard(it.slug, it.name ?: it.slug, it.description.orEmpty(), it.isCurrent, it.total)
        }
        val board = boards.firstOrNull { it.slug == preferredSlug }
            ?: boards.firstOrNull { it.isCurrent }
            ?: boards.firstOrNull()
            ?: KanbanBoard("default", "Default", "", true, 0)
        val snapshot = api.board(board.slug)
        val profiles = runCatching { api.assignees(board.slug) }.getOrDefault(emptyList())
        KanbanOverview(
            boards = boards,
            board = board,
            snapshot = KanbanBoardSnapshot(
                columns = KanbanStatus.entries.associateWith { status ->
                    snapshot.columns.filter { it.name == status.wire }.flatMap { it.cards }.map { it.toDomain() }
                },
                assignees = snapshot.assignees,
                latestEventId = snapshot.latestEventId,
            ),
            profiles = (profiles + snapshot.assignees).distinct().sorted(),
        )
    }

    suspend fun detail(board: String, id: CardId): CardDetail = kanban { api ->
        val dto = api.detail(board, id.value)
        CardDetail(
            card = dto.card.toDomain(),
            comments = dto.comments.map { KanbanComment(it.id, it.author ?: "dashboard", it.body.orEmpty(), it.createdAt.toInstant()) },
            prerequisites = dto.parents,
            dependents = dto.children,
            runs = dto.runs.map { KanbanRun(it.id, it.status ?: "", it.profile, it.startedAt.toInstant()) },
        )
    }

    suspend fun log(board: String, id: CardId): WorkerLog = kanban { api ->
        val dto = api.log(board, id.value)
        WorkerLog(dto.exists, dto.content, dto.truncated, dto.sizeBytes)
    }

    suspend fun create(board: String, draft: CardDraft): KanbanCard = kanban { api ->
        api.create(
            board,
            KanbanCreateBody(
                title = draft.title.trim(),
                body = draft.body.takeIf { it.isNotBlank() },
                assignee = draft.assignee?.takeIf { it.isNotBlank() },
                priority = draft.priority,
                workspaceKind = draft.workspaceKind.wire,
                triage = draft.status == KanbanStatus.Triage,
                idempotencyKey = UUID.randomUUID().toString(),
                skills = draft.skills.takeIf { it.isNotEmpty() },
                maxRuntimeSeconds = draft.maxRuntimeSeconds,
            ),
        ).toDomain()
    }

    suspend fun edit(board: String, original: KanbanCard, draft: CardDraft): KanbanCard = kanban { api ->
        val fields = buildJsonObject {
            put("title", draft.title.trim())
            put("body", draft.body)
            put("priority", draft.priority)
            if (draft.assignee != original.assignee) put("assignee", draft.assignee.orEmpty())
            if (draft.status != original.status) put("status", draft.status.wire)
        }
        api.update(board, original.id.value, fields).toDomain()
    }

    suspend fun setStatus(board: String, id: CardId, status: KanbanStatus, blockReason: String? = null): KanbanCard {
        require(status != KanbanStatus.Running) { "Running belongs to the Dispatcher" }
        return update(board, id, buildJsonObject {
            put("status", status.wire)
            if (status == KanbanStatus.Blocked && !blockReason.isNullOrBlank()) put("block_reason", blockReason)
        })
    }

    suspend fun comment(board: String, id: CardId, text: String) = kanban { api -> api.comment(board, id.value, text) }

    suspend fun dispatch(board: String, dryRun: Boolean): DispatchResult = kanban { api ->
        val dto = api.dispatch(board, dryRun)
        DispatchResult(
            dryRun = dryRun,
            spawned = dto.spawned,
            promoted = dto.promoted,
            reclaimed = dto.reclaimed,
            skippedUnassigned = dto.skippedUnassigned,
            skippedNonspawnable = dto.skippedNonspawnable,
            autoBlocked = dto.autoBlocked,
            timedOut = dto.timedOut,
            crashed = dto.crashed,
            completedAt = Instant.now(),
        )
    }

    private suspend fun update(board: String, id: CardId, fields: JsonObject): KanbanCard =
        kanban { api -> api.update(board, id.value, fields).toDomain() }

    private fun KanbanCardDto.toDomain(): KanbanCard {
        val status = KanbanStatus.fromWire(status)
        return KanbanCard(
            id = CardId(id),
            title = title?.takeIf { it.isNotBlank() } ?: "Untitled Card",
            status = status,
            body = body.orEmpty(),
            assignee = assignee,
            tenant = tenant,
            priority = priority ?: 0,
            commentCount = commentCount ?: 0,
            parentCount = parents ?: 0,
            childCount = children ?: 0,
            ageSeconds = if (status == KanbanStatus.Running) startedAgeSeconds ?: createdAgeSeconds else createdAgeSeconds,
            createdAt = createdAt.toInstant(),
            updatedAt = updatedAt.toInstant(),
            workspaceKind = WorkspaceKind.fromWire(workspaceKind),
            skills = skills,
            maxRuntimeSeconds = maxRuntimeSeconds,
            currentRunId = currentRunId,
            blockReason = blockReason,
        )
    }

    private fun Double?.toInstant(): Instant? = this?.let { Instant.ofEpochMilli((it * 1000).toLong()) }
}
