package com.hermex.android.feature.tasks

import com.hermex.android.core.designsystem.PanelScaffold
import com.hermex.android.core.designsystem.PanelTopBar
import com.hermex.android.core.designsystem.SectionCard
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hermex.android.core.data.ModelOption
import com.hermex.android.core.designsystem.EditorTitle
import com.hermex.android.core.designsystem.EditorTopBar
import com.hermex.android.core.designsystem.HermexTheme
import com.hermex.android.core.model.CronDraft

@Composable
internal fun TaskEditorRoute(viewModel: TaskEditorViewModel, onClose: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) { if (state.saved) onClose() }
    TaskEditorScreen(
        state = state,
        onCancel = onClose,
        onSave = viewModel::save,
        onEdit = viewModel::edit,
        onPickModel = viewModel::pickModel,
        onToggleSkill = viewModel::toggleSkill,
        onClearSkills = { viewModel.edit { it.copy(skills = emptyList()) } },
        onAcknowledgeWarning = viewModel::acknowledgeWarning,
        onRetry = viewModel::load,
    )
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun TaskEditorScreen(
    state: EditorScreenState,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    onEdit: ((CronDraft) -> CronDraft) -> Unit,
    onPickModel: (ModelOption?) -> Unit,
    onToggleSkill: (String) -> Unit,
    onClearSkills: () -> Unit,
    onAcknowledgeWarning: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HermexTheme.colors
    var modelPicker by remember { mutableStateOf(false) }
    var skillsPicker by remember { mutableStateOf(false) }
    val draft = state.draft
    PanelScaffold(modifier) {
        EditorTopBar(
            confirmLabel = if (state.isNew) "Create" else "Save",
            canConfirm = draft != null && draft.validationMessage == null,
            saving = state.saving,
            onCancel = onCancel,
            onConfirm = onSave,
            cancelTag = "editor.cancel",
            confirmTag = "editor.save",
        ) { EditorTitle(if (state.isNew) "New Task" else "Edit Task") }
        when (val content = state.content) {
            EditorUiState.Loading -> LoadingState("Loading task...")
            is EditorUiState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ContentUnavailable(Icons.Filled.Warning, "Could Not Load Task", content.message, actionLabel = "Try Again", onAction = onRetry)
            }
            is EditorUiState.Editing -> Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .windowInsetsPadding(WindowInsets.ime)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                val d = content.draft
                SectionCard(title = "Task") {
                    EditorField(d.name, { v -> onEdit { it.copy(name = v) } }, "Name", "editor.name")
                    Spacer(Modifier.size(10.dp))
                    EditorField(d.prompt, { v -> onEdit { it.copy(prompt = v) } }, "Prompt", "editor.prompt", singleLine = false)
                    Spacer(Modifier.size(10.dp))
                    EditorField(d.schedule, { v -> onEdit { it.copy(schedule = v) } }, "Schedule", "editor.schedule", plain = true)
                    Text(
                        "e.g. every 1h, 0 9 * * *, or an ISO time for a one-off",
                        color = colors.tertiaryLabel, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp, start = 4.dp),
                    )
                }
                SectionCard(title = "Delivery") {
                    val targets = state.targets
                    if (targets == null) {
                        EditorField(d.deliver, { v -> onEdit { it.copy(deliver = v) } }, "Deliver", "editor.deliver", plain = true)
                    } else {
                        val options = buildList {
                            addAll(targets.map { it.id to it.name })
                            if (d.deliver.isNotBlank() && targets.none { it.id == d.deliver }) add(d.deliver to "${d.deliver} (custom)")
                        }
                        options.forEachIndexed { index, (id, name) ->
                            if (index > 0) HorizontalDivider(color = colors.separator.copy(alpha = 0.5f))
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable { onEdit { it.copy(deliver = id) } }.testTag("editor.deliver.$id"),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(name, color = colors.label, fontSize = 16.sp, modifier = Modifier.weight(1f))
                                if (d.deliver == id) Icon(Icons.Filled.Check, contentDescription = "Selected", tint = colors.accent, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
                Column {
                    SectionCard(title = "Configuration") {
                        PickerRow(
                            label = "Model",
                            value = d.model ?: "Server default",
                            tag = "editor.model",
                            onClick = { modelPicker = true },
                        )
                        HorizontalDivider(color = colors.separator.copy(alpha = 0.5f))
                        PickerRow(
                            label = "Skills",
                            value = when {
                                state.skillsError != null && state.skills == null -> "Couldn't load"
                                d.skills.isEmpty() -> "None"
                                d.skills.size <= 2 -> d.skills.joinToString(", ")
                                else -> "${d.skills.size} selected"
                            },
                            tag = "editor.skills",
                            onClick = { skillsPicker = true },
                        )
                    }
                    Text(
                        "Leaving the model on Server default lets the server use the selected profile's model.",
                        color = colors.secondaryLabel, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp, top = 6.dp),
                    )
                }
                val message = state.error ?: d.validationMessage
                if (message != null) {
                    Text(
                        message,
                        color = if (state.error != null) colors.danger else colors.secondaryLabel,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 4.dp).testTag("editor.message"),
                    )
                }
                Spacer(Modifier.size(24.dp))
            }
        }
    }
    if (modelPicker && draft != null) {
        ModelPickerDialog(state, draft, onDismiss = { modelPicker = false }, onPick = {
            onPickModel(it)
            modelPicker = false
        })
    }
    if (skillsPicker && draft != null) {
        SkillsPickerDialog(state, draft, onToggle = onToggleSkill, onClear = onClearSkills, onDismiss = { skillsPicker = false })
    }
    state.warning?.let { warning ->
        AlertDialog(
            onDismissRequest = onAcknowledgeWarning,
            title = { Text("Saved") },
            text = { Text(warning) },
            confirmButton = {
                TextButton(onClick = onAcknowledgeWarning, modifier = Modifier.semantics { testTagsAsResourceId = true }.testTag("editor.warning.ok")) { Text("OK") }
            },
        )
    }
}

