package com.hermeh.android.feature.kanban

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hermeh.android.core.designsystem.HermehTheme
import com.hermeh.android.core.designsystem.glassSurface
import com.hermeh.android.core.model.KanbanCard
import com.hermeh.android.core.model.KanbanStatus
import kotlinx.coroutines.delay

private const val NOTICE_MS = 4_000L

sealed interface CardAction {
    data class Move(val to: KanbanStatus) : CardAction
    data object Block : CardAction
    data object Unblock : CardAction
    data object Complete : CardAction
    data object Archive : CardAction
}

internal fun KanbanCard.actions(): List<CardAction> = buildList {
    moveDestinations.forEach { add(CardAction.Move(it)) }
    if (canUnblock) add(CardAction.Unblock) else if (canBlock) add(CardAction.Block)
    if (canComplete) add(CardAction.Complete)
    if (canArchive) add(CardAction.Archive)
}

internal fun CardAction.label(): String = when (this) {
    is CardAction.Move -> "Move to ${to.title}"
    CardAction.Block -> "Block"
    CardAction.Unblock -> "Unblock"
    CardAction.Complete -> "Complete"
    CardAction.Archive -> "Archive"
}

internal fun CardAction.tag(): String = when (this) {
    is CardAction.Move -> "card.move.${to.wire}"
    CardAction.Block -> "card.block"
    CardAction.Unblock -> "card.unblock"
    CardAction.Complete -> "card.complete"
    CardAction.Archive -> "card.archive"
}

@Composable
internal fun PillIconButton(icon: ImageVector, description: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val colors = HermehTheme.colors
    Box(
        modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = if (enabled) colors.label else colors.tertiaryLabel, modifier = Modifier.size(22.dp))
    }
}

@Composable
internal fun GlassPill(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(modifier.glassSurface(CircleShape).padding(2.dp), verticalAlignment = Alignment.CenterVertically, content = content)
}

@Composable
internal fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val colors = HermehTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .glassSurface(RoundedCornerShape(22.dp))
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Search, contentDescription = null, tint = colors.secondaryLabel, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(placeholder, color = colors.secondaryLabel, fontSize = 16.sp)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(color = colors.label, fontSize = 16.sp),
                cursorBrush = SolidColor(colors.accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty()) {
            Icon(
                Icons.Outlined.Close,
                contentDescription = "Clear",
                tint = colors.secondaryLabel,
                modifier = Modifier.size(20.dp).clip(CircleShape).clickable { onValueChange("") },
            )
        }
    }
}

