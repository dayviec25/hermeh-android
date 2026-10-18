package com.hermex.android.core.model

import java.time.Instant

data class CronSchedule(
    val kind: String?,
    val expression: String?,
    val runAt: String?,
    val every: String?,
) {
    val displayText: String? get() = expression ?: runAt ?: every ?: kind
}

data class CronJob(
    val id: String,
    val name: String?,
    val prompt: String?,
    val schedule: CronSchedule?,
    val scheduleDisplay: String?,
    val enabled: Boolean?,
    val state: String?,
    val nextRunAt: Instant?,
    val lastRunAt: Instant?,
    val lastStatus: String?,
    val lastError: String?,
    val lastDeliveryError: String?,
    val repeatTimes: Int?,
    val repeatCompleted: Int?,
    val deliver: String?,
    val skills: List<String>,
    val model: String?,
    val provider: String?,
    val profile: String?,
    val fireClaimAt: Instant?,
    val hasFireClaim: Boolean,
    val latestExecutionStatus: String?,
    val schedulerHeartbeatAge: Double?,
) {
    val displayName: String
        get() = name?.takeIf { it.isNotBlank() } ?: scheduleText?.takeIf { it.isNotBlank() } ?: "Untitled Task"

    val scheduleText: String? get() = scheduleDisplay ?: schedule?.displayText

    val editableSchedule: String?
        get() = schedule?.expression ?: schedule?.runAt ?: schedule?.every ?: scheduleDisplay

    val isLive: Boolean get() = enabled != false && state != "paused"

    val isRunning: Boolean
        get() = hasFireClaim || latestExecutionStatus == "claimed" || latestExecutionStatus == "running"

    val hasFailedRun: Boolean
        get() = lastStatus == "error" || !lastError.isNullOrBlank() || !lastDeliveryError.isNullOrBlank()

    val failureSummary: String?
        get() = listOf(lastError, lastDeliveryError).firstNotNullOfOrNull { text ->
            text?.replace(Regex("\u001B\\[[0-9;?]*[A-Za-z]"), "")
                ?.lineSequence()?.map { it.trim() }?.firstOrNull { it.isNotEmpty() }
        }

    val status: CronJobStatus
        get() {
            val recurring = schedule?.kind == "cron" || schedule?.kind == "interval"
            return when {
                recurring && repeatTimes == null && enabled == false && state == "completed" && nextRunAt == null -> CronJobStatus.NeedsAttention
                recurring && nextRunAt == null && (state == "error" || lastStatus == "error") -> CronJobStatus.NeedsAttention
                state == "paused" -> CronJobStatus.Paused
                enabled == false -> CronJobStatus.Off
                lastStatus == "error" -> CronJobStatus.Error
                else -> CronJobStatus.Active
            }
        }

    val shouldResume: Boolean get() = status == CronJobStatus.Paused || status == CronJobStatus.Off
}

enum class CronJobStatus(val label: String) {
    Active("Active"),
    Paused("Paused"),
    Off("Off"),
    Error("Last run failed"),
    NeedsAttention("Needs Attention"),
}

data class CronRun(
    val id: String,
    val startedAt: Instant?,
    val endedAt: Instant?,
    val isActive: Boolean,
    val model: String?,
    val inputTokens: Long?,
    val outputTokens: Long?,
    val costUsd: Double?,
) {
    val totalTokens: Long? get() = if (inputTokens == null && outputTokens == null) null else (inputTokens ?: 0) + (outputTokens ?: 0)
}

data class DeliveryTarget(val id: String, val name: String)

data class CronDraft(
    val name: String = "",
    val prompt: String = "",
    val schedule: String = "",
    val deliver: String = "local",
    val skills: List<String> = emptyList(),
    val model: String? = null,
    val provider: String? = null,
) {
    val validationMessage: String?
        get() = when {
            prompt.isBlank() -> "Prompt is required."
            schedule.isBlank() -> "Schedule is required."
            else -> null
        }

    companion object {
        fun from(job: CronJob) = CronDraft(
            name = job.name.orEmpty(),
            prompt = job.prompt.orEmpty(),
            schedule = job.editableSchedule.orEmpty(),
            deliver = job.deliver?.takeIf { it.isNotBlank() } ?: "local",
            skills = job.skills,
            model = job.model?.takeIf { it.isNotBlank() },
            provider = job.provider?.takeIf { it.isNotBlank() },
        )
    }
}

sealed interface CronSaveResult {
    data class Saved(val job: CronJob) : CronSaveResult
    data class SavedWithWarning(val warning: String?) : CronSaveResult
}

data class Skill(
    val name: String,
    val description: String?,
    val category: String?,
    val enabled: Boolean,
)

data class SkillDetail(
    val name: String,
    val content: String,
    val linkedFiles: List<String>,
)

data class SkillFile(val name: String, val text: String?, val binary: Boolean, val truncated: Boolean)

enum class MemorySection(val slug: String, val title: String, val emptyMessage: String) {
    Notes("notes", "My Notes", "No notes yet."),
    User("user", "User Profile", "No profile yet."),
    Soul("soul", "Agent Soul", "No soul defined yet."),
}

data class MemoryEntry(
    val section: MemorySection,
    val text: String,
    val limit: Int?,
    val readOnly: Boolean,
)

data class MemorySnapshot(val entries: List<MemoryEntry>)

data class UsageDay(
    val day: String,
    val inputTokens: Long,
    val outputTokens: Long,
    val cacheReadTokens: Long,
    val reasoningTokens: Long,
    val estimatedCost: Double,
    val actualCost: Double,
    val sessions: Int,
    val apiCalls: Int,
)

data class UsageModel(
    val model: String,
    val provider: String?,
    val inputTokens: Long,
    val outputTokens: Long,
    val cacheReadTokens: Long,
    val estimatedCost: Double,
    val sessions: Int,
    val apiCalls: Int,
)

data class UsageTool(val tool: String, val count: Int, val percentage: Double)

data class UsageTotals(
    val input: Long,
    val output: Long,
    val cacheRead: Long,
    val reasoning: Long,
    val estimatedCost: Double,
    val actualCost: Double,
    val sessions: Int,
    val apiCalls: Int,
)

data class UsageReport(
    val periodDays: Int,
    val daily: List<UsageDay>,
    val models: List<UsageModel>,
    val totals: UsageTotals,
    val tools: List<UsageTool>,
)
