package com.hermex.android.feature.memory

import com.hermex.android.core.designsystem.CardShape
import com.hermex.android.core.designsystem.PanelScaffold
import com.hermex.android.core.designsystem.PanelTopBar
import com.hermex.android.core.designsystem.BarIcon
import com.hermex.android.core.designsystem.ContentUnavailable
import com.hermex.android.core.designsystem.LoadingState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hermex.android.R
import com.hermex.android.core.designsystem.HermexTheme
import com.hermex.android.core.model.MemoryEntry
import com.hermex.android.core.model.MemorySection
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import java.util.Locale

@Composable
internal fun MemoryListRoute(viewModel: MemoryViewModel, onBack: () -> Unit, onEdit: (MemorySection) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    MemoryListScreen(state, onBack, viewModel::load, onEdit)
}

@Composable
private fun sectionIcon(section: MemorySection): Painter = when (section) {
    MemorySection.Notes -> painterResource(R.drawable.ic_lucide_brain)
    MemorySection.User -> painterResource(R.drawable.ic_lucide_user_round)
    MemorySection.Soul -> rememberVectorPainter(Icons.Filled.AutoAwesome)
}

@Composable
internal fun MemoryListScreen(
    state: MemoryScreenState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onEdit: (MemorySection) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HermexTheme.colors
    PanelScaffold(modifier) {
        PanelTopBar(title = "Memory", onBack = onBack, backTag = "memory.back") {
            BarIcon(Icons.Filled.Refresh, "Refresh", "memory.refresh", busy = state.refreshing && state.content !is MemoryUiState.Loading, onClick = onRefresh)
        }
        when (val content = state.content) {
            MemoryUiState.Loading -> LoadingState("Loading memory...")
            is MemoryUiState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ContentUnavailable(Icons.Filled.Warning, "Could Not Load Memory", content.message, actionLabel = "Try Again", actionTag = "memory.retry", onAction = onRefresh)
            }
            is MemoryUiState.Success -> Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                content.entries.forEach { entry -> MemorySectionCard(entry, enabled = !state.refreshing, onEdit = { onEdit(entry.section) }) }
                Spacer(Modifier.size(24.dp))
            }
        }
    }
}

@Composable
private fun MemorySectionCard(entry: MemoryEntry, enabled: Boolean, onEdit: () -> Unit) {
    val colors = HermexTheme.colors
    Column(Modifier.fillMaxWidth().testTag("memory.section.${entry.section.slug}")) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(sectionIcon(entry.section), contentDescription = null, tint = colors.secondaryLabel, modifier = Modifier.size(16.dp))
            Text(
                entry.section.title.uppercase(Locale.getDefault()),
                color = colors.secondaryLabel, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 6.dp).weight(1f),
            )
            if (entry.readOnly) {
                Icon(Icons.Filled.Lock, contentDescription = "Read-only", tint = colors.secondaryLabel, modifier = Modifier.size(18.dp))
            } else {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).clickable(enabled = enabled, onClick = onEdit).testTag("memory.edit.${entry.section.slug}"),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Edit, contentDescription = "Edit ${entry.section.title}", tint = colors.accent, modifier = Modifier.size(20.dp))
                }
            }
        }
        Column(Modifier.fillMaxWidth().clip(CardShape).background(colors.secondaryBackground).padding(horizontal = 16.dp, vertical = 12.dp)) {
            if (entry.text.isBlank()) {
                Text(entry.section.emptyMessage, color = colors.secondaryLabel, fontStyle = FontStyle.Italic, fontSize = 16.sp, modifier = Modifier.testTag("memory.text.${entry.section.slug}"))
            } else {
                MemoryMarkdown(entry.text, Modifier.testTag("memory.text.${entry.section.slug}"))
            }
        }
    }
}

@Composable
private fun MemoryMarkdown(content: String, modifier: Modifier = Modifier) {
    val colors = HermexTheme.colors
    val body = TextStyle(color = colors.label, fontSize = 16.sp, lineHeight = 22.sp)
    Markdown(
        content = content,
        modifier = modifier.fillMaxWidth(),
        colors = markdownColor(
            text = colors.label,
            codeBackground = colors.codeBackground,
            inlineCodeBackground = colors.inlineCode,
            dividerColor = colors.separator,
            tableBackground = colors.tableBackground,
        ),
        typography = markdownTypography(
            h1 = body.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold),
            h2 = body.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold),
            h3 = body.copy(fontWeight = FontWeight.SemiBold),
            h4 = body.copy(fontWeight = FontWeight.SemiBold),
            h5 = body.copy(fontWeight = FontWeight.SemiBold),
            h6 = body.copy(fontWeight = FontWeight.SemiBold),
            text = body,
            paragraph = body,
            ordered = body,
            bullet = body,
            list = body,
            code = body.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
            inlineCode = body.copy(fontFamily = FontFamily.Monospace, fontSize = 14.sp),
            quote = body.copy(color = colors.secondaryLabel),
            table = body.copy(fontSize = 15.sp),
        ),
    )
}

