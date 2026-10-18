package com.hermex.android.feature.kanban

import androidx.compose.ui.graphics.Color
import com.hermex.android.core.model.KanbanStatus
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

internal fun abbreviatedAge(seconds: Long): String {
    val value = seconds.coerceAtLeast(0)
    return when {
        value >= 86_400 -> "${value / 86_400}d"
        value >= 3_600 -> "${value / 3_600}h"
        else -> "${value / 60}m"
    }
}

internal fun fullDuration(seconds: Long): String {
    val value = seconds.coerceAtLeast(0)
    fun unit(count: Long, name: String) = "$count $name${if (count == 1L) "" else "s"}"
    return when {
        value >= 86_400 -> unit(value / 86_400, "day")
        value >= 3_600 -> unit(value / 3_600, "hour")
        value >= 60 -> unit(value / 60, "minute")
        else -> unit(value, "second")
    }
}

private val markdownNoise = Regex("""(\*\*|__|[*_`~#>]|!?\[([^\]]*)]\([^)]*\))""")

internal fun plainPreview(markdown: String): String =
    markdown.replace(markdownNoise) { match -> match.groupValues[2] }
        .lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" ")

private val dateTime = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)

internal fun formatDate(instant: Instant?): String? = instant?.let { dateTime.format(it.atZone(ZoneId.systemDefault())) }

internal fun statusColor(status: KanbanStatus?, dark: Boolean): Color = when (status) {
    KanbanStatus.Triage -> if (dark) Color(0xFF98989D) else Color(0xFF8E8E93)
    KanbanStatus.Todo -> if (dark) Color(0xFF0A84FF) else Color(0xFF007AFF)
    KanbanStatus.Scheduled -> if (dark) Color(0xFF5E5CE6) else Color(0xFF5856D6)
    KanbanStatus.Ready -> if (dark) Color(0xFF63E6E2) else Color(0xFF00C7BE)
    KanbanStatus.Running -> if (dark) Color(0xFFFF9F0A) else Color(0xFFFF9500)
    KanbanStatus.Blocked -> if (dark) Color(0xFFFF453A) else Color(0xFFFF3B30)
    KanbanStatus.Review -> if (dark) Color(0xFFFFD60A) else Color(0xFFFFCC00)
    KanbanStatus.Done -> if (dark) Color(0xFF30D158) else Color(0xFF34C759)
    KanbanStatus.Archived -> if (dark) Color(0xFF8D8D93) else Color(0xFF8A8A8E)
    null -> if (dark) Color(0xFFBF5AF2) else Color(0xFFAF52DE)
}

internal fun countLabel(count: Int, singular: String, plural: String = "${singular}s") =
    "$count ${if (count == 1) singular else plural}"
