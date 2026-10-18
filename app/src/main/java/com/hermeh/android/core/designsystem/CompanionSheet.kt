package com.hermeh.android.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hermeh.android.core.model.COMPANION_NAME_MAX
import com.hermeh.android.core.model.Companion
import com.hermeh.android.core.model.MascotCatalog
import com.hermeh.android.core.model.MascotColor
import com.hermeh.android.core.model.MascotId
import com.hermeh.android.core.model.MascotMood
import com.hermeh.android.core.model.companionName

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun CompanionSheet(
    companion: Companion,
    mood: MascotMood,
    focusName: Boolean,
    onSaveName: (String) -> Unit,
    onColor: (MascotColor) -> Unit,
    onMascot: (MascotId) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = HermehTheme.colors
    val mascot = MascotCatalog.find(companion.mascot)
    val tint = Color(companion.color.argb)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.secondaryBackground,
        shape = HermehShapes.sheet,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .semantics { testTagsAsResourceId = true }
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp)
                .testTag("companion.sheet"),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Mascot(mascot, tint, mood, 96.dp, name = companion.name)
                Text(companion.name, style = HermehType.thought, color = colors.label, textAlign = TextAlign.Center)
            }
            SheetLabel("Name")
            NameField(companion, focusName, onSaveName)
            SheetLabel("Color")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MascotColor.entries.forEach { option ->
                    val selected = option == companion.color
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color(option.argb))
                            .border(if (selected) 2.5.dp else 1.dp, if (selected) colors.label else colors.separator, CircleShape)
                            .clickable(role = Role.RadioButton, onClickLabel = option.label) { onColor(option) }
                            .testTag("companion.color.${option.name.lowercase()}"),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) Icon(Icons.Filled.Check, contentDescription = "${option.label}, selected", tint = OnMascot, modifier = Modifier.size(20.dp))
                    }
                }
            }
            SheetLabel("Mascot")
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MascotCatalog.all.forEach { option ->
                    val selected = option.id == companion.mascot
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .heightIn(min = 168.dp)
                            .clip(HermehShapes.tile)
                            .background(colors.tertiaryBackground)
                            .border(if (selected) 2.dp else 1.dp, if (selected) colors.accent else colors.separator, HermehShapes.tile)
                            .clickable(role = Role.RadioButton) { onMascot(option.id) }
                            .padding(12.dp)
                            .testTag("companion.mascot.${option.id.value}"),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopEnd) {
                            Mascot(option, tint, MascotMood.Idle, 72.dp, Modifier.align(Alignment.Center), animated = false)
                            if (selected) Icon(Icons.Filled.CheckCircle, contentDescription = "Selected", tint = colors.accent, modifier = Modifier.size(20.dp))
                        }
                        Text(option.title, color = colors.label, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                }
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .heightIn(min = 168.dp)
                        .clip(HermehShapes.tile)
                        .border(1.dp, colors.separator, HermehShapes.tile)
                        .semantics(mergeDescendants = true) { disabled() }
                        .alpha(0.6f)
                        .padding(12.dp)
                        .testTag("companion.mascot.create"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(Modifier.size(72.dp).clip(CircleShape).background(colors.accent.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = colors.accent, modifier = Modifier.size(30.dp))
                    }
                    Text("Create with AI", color = colors.label, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Text(
                        "Design your own mascot. Uses tokens. Coming soon.",
                        color = colors.secondaryLabel,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun SheetLabel(text: String) {
    Text(
        text.uppercase(),
        color = HermehTheme.colors.secondaryLabel,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 6.dp, start = 4.dp),
    )
}

@Composable
private fun NameField(companion: Companion, focusName: Boolean, onSaveName: (String) -> Unit) {
    val colors = HermehTheme.colors
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    var text by rememberSaveable(companion.name) { mutableStateOf(companion.name) }
    val valid = companionName(text)
    val canSave = valid != null && (valid != companion.name || !companion.named)
    val save = {
        if (canSave) {
            focusManager.clearFocus()
            onSaveName(valid)
        }
    }
    if (focusName) LaunchedEffect(Unit) { focus.requestFocus() }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        BasicTextField(
            value = text,
            onValueChange = { text = it.take(COMPANION_NAME_MAX) },
            singleLine = true,
            textStyle = TextStyle(color = colors.label, fontSize = 16.sp),
            cursorBrush = SolidColor(colors.accent),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { save() }),
            modifier = Modifier
                .weight(1f)
                .clip(HermehShapes.capsule)
                .background(colors.tertiaryBackground)
                .border(1.dp, colors.separator, HermehShapes.capsule)
                .padding(horizontal = 18.dp, vertical = 14.dp)
                .focusRequester(focus)
                .testTag("companion.name"),
            decorationBox = { inner ->
                Box {
                    if (text.isEmpty()) Text("Name your companion", color = colors.tertiaryLabel, fontSize = 16.sp)
                    inner()
                }
            },
        )
        Box(
            Modifier
                .clip(HermehShapes.capsule)
                .background(if (canSave) colors.ink else colors.ink.copy(alpha = 0.18f))
                .clickable(enabled = canSave, role = Role.Button, onClick = save)
                .heightIn(min = 48.dp)
                .padding(horizontal = 20.dp)
                .testTag("companion.save"),
            contentAlignment = Alignment.Center,
        ) {
            Text("Save", color = if (canSave) colors.onInk else colors.secondaryLabel, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
