package com.hermeh.android.feature.chat

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.ClipEntry
import android.content.ClipData
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hermeh.android.R
import com.hermeh.android.core.designsystem.HermehTheme
import com.hermeh.android.core.model.NoticeKind
import com.hermeh.android.core.model.ToolStatus
import com.hermeh.android.core.model.TranscriptItem
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.MarkdownCodeFence
import com.mikepenz.markdown.compose.elements.MarkdownCodeBlock
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography

@Composable
fun UserBubble(item: TranscriptItem.User, modifier: Modifier = Modifier) {
    val colors = HermehTheme.colors
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        val shape = RoundedCornerShape(20.dp)
        Text(
            item.text,
            color = colors.label,
            fontSize = 15.sp,
            lineHeight = 21.sp,
            modifier = Modifier
                .widthIn(max = maxWidth * 0.8f)
                .clip(shape)
                .background(colors.userBubble)
                .border(1.dp, colors.userBubbleStroke, shape)
                .padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

@Composable
fun AssistantText(item: TranscriptItem.Assistant, modifier: Modifier = Modifier) {
    val colors = HermehTheme.colors
    val body = TextStyle(color = colors.label, fontSize = 16.sp, lineHeight = 23.sp)
    val code = TextStyle(color = colors.label, fontSize = 13.sp, lineHeight = 18.sp, fontFamily = FontFamily.Monospace)
    Markdown(
        content = item.markdown,
        modifier = modifier.fillMaxWidth(),
        colors = markdownColor(
            text = colors.label,
            codeBackground = colors.codeBackground,
            inlineCodeBackground = colors.inlineCode,
            dividerColor = colors.separator,
            tableBackground = colors.tableBackground,
        ),
        typography = markdownTypography(
            h1 = body.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold, lineHeight = 28.sp),
            h2 = body.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold, lineHeight = 26.sp),
            h3 = body.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
            h4 = body.copy(fontWeight = FontWeight.SemiBold),
            h5 = body.copy(fontWeight = FontWeight.SemiBold),
            h6 = body.copy(fontWeight = FontWeight.SemiBold),
            text = body,
            paragraph = body,
            ordered = body,
            bullet = body,
            list = body,
            code = code,
            inlineCode = body.copy(fontFamily = FontFamily.Monospace, fontSize = 14.sp),
            quote = body.copy(color = colors.secondaryLabel),
            table = body.copy(fontSize = 15.sp),
        ),
        components = markdownComponents(
            codeFence = { model ->
                MarkdownCodeFence(model.content, model.node, model.typography.code) { text, language, _ -> CodeBlock(text, language) }
            },
            codeBlock = { model ->
                MarkdownCodeBlock(model.content, model.node, model.typography.code) { text, language, _ -> CodeBlock(text, language) }
            },
        ),
    )
}

@Composable
private fun CodeBlock(code: String, language: String?) {
    val colors = HermehTheme.colors
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val shape = RoundedCornerShape(24.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(shape)
            .background(colors.codeBackground)
            .border(1.dp, colors.separator.copy(alpha = 0.35f), shape)
            .padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                language?.replaceFirstChar { it.uppercase() }?.ifBlank { null } ?: "Code",
                color = colors.label,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Icon(
                Icons.Filled.ContentCopy,
                contentDescription = "Copy code",
                tint = colors.label,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("code", code))) } }
                    .padding(9.dp),
            )
        }
        Spacer(Modifier.size(6.dp))
        Text(
            code,
            color = colors.label,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            softWrap = false,
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        )
    }
}

@Composable
fun ThinkingRow(item: TranscriptItem.Thinking, modifier: Modifier = Modifier) {
    val colors = HermehTheme.colors
    var expanded by rememberSaveable(item.id) { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .then(if (expanded) Modifier.background(colors.secondaryBackground).border(1.dp, colors.separator.copy(alpha = 0.6f), shape) else Modifier)
            .clickable { expanded = !expanded }
            .animateContentSize()
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 32.dp)) {
            Icon(painterResource(R.drawable.ic_lucide_brain), contentDescription = null, tint = colors.secondaryLabel, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text("Thinking", color = colors.label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.size(6.dp))
            Text(
                item.text.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty(),
                color = colors.secondaryLabel,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null, tint = colors.secondaryLabel, modifier = Modifier.size(20.dp))
        }
        if (expanded) {
            Text(
                item.text,
                color = colors.label.copy(alpha = 0.85f),
                fontSize = 12.sp,
                lineHeight = 17.sp,
                modifier = Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState()).padding(top = 4.dp, bottom = 4.dp),
            )
        }
    }
}

