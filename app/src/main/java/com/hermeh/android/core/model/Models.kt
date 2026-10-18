package com.hermeh.android.core.model

import java.time.Instant

@JvmInline value class StoredKey(val value: String)

@JvmInline value class RuntimeId(val value: String)

@JvmInline value class ProfileName(val value: String) {
    companion object {
        val Default = ProfileName("default")
    }
}

data class ServerConfig(
    val id: String,
    val name: String,
    val baseUrl: String,
    val username: String,
    val customHeaders: List<Pair<String, String>>,
)

data class HermesUser(
    val userId: String,
    val displayName: String,
    val provider: String,
)

sealed interface AuthState {
    data object Unconfigured : AuthState
    data class SignedOut(val server: ServerConfig, val message: String?) : AuthState
    data class SignedIn(val server: ServerConfig, val user: HermesUser, val hostVersion: String) : AuthState
}

data class SessionSummary(
    val key: StoredKey,
    val profile: ProfileName,
    val title: String,
    val preview: String,
    val messageCount: Int,
    val lastActive: Instant,
    val pinned: Boolean,
    val archived: Boolean,
    val unread: Boolean,
    val isActive: Boolean,
    val model: String?,
    val parentKey: StoredKey?,
    val cwd: String? = null,
)

data class ProfileSummary(
    val name: ProfileName,
    val displayName: String,
    val model: String?,
    val isDefault: Boolean,
)

data class ProjectLane(
    val id: String,
    val label: String,
    val color: String?,
    val path: String?,
    val isAuto: Boolean,
    val sessionKeys: Set<StoredKey>,
    val sessionCount: Int,
)

sealed interface DeleteOutcome {
    data object Deleted : DeleteOutcome
    data object BusyHere : DeleteOutcome
    data object OpenElsewhere : DeleteOutcome
}

data class DuplicateResult(val key: StoredKey, val title: String?)

data class BranchResult(val key: StoredKey, val title: String?)

data class FileEntry(val name: String, val path: String, val isDirectory: Boolean)

sealed interface FilePreview {
    val path: String

    data class Text(override val path: String, val text: String, val truncated: Boolean, val byteSize: Long?, val language: String?) : FilePreview
    data class Binary(override val path: String, val byteSize: Long?) : FilePreview
}

enum class NoticeKind { Error, Interrupted }

sealed interface TranscriptItem {
    val id: String

    data class User(override val id: String, val text: String, val steer: Boolean) : TranscriptItem
    data class Assistant(override val id: String, val markdown: String, val streaming: Boolean) : TranscriptItem
    data class Thinking(override val id: String, val text: String) : TranscriptItem
    data class Tool(
        override val id: String,
        val name: String,
        val context: String,
        val argsJson: String,
        val status: ToolStatus,
        val output: String?,
    ) : TranscriptItem

    data class Notice(override val id: String, val text: String, val kind: NoticeKind) : TranscriptItem
}

sealed interface ToolStatus {
    data object Running : ToolStatus
    data class Done(val seconds: Double?) : ToolStatus
    data class Failed(val error: String) : ToolStatus
    data object Interrupted : ToolStatus
}

enum class ApprovalChoice(val wire: String, val label: String) {
    Once("once", "Once"),
    Session("session", "Session"),
    Always("always", "Always"),
    Deny("deny", "Deny"),
    ;

    companion object {
        fun fromWire(value: String): ApprovalChoice? = entries.firstOrNull { it.wire == value }
    }
}

sealed interface PendingRequest {
    val envelopeId: String

    data class Approval(
        override val envelopeId: String,
        val requestId: String,
        val command: String,
        val description: String,
        val choices: List<ApprovalChoice>,
    ) : PendingRequest

    data class Clarify(
        override val envelopeId: String,
        val requestId: String,
        val question: String,
        val options: List<String>,
    ) : PendingRequest

    data class Credential(override val envelopeId: String, val kind: String, val prompt: String) : PendingRequest
}

sealed interface ConnectionState {
    data object Online : ConnectionState
    data object Reconnecting : ConnectionState
    data object WaitingForNetwork : ConnectionState
    data class Failed(val message: String) : ConnectionState
}

sealed interface RunState {
    data object Idle : RunState
    data class Working(val since: Instant, val caption: String) : RunState
    data object WaitingForYou : RunState
    data object Stopping : RunState
    data object Reconnecting : RunState
}

data class ComposerState(
    val text: String,
    val model: String?,
    val provider: String?,
    val reasoning: String?,
    val profile: ProfileName,
    val cwd: String?,
)
