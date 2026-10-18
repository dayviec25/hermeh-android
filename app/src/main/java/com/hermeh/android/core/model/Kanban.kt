package com.hermeh.android.core.model

import java.time.Instant

@JvmInline value class CardId(val value: String)

enum class KanbanStatus(val wire: String, val title: String) {
    Triage("triage", "Triage"),
    Todo("todo", "To Do"),
    Scheduled("scheduled", "Scheduled"),
    Ready("ready", "Ready"),
    Running("running", "Running"),
    Blocked("blocked", "Blocked"),
    Review("review", "Review"),
    Done("done", "Done"),
    Archived("archived", "Archived"),
    ;

    companion object {
        val board: List<KanbanStatus> = entries.filter { it != Archived }

        val movable: List<KanbanStatus> = listOf(Triage, Ready)

        fun fromWire(value: String?): KanbanStatus? = entries.firstOrNull { it.wire == value }
    }
}

enum class WorkspaceKind(val wire: String, val title: String) {
    Scratch("scratch", "Scratch"),
    Worktree("worktree", "Worktree"),
    Directory("dir", "Directory"),
    ;

    companion object {
        fun fromWire(value: String?): WorkspaceKind? = entries.firstOrNull { it.wire == value || it.name.equals(value, ignoreCase = true) }
    }
}

data class KanbanBoard(
    val slug: String,
    val name: String,
    val description: String,
    val isCurrent: Boolean,
    val total: Int,
)

data class KanbanCard(
    val id: CardId,
    val title: String,
    val status: KanbanStatus?,
    val body: String,
    val assignee: String?,
    val tenant: String?,
    val priority: Int,
    val commentCount: Int,
    val parentCount: Int,
    val childCount: Int,
    val ageSeconds: Long?,
    val createdAt: Instant?,
    val updatedAt: Instant?,
    val workspaceKind: WorkspaceKind?,
    val skills: List<String>,
    val maxRuntimeSeconds: Long?,
    val currentRunId: String?,
    val blockReason: String?,
) {
    val canBlock: Boolean get() = status == KanbanStatus.Ready || status == KanbanStatus.Running
    val canUnblock: Boolean get() = status == KanbanStatus.Blocked
    val canComplete: Boolean get() = status == KanbanStatus.Review
    val canArchive: Boolean get() = status != KanbanStatus.Archived
    val moveDestinations: List<KanbanStatus> get() = KanbanStatus.movable.filter { it != status }
}

data class KanbanBoardSnapshot(
    val columns: Map<KanbanStatus, List<KanbanCard>>,
    val assignees: List<String>,
    val latestEventId: Long,
)

data class KanbanComment(val id: String, val author: String, val body: String, val createdAt: Instant?)

data class KanbanRun(val id: String, val status: String, val profile: String?, val startedAt: Instant?)

data class CardDetail(
    val card: KanbanCard,
    val comments: List<KanbanComment>,
    val prerequisites: List<String>,
    val dependents: List<String>,
    val runs: List<KanbanRun>,
)

data class WorkerLog(val exists: Boolean, val content: String, val truncated: Boolean, val sizeBytes: Long)

data class DispatchResult(
    val dryRun: Boolean,
    val spawned: Int,
    val promoted: Int,
    val reclaimed: Int,
    val skippedUnassigned: Int,
    val skippedNonspawnable: Int,
    val autoBlocked: Int,
    val timedOut: Int,
    val crashed: Int,
    val completedAt: Instant,
)

data class CardDraft(
    val title: String,
    val body: String,
    val status: KanbanStatus,
    val priority: Int,
    val assignee: String?,
    val workspaceKind: WorkspaceKind,
    val skills: List<String>,
    val maxRuntimeSeconds: Long?,
)
