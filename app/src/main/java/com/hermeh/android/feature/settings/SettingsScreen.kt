package com.hermeh.android.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hermeh.android.BuildConfig
import com.hermeh.android.core.data.ThemeMode
import com.hermeh.android.core.designsystem.CompanionSheet
import com.hermeh.android.core.designsystem.GlassCircleButton
import com.hermeh.android.core.designsystem.HermehTheme
import com.hermeh.android.core.designsystem.HermehType
import com.hermeh.android.core.designsystem.Mascot
import com.hermeh.android.core.model.AuthState
import com.hermeh.android.core.model.Companion
import com.hermeh.android.core.model.MascotCatalog
import com.hermeh.android.core.model.MascotColor
import com.hermeh.android.core.model.MascotId
import com.hermeh.android.core.model.MascotMood

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SettingsScreen(
    auth: AuthState.SignedIn,
    theme: ThemeMode,
    companion: Companion,
    onBack: () -> Unit,
    onTheme: (ThemeMode) -> Unit,
    onCompanionName: (String) -> Unit,
    onCompanionColor: (MascotColor) -> Unit,
    onCompanionMascot: (MascotId) -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HermehTheme.colors
    var confirm by remember { mutableStateOf(false) }
    var companionSheet by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier
            .fillMaxSize()
            .background(if (colors.isDark) colors.background else colors.secondaryBackground)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            GlassCircleButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack, Modifier.testTag("settings.back"))
            Text("Settings", style = HermehType.screenTitle, color = colors.label, modifier = Modifier.padding(start = 14.dp))
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            Section("Server") {
                InfoRow("Name", auth.server.name)
                Divider()
                InfoRow("Address", auth.server.baseUrl)
                Divider()
                InfoRow("Signed in as", auth.user.displayName.ifBlank { auth.server.username })
                Divider()
                InfoRow("Hermes version", auth.hostVersion.ifBlank { "Unknown" })
            }
            Section("Appearance") {
                Row(Modifier.fillMaxWidth().padding(4.dp).clip(RoundedCornerShape(10.dp)).background(if (colors.isDark) colors.background else colors.secondaryBackground).padding(3.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        val selected = theme == mode
                        Box(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) colors.tertiaryBackground else Color.Transparent)
                                .clickable { onTheme(mode) }
                                .heightIn(min = 40.dp)
                                .testTag("settings.theme.${mode.name.lowercase()}"),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(mode.name, color = colors.label, fontSize = 14.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                        }
                    }
                }
                Divider()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { companionSheet = true }
                        .heightIn(min = 64.dp)
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .testTag("settings.companion"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Mascot(MascotCatalog.find(companion.mascot), colors.mascot, MascotMood.Idle, 40.dp, name = companion.name, animated = false)
                    Column(Modifier.weight(1f)) {
                        Text("Companion", color = colors.label, fontSize = 16.sp)
                        Text("${companion.name} · ${companion.color.label}", color = colors.secondaryLabel, fontSize = 13.sp)
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = colors.tertiaryLabel)
                }
            }
            Section("About") {
                Text("Hermeh", style = HermehType.wordmark, color = colors.label, modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp))
                Divider()
                InfoRow("Version", BuildConfig.VERSION_NAME)
            }
            Section(null) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clickable { confirm = true }
                        .heightIn(min = 48.dp)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .testTag("settings.signOut"),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text("Sign Out", color = colors.danger, fontSize = 16.sp)
                }
            }
            Spacer(Modifier.size(24.dp))
        }
    }
    if (companionSheet) {
        CompanionSheet(
            companion = companion,
            mood = MascotMood.Idle,
            focusName = false,
            onSaveName = onCompanionName,
            onColor = onCompanionColor,
            onMascot = onCompanionMascot,
            onDismiss = { companionSheet = false },
        )
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Sign out?") },
            text = { Text("Your saved sign-in for ${auth.server.name} is removed from this phone. Conversations on the host stay.") },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    onSignOut()
                }, modifier = Modifier.semantics { testTagsAsResourceId = true }.testTag("settings.signOut.confirm")) { Text("Sign Out", color = colors.danger) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Section(title: String?, content: @Composable ColumnScope.() -> Unit) {
    val colors = HermehTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (title != null) {
            Text(title.uppercase(), color = colors.secondaryLabel, fontSize = 12.sp, modifier = Modifier.padding(start = 16.dp))
        }
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(if (colors.isDark) colors.secondaryBackground else colors.tertiaryBackground),
            content = content,
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    val colors = HermehTheme.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = colors.label, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Text(value, color = colors.secondaryLabel, fontSize = 15.sp, maxLines = 2)
    }
}

@Composable
private fun Divider() {
    HorizontalDivider(Modifier.padding(start = 16.dp), color = HermehTheme.colors.separator.copy(alpha = 0.6f), thickness = 0.5.dp)
}
