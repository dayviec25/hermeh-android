package com.hermeh.android.feature.kanban

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hermeh.android.core.designsystem.EditorTitle
import com.hermeh.android.core.designsystem.EditorTopBar
import com.hermeh.android.core.designsystem.HermehTheme
import com.hermeh.android.core.model.CardDraft
import com.hermeh.android.core.model.KanbanStatus
import com.hermeh.android.core.model.WorkspaceKind

@Composable
fun CardEditorDialog(
    editor: EditorUi,
    profiles: List<String>,
    onSave: (CardDraft) -> Unit,
    onDismiss: () -> Unit,
) {
    val original = (editor.mode as? EditorMode.Edit)?.card
    val creating = original == null
    var title by rememberSaveable { mutableStateOf(original?.title.orEmpty()) }
    var body by rememberSaveable { mutableStateOf(original?.body.orEmpty()) }
    var status by rememberSaveable { mutableStateOf(original?.status ?: KanbanStatus.Triage) }
    var priority by rememberSaveable { mutableStateOf((original?.priority ?: 0).toString()) }
    var assignee by rememberSaveable { mutableStateOf(original?.assignee) }
    var workspace by rememberSaveable { mutableStateOf(original?.workspaceKind ?: WorkspaceKind.Scratch) }
    var skills by rememberSaveable { mutableStateOf(original?.skills.orEmpty().joinToString(", ")) }
    var runtime by rememberSaveable { mutableStateOf(original?.maxRuntimeSeconds?.toString().orEmpty()) }
    val priorityValue = priority.trim().toIntOrNull()
    val priorityValid = priorityValue != null && priorityValue in -100..100
    val runtimeValue = runtime.trim().takeIf { it.isNotEmpty() }?.toLongOrNull()
    val runtimeValid = runtime.isBlank() || (runtimeValue != null && runtimeValue > 0)
    val canSave = !editor.saving && title.isNotBlank() && priorityValid && runtimeValid

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val colors = HermehTheme.colors
        Column(
            Modifier
                .fillMaxSize()
                .background(colors.background)
                .semantics { testTagsAsResourceId = true }
                .windowInsetsPadding(WindowInsets.systemBars)
                .imePadding(),
        ) {
            EditorTopBar(
                confirmLabel = if (creating) "Create" else "Save",
                canConfirm = canSave,
                saving = editor.saving,
                onCancel = onDismiss,
                onConfirm = {
                    onSave(
                        CardDraft(
                            title = title,
                            body = body,
                            status = status,
                            priority = priorityValue ?: 0,
                            assignee = assignee,
                            workspaceKind = workspace,
                            skills = skills.split(',').map { it.trim() }.filter { it.isNotEmpty() },
                            maxRuntimeSeconds = runtimeValue,
                        ),
                    )
                },
                cancelTag = "editor.cancel",
                confirmTag = "editor.save",
            ) { EditorTitle(if (creating) "New Card" else "Edit Card") }
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
            ) {
                if (editor.error != null) {
                    Text(
                        editor.error,
                        color = colors.danger,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp).testTag("editor.error"),
                    )
                }
                SectionCard("Card") {
                    Field(title, { title = it }, "Title", Modifier.testTag("editor.title"))
                    Field(body, { body = it }, "Description", Modifier.testTag("editor.description"), singleLine = false)
                }
                SectionCard("Status") {
                    val options = if (creating) KanbanStatus.movable else (listOfNotNull(original.status) + KanbanStatus.movable).distinct()
                    Segmented(options.map { it to it.title }, status, { status = it }, tagPrefix = "editor.status")
                    if (!creating) {
                        Hint("Current Status: ${original.status?.title ?: "Unknown Status"}. Choose a permitted Status only if you want to move the Card.")
                    }
                }
                SectionCard("Assignment") {
                    Field(
                        priority,
                        { priority = it.filter { ch -> ch.isDigit() || ch == '-' }.take(4) },
                        "Priority",
                        Modifier.testTag("editor.priority"),
                        keyboard = KeyboardType.Number,
                        isError = !priorityValid,
                    )
                    Hint("A whole number from -100 through 100.")
                    ProfilePicker(assignee, profiles) { assignee = it }
                }
                if (creating) {
                    SectionCard("Execution") {
                        Text("Workspace", color = colors.label, fontSize = 15.sp)
                        Segmented(WorkspaceKind.entries.map { it to it.title }, workspace, { workspace = it }, tagPrefix = "editor.workspace")
                        Field(skills, { skills = it }, "Skills", Modifier.testTag("editor.skills"))
                        Hint("Separate skill names with commas.")
                        Field(
                            runtime,
                            { runtime = it.filter(Char::isDigit).take(9) },
                            "Maximum Runtime",
                            Modifier.testTag("editor.runtime"),
                            keyboard = KeyboardType.Number,
                            isError = !runtimeValid,
                        )
                        Hint("Optional number of seconds.")
                    }
                } else {
                    Hint(
                        "Workspace, Skills, Maximum Runtime, and Prerequisite are set when the Card is created and cannot be edited here.",
                        Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Field(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    keyboard: KeyboardType = KeyboardType.Text,
    isError: Boolean = false,
) {
    val colors = HermehTheme.colors
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 4,
        isError = isError,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        shape = RoundedCornerShape(10.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = colors.accent,
            unfocusedBorderColor = colors.separator,
            focusedLabelColor = colors.accent,
            unfocusedLabelColor = colors.secondaryLabel,
            cursorColor = colors.accent,
            focusedTextColor = colors.label,
            unfocusedTextColor = colors.label,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun Hint(text: String, modifier: Modifier = Modifier) {
    Text(text, color = HermehTheme.colors.secondaryLabel, fontSize = 13.sp, modifier = modifier)
}

@Composable
private fun <T> Segmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, tagPrefix: String) {
    val colors = HermehTheme.colors
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(colors.label.copy(alpha = 0.06f)).padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEach { (value, label) ->
            val active = value == selected
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (active) colors.tertiaryBackground else colors.label.copy(alpha = 0f))
                    .border(1.dp, if (active) colors.glassStroke else colors.label.copy(alpha = 0f), RoundedCornerShape(8.dp))
                    .clickable { onSelect(value) }
                    .testTag("$tagPrefix.${label.lowercase().replace(" ", "")}"),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = colors.label, fontSize = 14.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
    }
}

@Composable
private fun ProfilePicker(selected: String?, profiles: List<String>, onSelect: (String?) -> Unit) {
    val colors = HermehTheme.colors
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(10.dp))
                .clickable { open = true }
                .testTag("editor.profile"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Profile", color = colors.label, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Text(selected ?: "Unassigned", color = colors.secondaryLabel, fontSize = 15.sp)
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = colors.secondaryLabel)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.semantics { testTagsAsResourceId = true }) {
            DropdownMenuItem(text = { Text("Unassigned") }, onClick = { open = false; onSelect(null) }, modifier = Modifier.testTag("editor.profile.none"))
            (profiles + listOfNotNull(selected)).distinct().forEach { name ->
                DropdownMenuItem(text = { Text(name) }, onClick = { open = false; onSelect(name) }, modifier = Modifier.testTag("editor.profile.$name"))
            }
        }
    }
}