private fun toolIcon(name: String): ImageVector = when {
    name == "terminal" -> Icons.Filled.Terminal
    name == "read_file" -> Icons.Filled.Description
    name == "patch" || name == "write_file" -> Icons.Filled.EditNote
    name == "search_files" -> Icons.Filled.Search
    name == "web_search" || name == "web_extract" -> Icons.Filled.Language
    name.startsWith("todo") -> Icons.AutoMirrored.Filled.List
    name.startsWith("delegate") -> Icons.AutoMirrored.Filled.CallSplit
    else -> Icons.Filled.Build
}

private fun toolLabel(name: String): String = when (name) {
    "terminal" -> "Terminal"
    "read_file" -> "Read file"
    "write_file" -> "Write file"
    "patch" -> "Patch"
    "search_files" -> "Search files"
    "web_search" -> "Web search"
    "web_extract" -> "Web extract"
    else -> name.replace('_', ' ').replaceFirstChar { it.uppercase() }
}

@Composable
fun ToolRow(item: TranscriptItem.Tool, modifier: Modifier = Modifier) {
    val colors = HermehTheme.colors
    var expanded by rememberSaveable(item.id) { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    val statusText = when (val status = item.status) {
        ToolStatus.Running -> "running"
        is ToolStatus.Done -> status.seconds?.let { formatDuration(it) } ?: "done"
        is ToolStatus.Failed -> "failed"
        ToolStatus.Interrupted -> "interrupted"
    }
    val statusTag = when (item.status) {
        ToolStatus.Running -> "running"
        is ToolStatus.Done -> "done"
        is ToolStatus.Failed -> "failed"
        ToolStatus.Interrupted -> "interrupted"
    }
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .then(if (expanded) Modifier.background(colors.secondaryBackground).border(1.dp, colors.separator.copy(alpha = 0.6f), shape) else Modifier)
            .clickable { expanded = !expanded }
            .animateContentSize()
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .testTag("chat.tool.$statusTag"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 32.dp)) {
            Icon(toolIcon(item.name), contentDescription = null, tint = colors.secondaryLabel, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text(toolLabel(item.name), color = colors.label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.size(6.dp))
            Text(
                item.context,
                color = colors.secondaryLabel,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.size(6.dp))
            Text(statusText, color = colors.tertiaryLabel, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.size(4.dp))
            val (glyph, tint) = when (item.status) {
                ToolStatus.Running -> Icons.Filled.MoreHoriz to colors.working
                is ToolStatus.Done -> Icons.Filled.Check to colors.secondaryLabel
                is ToolStatus.Failed -> Icons.Filled.Close to colors.danger
                ToolStatus.Interrupted -> Icons.Filled.Remove to colors.secondaryLabel
            }
            Icon(glyph, contentDescription = statusTag, tint = tint, modifier = Modifier.size(16.dp))
        }
        if (expanded) {
            Column(
                Modifier.padding(start = 26.dp, top = 4.dp, bottom = 4.dp).heightIn(max = 240.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(item.argsJson, color = colors.secondaryLabel, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                item.output?.takeIf { it.isNotBlank() }?.let {
                    Text(it, color = colors.label, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                }
                (item.status as? ToolStatus.Failed)?.let {
                    Text(it.error, color = colors.danger, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun NoticeRow(item: TranscriptItem.Notice, modifier: Modifier = Modifier) {
    val colors = HermehTheme.colors
    val tint = if (item.kind == NoticeKind.Error) colors.danger else colors.secondaryLabel
    Row(
        modifier.fillMaxWidth().padding(vertical = 4.dp).testTag("chat.notice"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(if (item.kind == NoticeKind.Error) Icons.Filled.Warning else Icons.Filled.Remove, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        Text(item.text, color = tint, style = MaterialTheme.typography.labelMedium)
    }
}

fun formatDuration(seconds: Double): String = when {
    seconds < 1 -> "${(seconds * 1000).toInt()}ms"
    seconds < 60 -> "%.1fs".format(seconds)
    else -> "${(seconds / 60).toInt()}m ${(seconds % 60).toInt()}s"
}