@Composable
internal fun MemoryEditRoute(viewModel: MemoryViewModel, section: MemorySection, onClose: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val draft = state.draft
    LaunchedEffect(draft?.saved) { if (draft?.saved == true) onClose() }
    LaunchedEffect(draft == null) { if (draft == null) viewModel.beginEdit(section) }
    MemoryEditScreen(
        draft = draft,
        section = section,
        onCancel = onClose,
        onSave = viewModel::save,
        onText = viewModel::editText,
        onReload = viewModel::reloadDraft,
        onKeepDraft = viewModel::keepDraft,
    )
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun MemoryEditScreen(
    draft: MemoryDraft?,
    section: MemorySection,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    onText: (String) -> Unit,
    onReload: () -> Unit,
    onKeepDraft: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HermexTheme.colors
    PanelScaffold(modifier) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onCancel, enabled = draft?.saving != true, modifier = Modifier.testTag("memory.cancel")) {
                Text("Cancel", color = colors.label, fontSize = 16.sp)
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Edit ${section.title}", color = colors.label, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 1)
                if (draft?.limit != null) {
                    Text(
                        "${String.format(Locale.getDefault(), "%,d", draft.count)} of ${String.format(Locale.getDefault(), "%,d", draft.limit)} characters",
                        color = if (draft.isOver) colors.danger else colors.secondaryLabel,
                        fontSize = 12.sp,
                        modifier = Modifier.testTag("memory.counter"),
                    )
                }
            }
            Box(Modifier.heightIn(min = 48.dp), contentAlignment = Alignment.Center) {
                if (draft?.saving == true) {
                    CircularProgressIndicator(Modifier.padding(horizontal = 24.dp).size(18.dp), color = colors.secondaryLabel, strokeWidth = 2.dp)
                } else {
                    TextButton(onClick = onSave, enabled = draft?.canSave == true, modifier = Modifier.testTag("memory.save")) {
                        Text("Save", color = if (draft?.canSave == true) colors.accent else colors.tertiaryLabel, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        if (draft == null) {
            LoadingState("Loading memory...")
            return@PanelScaffold
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).windowInsetsPadding(WindowInsets.ime).windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(section.title.uppercase(Locale.getDefault()), color = colors.secondaryLabel, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp))
            BasicTextField(
                value = draft.text,
                onValueChange = onText,
                enabled = !draft.saving && !draft.reloading && !draft.readOnly,
                textStyle = TextStyle(color = colors.label, fontSize = 15.sp, lineHeight = 21.sp, fontFamily = FontFamily.Monospace),
                cursorBrush = SolidColor(colors.accent),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 320.dp)
                    .clip(CardShape)
                    .background(colors.secondaryBackground)
                    .padding(14.dp)
                    .testTag("memory.editor"),
            )
            when {
                draft.readOnly -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp)) {
                    Icon(Icons.Filled.Lock, null, tint = colors.secondaryLabel, modifier = Modifier.size(14.dp))
                    Text("Read-only", color = colors.secondaryLabel, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp))
                }
                draft.isOver -> Text("Over the host's limit. Shorten to save.", color = colors.danger, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp))
                else -> Text("Edits apply from the agent's next session.", color = colors.secondaryLabel, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp))
            }
            draft.error?.let { Text(it, color = colors.danger, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp).testTag("memory.error")) }
            Spacer(Modifier.size(24.dp))
        }
    }
    if (draft?.conflicted == true) {
        AlertDialog(
            onDismissRequest = onKeepDraft,
            icon = { Icon(Icons.Filled.Warning, null, tint = colors.approval) },
            title = { Text("Changed on the host") },
            text = { Text("${section.title} changed since you opened it. Your draft is kept. Reload shows the host's text and drops this draft.") },
            confirmButton = {
                TextButton(onClick = onReload, modifier = Modifier.semantics { testTagsAsResourceId = true }.testTag("memory.reload")) { Text("Reload") }
            },
            dismissButton = {
                TextButton(onClick = onKeepDraft, modifier = Modifier.semantics { testTagsAsResourceId = true }.testTag("memory.conflict.keep")) { Text("Keep Draft") }
            },
        )
    }
}