@Composable
private fun EditorField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    tag: String,
    singleLine: Boolean = true,
    plain: Boolean = false,
) {
    val colors = HermexTheme.colors
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
        maxLines = if (singleLine) 1 else 8,
        keyboardOptions = KeyboardOptions(
            capitalization = if (plain) KeyboardCapitalization.None else KeyboardCapitalization.Sentences,
            autoCorrectEnabled = !plain,
        ),
        shape = RoundedCornerShape(10.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = colors.label,
            unfocusedTextColor = colors.label,
            focusedBorderColor = colors.accent,
            unfocusedBorderColor = colors.separator,
            focusedLabelColor = colors.accent,
            unfocusedLabelColor = colors.secondaryLabel,
            cursorColor = colors.accent,
            focusedContainerColor = colors.tertiaryBackground,
            unfocusedContainerColor = colors.tertiaryBackground,
        ),
        modifier = Modifier.fillMaxWidth().testTag(tag),
    )
}

@Composable
private fun PickerRow(label: String, value: String, tag: String, onClick: () -> Unit) {
    val colors = HermexTheme.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onClick).testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = colors.label, fontSize = 16.sp)
        Text(value, color = colors.secondaryLabel, fontSize = 15.sp, maxLines = 1, textAlign = TextAlign.End, modifier = Modifier.weight(1f).padding(start = 12.dp))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = colors.tertiaryLabel, modifier = Modifier.size(20.dp))
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ModelPickerDialog(state: EditorScreenState, draft: CronDraft, onDismiss: () -> Unit, onPick: (ModelOption?) -> Unit) {
    val colors = HermexTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Model") },
        text = {
            val catalog = state.models
            LazyColumn(Modifier.semantics { testTagsAsResourceId = true }.heightIn(max = 420.dp)) {
                item(key = "default") {
                    ChoiceRow("Server default", draft.model == null, "editor.model.default") { onPick(null) }
                }
                when {
                    catalog == null && state.modelsError != null -> item(key = "error") {
                        Text(state.modelsError, color = colors.secondaryLabel, fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp))
                    }
                    catalog == null -> item(key = "loading") {
                        CircularProgressIndicator(Modifier.padding(12.dp).size(18.dp), strokeWidth = 2.dp)
                    }
                    else -> catalog.groups.forEach { (provider, models) ->
                        item(key = "h-$provider") {
                            Text(provider.uppercase(), color = colors.secondaryLabel, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                        }
                        items(models, key = { "${it.provider}/${it.id}" }) { option ->
                            ChoiceRow(option.id, draft.model == option.id && (draft.provider == null || draft.provider == option.provider), "editor.model.${option.provider}.${option.id}") { onPick(option) }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun SkillsPickerDialog(
    state: EditorScreenState,
    draft: CronDraft,
    onToggle: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = HermexTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Skills") },
        text = {
            val skills = state.skills
            LazyColumn(Modifier.semantics { testTagsAsResourceId = true }.heightIn(max = 420.dp)) {
                when {
                    skills == null && state.skillsError != null -> item(key = "error") {
                        Text(state.skillsError, color = colors.secondaryLabel, fontSize = 13.sp)
                    }
                    skills == null -> item(key = "loading") {
                        CircularProgressIndicator(Modifier.padding(12.dp).size(18.dp), strokeWidth = 2.dp)
                    }
                    else -> items(skills, key = { it.name }) { skill ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onToggle(skill.name) }.testTag("editor.skill.${skill.name}"),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = skill.name in draft.skills, onCheckedChange = { onToggle(skill.name) })
                            Text(skill.name, color = colors.label, fontSize = 15.sp)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss, modifier = Modifier.semantics { testTagsAsResourceId = true }.testTag("editor.skills.done")) { Text("Done") } },
        dismissButton = { TextButton(onClick = onClear) { Text("Clear") } },
    )
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, tag: String, onClick: () -> Unit) {
    val colors = HermexTheme.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).testTag(tag).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = colors.label, fontSize = 15.sp, modifier = Modifier.weight(1f))
        if (selected) Icon(Icons.Filled.Check, contentDescription = "Selected", tint = colors.accent, modifier = Modifier.size(18.dp))
    }
}

@Composable
internal fun RunOutputRoute(viewModel: RunOutputViewModel, title: String, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    RunOutputScreen(state, title, onBack, viewModel::load)
}

@Composable
internal fun RunOutputScreen(state: RunOutputUiState, title: String, onBack: () -> Unit, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val colors = HermexTheme.colors
    PanelScaffold(modifier) {
        PanelTopBar(title = title, onBack = onBack, backTag = "run.back")
        when (state) {
            RunOutputUiState.Loading -> LoadingState("Loading output...")
            RunOutputUiState.Empty -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ContentUnavailable(Icons.Filled.Warning, "Empty Output", "No output for this run.")
            }
            is RunOutputUiState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ContentUnavailable(Icons.Filled.Warning, "Could Not Load Output", state.message, actionLabel = "Try Again", onAction = onRetry)
            }
            is RunOutputUiState.Success -> Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).windowInsetsPadding(WindowInsets.navigationBars).padding(16.dp),
            ) {
                androidx.compose.foundation.text.selection.SelectionContainer {
                    Text(
                        state.text,
                        color = colors.label,
                        fontSize = 13.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(colors.secondaryBackground)
                            .padding(14.dp)
                            .testTag("run.output"),
                    )
                }
            }
        }
    }
}