@Composable
internal fun StatusSelector(
    counts: Map<KanbanStatus, Int>,
    selected: KanbanStatus,
    onSelect: (KanbanStatus) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HermehTheme.colors
    LazyRow(
        modifier = modifier.fillMaxWidth().testTag("kanban.status"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(KanbanStatus.board, key = { it.wire }, contentType = { "status" }) { status ->
            val tint = statusColor(status, colors.isDark)
            val isSelected = status == selected
            val shape = CircleShape
            Row(
                Modifier
                    .heightIn(min = 44.dp)
                    .clip(shape)
                    .background(if (isSelected) tint.copy(alpha = if (colors.isDark) 0.24f else 0.16f) else colors.secondaryBackground, shape)
                    .border(1.dp, if (isSelected) tint.copy(alpha = 0.6f) else colors.glassStroke, shape)
                    .clickable { onSelect(status) }
                    .padding(horizontal = 14.dp)
                    .testTag("kanban.status.${status.wire}")
                    .semantics { contentDescription = "${status.title}, ${counts[status] ?: 0}" },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(tint))
                Text(status.title, color = colors.label, fontSize = 14.sp, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium)
                Text(
                    "${counts[status] ?: 0}",
                    color = if (isSelected) colors.label else colors.secondaryLabel,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
internal fun CardRow(
    row: CardRowUi,
    busy: Boolean,
    onOpen: () -> Unit,
    onAction: (CardAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HermehTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(colors.secondaryBackground)
            .clickable(onClick = onOpen)
            .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp)
            .testTag("kanban.card.${row.key}"),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    row.priority,
                    color = colors.label,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(CircleShape).background(colors.label.copy(alpha = 0.08f)).padding(horizontal = 6.dp, vertical = 1.dp),
                )
                Text(row.key, color = colors.secondaryLabel, fontSize = 12.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                if (row.age != null) {
                    Meta(Icons.Outlined.Schedule, row.age)
                }
            }
            Text(row.card.title, color = colors.label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 3, overflow = TextOverflow.Ellipsis)
            if (row.preview.isNotEmpty()) {
                Text(row.preview, color = colors.secondaryLabel, fontSize = 14.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Meta(Icons.Outlined.Person, row.assignee)
                if (row.card.commentCount > 0) Meta(Icons.AutoMirrored.Outlined.Chat, "${row.card.commentCount}")
                val links = row.card.parentCount + row.card.childCount
                if (links > 0) Meta(Icons.Outlined.Link, "$links")
                if (busy) CircularProgressIndicator(strokeWidth = 2.dp, color = colors.secondaryLabel, modifier = Modifier.size(14.dp))
            }
        }
        CardActionsMenu(row.card, enabled = !busy, onAction = onAction, tag = "kanban.card.${row.key}.menu")
    }
}

@Composable
internal fun CardActionsMenu(card: KanbanCard, enabled: Boolean, onAction: (CardAction) -> Unit, tag: String) {
    var open by remember { mutableStateOf(false) }
    Box {
        PillIconButton(Icons.Outlined.MoreHoriz, "Card Actions", { open = true }, Modifier.testTag(tag), enabled = enabled)
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.semantics { testTagsAsResourceId = true }) {
            card.actions().forEach { action ->
                DropdownMenuItem(
                    text = { Text(action.label(), color = if (action == CardAction.Archive) HermehTheme.colors.danger else HermehTheme.colors.label) },
                    onClick = {
                        open = false
                        onAction(action)
                    },
                    modifier = Modifier.testTag(action.tag()),
                )
            }
        }
    }
}

@Composable
private fun Meta(icon: ImageVector, text: String) {
    val colors = HermehTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Icon(icon, contentDescription = null, tint = colors.secondaryLabel, modifier = Modifier.size(14.dp))
        Text(text, color = colors.secondaryLabel, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
internal fun SectionCard(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val colors = HermehTheme.colors
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Text(
            title.uppercase(),
            color = colors.secondaryLabel,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 16.dp, bottom = 6.dp),
        )
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(colors.secondaryBackground)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

@Composable
internal fun LabeledValue(label: String, value: String, modifier: Modifier = Modifier, mono: Boolean = false) {
    val colors = HermehTheme.colors
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(label, color = colors.label, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Text(
            value,
            color = colors.secondaryLabel,
            fontSize = if (mono) 14.sp else 15.sp,
            fontFamily = if (mono) FontFamily.Monospace else null,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1.4f),
        )
    }
}

@Composable
internal fun Unavailable(title: String, detail: String, onRetry: (() -> Unit)?, modifier: Modifier = Modifier) {
    val colors = HermehTheme.colors
    Column(
        modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, color = colors.label, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Text(detail, color = colors.secondaryLabel, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
        if (onRetry != null) {
            TextButton(onClick = onRetry, modifier = Modifier.padding(top = 8.dp).testTag("kanban.retry")) {
                Text("Try Again", color = colors.accent, fontSize = 16.sp)
            }
        }
    }
}

@Composable
internal fun NoticeBanner(text: String, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val colors = HermehTheme.colors
    LaunchedEffect(text) {
        delay(NOTICE_MS)
        onDismiss()
    }
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.accent.copy(alpha = if (colors.isDark) 0.22f else 0.12f))
            .padding(start = 14.dp, end = 4.dp)
            .heightIn(min = 44.dp)
            .testTag("kanban.notice"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, color = colors.label, fontSize = 14.sp, modifier = Modifier.weight(1f))
        PillIconButton(Icons.Outlined.Close, "Dismiss", onDismiss)
    }
}
