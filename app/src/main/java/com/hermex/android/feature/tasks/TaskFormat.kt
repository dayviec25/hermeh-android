package com.hermex.android.feature.tasks

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.hermex.android.core.designsystem.HermexTheme
import com.hermex.android.core.model.CronJob
import com.hermex.android.core.model.CronJobStatus
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal val SystemOrange = Color(0xFFFF9500)

internal object TaskFormat {
    private val full = DateTimeFormatter.ofPattern("MMM d, yyyy 'at' h:mm a", Locale.getDefault())

    fun date(instant: Instant): String = full.format(instant.atZone(ZoneId.systemDefault()))

    fun countdown(to: Instant, now: Instant = Instant.now()): String {
        val seconds = Duration.between(now, to).seconds
        if (seconds <= 0) return "Due now"
        val minutes = (seconds + 59) / 60
        return when {
            minutes < 60 -> "in $minutes min"
            minutes < 60 * 24 -> "in ${minutes / 60} hr ${minutes % 60} min".replace(" 0 min", "")
            else -> "in ${minutes / (60 * 24)} days"
        }
    }

    fun elapsed(seconds: Double): String =
        if (seconds < 60) "${seconds.toInt()}s" else "${(seconds / 60).toInt()}m ${(seconds % 60).toInt()}s"

    fun duration(seconds: Double): String = when {
        seconds < 60 -> "${seconds.toInt()}s"
        seconds < 3600 -> "${(seconds / 60).toInt()}m ${(seconds % 60).toInt()}s"
        else -> "${(seconds / 3600).toInt()}h ${((seconds % 3600) / 60).toInt()}m"
    }

    fun tokens(value: Long): String = String.format(Locale.getDefault(), "%,d", value)

    fun cost(value: Double): String =
        if (value > 0 && value < 0.01) String.format(Locale.US, "$%.4f", value) else String.format(Locale.US, "$%.2f", value)

    fun stalled(seconds: Double): String {
        val total = seconds.toLong()
        val days = total / 86_400
        val hours = (total % 86_400) / 3600
        val minutes = (total % 3600) / 60
        val parts = buildList {
            if (days > 0) add("$days ${if (days == 1L) "day" else "days"}")
            if (hours > 0) add("$hours ${if (hours == 1L) "hour" else "hours"}")
            if (minutes > 0) add("$minutes ${if (minutes == 1L) "minute" else "minutes"}")
        }.take(2)
        return parts.joinToString(", ").ifEmpty { "$total seconds" }
    }
}

internal enum class TaskFilter(val slug: String, val title: String) {
    All("all", "All"),
    Live("live", "Live"),
    Paused("paused", "Paused"),
    Errors("errors", "Errors"),
    ;

    fun matches(job: CronJob): Boolean = when (this) {
        All -> true
        Live -> job.isRunning || job.isLive
        Paused -> !job.isRunning && !job.isLive
        Errors -> job.hasFailedRun
    }
}

internal fun CronJob.badgeText(): String = when {
    isRunning -> "Running"
    hasFailedRun && status == CronJobStatus.Active -> "Last run failed"
    else -> status.label
}

@Composable
internal fun CronJob.badgeColor(): Color {
    val colors = HermexTheme.colors
    if (isRunning) return colors.working
    return when (status) {
        CronJobStatus.Active -> if (hasFailedRun) colors.danger else colors.success
        CronJobStatus.Paused, CronJobStatus.Off -> SystemOrange
        CronJobStatus.Error -> colors.danger
        CronJobStatus.NeedsAttention -> colors.approval
    }
}
