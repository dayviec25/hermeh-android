package com.hermex.android.feature.skills

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hermex.android.R
import com.hermex.android.core.designsystem.HermexTheme
import com.hermex.android.core.model.Skill
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography

@Composable
internal fun SkillsListRoute(viewModel: SkillsViewModel, onBack: () -> Unit, onOpen: (Skill) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SkillsListScreen(
        state = state,
        onBack = onBack,
        onRefresh = viewModel::load,
        onSearch = viewModel::search,
        onOpen = onOpen,
        onToggle = viewModel::setEnabled,
        onDismissError = viewModel::dismissToggleError,
    )
}

@Composable
internal fun SkillsListScreen(
    state: SkillsScreenState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSearch: (String) -> Unit,
    onOpen: (Skill) -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HermexTheme.colors
    PanelScaffold(modifier) {
        PanelTopBar(title = "Skills", onBack = onBack, backTag = "skills.back") {
            BarIcon(Icons.Filled.Refresh, "Refresh", "skills.refresh", busy = state.refreshing && state.content !is SkillsUiState.Loading, onClick = onRefresh)
        }
        SearchPill(state.query, onSearch, Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
        when (val content = state.content) {
            SkillsUiState.Loading -> LoadingState("Loading skills...")
            is SkillsUiState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ContentUnavailable(Icons.Filled.Warning, "Could Not Load Skills", content.message, actionLabel = "Try Again", actionTag = "skills.retry", onAction = onRefresh)
            }
            SkillsUiState.Empty -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ContentUnavailable(Icons.Filled.Search, "No Skills", "Skills from the Hermes server will appear here.")
            }
            is SkillsUiState.Success -> {
                val groups = state.groups
                if (groups.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        ContentUnavailable(Icons.Filled.Search, "No Results", "No skills match \"${state.query}\".", Modifier.testTag("skills.noResults"))
                    }
                } else {
                    LazyColumn(
                        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars),
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 32.dp),
                    ) {
                        groups.forEach { group ->
                            item(key = "h-${group.category}", contentType = "header") {
                                Text(
                                    group.category.uppercase(),
                                    color = colors.secondaryLabel, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(start = 4.dp, top = 14.dp, bottom = 4.dp),
                                )
                            }
                            itemsIndexed(group.skills, key = { _, skill -> skill.name }, contentType = { _, _ -> "skill" }) { index, skill ->
                                SkillRow(skill, toggling = skill.name in state.toggling, onOpen = { onOpen(skill) }, onToggle = { onToggle(skill.name, it) })
                                if (index < group.skills.lastIndex) HorizontalDivider(Modifier.padding(start = 52.dp), color = colors.separator.copy(alpha = 0.6f))
                            }
                        }
                    }
                }
            }
        }
    }
    state.toggleError?.let { message ->
        AlertDialog(
            onDismissRequest = onDismissError,
            title = { Text("Could Not Update Skill") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = onDismissError) { Text("OK") } },
        )
    }
}

@Composable
private fun SearchPill(query: String, onQuery: (String) -> Unit, modifier: Modifier = Modifier) {
    val colors = HermexTheme.colors
    Row(
        modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(50)).background(colors.secondaryBackground).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Search, null, tint = colors.secondaryLabel, modifier = Modifier.size(20.dp))
        Box(Modifier.weight(1f).padding(start = 10.dp)) {
            if (query.isEmpty()) Text("Search skills...", color = colors.secondaryLabel, fontSize = 17.sp)
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = TextStyle(color = colors.label, fontSize = 17.sp),
                cursorBrush = SolidColor(colors.accent),
                modifier = Modifier.fillMaxWidth().testTag("skills.search"),
            )
        }
        if (query.isNotEmpty()) {
            Icon(
                Icons.Filled.Close, contentDescription = "Clear search", tint = colors.secondaryLabel,
                modifier = Modifier.size(32.dp).clip(CircleShape).clickable { onQuery("") }.padding(6.dp).testTag("skills.search.clear"),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
private fun SkillRow(skill: Skill, toggling: Boolean, onOpen: () -> Unit, onToggle: (Boolean) -> Unit) {
    val colors = HermexTheme.colors
    var menu by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onOpen, onLongClick = { menu = true })
                .alpha(if (skill.enabled) 1f else 0.55f)
                .padding(vertical = 10.dp)
                .testTag("skills.row.${skill.name}"),
            verticalAlignment = Alignment.Top,
        ) {
            HammerBadge()
            Column(Modifier.weight(1f).padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(skill.name, color = colors.label, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                skill.description?.takeIf { it.isNotBlank() }?.let {
                    Text(it, color = colors.secondaryLabel, fontSize = 15.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                if (!skill.enabled) DisabledPill(Modifier.padding(top = 2.dp))
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = colors.tertiaryLabel,
                modifier = Modifier.padding(start = 8.dp, top = 10.dp).size(20.dp),
            )
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text(if (skill.enabled) "Disable" else "Enable") },
                enabled = !toggling,
                onClick = {
                    menu = false
                    onToggle(!skill.enabled)
                },
                modifier = Modifier.semantics { testTagsAsResourceId = true }.testTag("skills.menu.toggle"),
            )
        }
    }
}

@Composable
private fun HammerBadge() {
    val colors = HermexTheme.colors
    Box(
        Modifier.size(36.dp).clip(CircleShape).background(if (colors.isDark) colors.secondaryBackground else Color(0xFFEEEEF0)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(R.drawable.ic_lucide_hammer), contentDescription = null, tint = colors.label, modifier = Modifier.size(19.dp))
    }
}

@Composable
private fun DisabledPill(modifier: Modifier = Modifier) {
    val colors = HermexTheme.colors
    Text(
        "Disabled",
        color = colors.secondaryLabel,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier.clip(RoundedCornerShape(50)).background(colors.label.copy(alpha = 0.10f)).padding(horizontal = 7.dp, vertical = 3.dp),
    )
}

@Composable
internal fun SkillDetailRoute(
    viewModel: SkillDetailViewModel,
    listViewModel: SkillsViewModel,
    name: String,
    onBack: () -> Unit,
    onOpenFile: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val list by listViewModel.state.collectAsStateWithLifecycle()
    val skill = (list.content as? SkillsUiState.Success)?.skills?.firstOrNull { it.name == name }
    SkillDetailScreen(
        title = name,
        state = state,
        enabled = skill?.enabled,
        toggling = name in list.toggling,
        toggleError = list.toggleError,
        onBack = onBack,
        onRefresh = viewModel::load,
        onToggle = { listViewModel.setEnabled(name, it) },
        onDismissError = listViewModel::dismissToggleError,
        onOpenFile = onOpenFile,
    )
}

@Composable
internal fun SkillDetailScreen(
    title: String,
    state: SkillDetailUiState,
    enabled: Boolean?,
    toggling: Boolean,
    toggleError: String?,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDismissError: () -> Unit,
    onOpenFile: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HermexTheme.colors
    PanelScaffold(modifier) {
        PanelTopBar(title = title, onBack = onBack, backTag = "skill.back") {
            BarIcon(Icons.Filled.Refresh, "Refresh", "skill.refresh", onClick = onRefresh)
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).windowInsetsPadding(WindowInsets.navigationBars).padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (enabled != null) {
                Row(
                    Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(CardShape).background(colors.secondaryBackground)
                        .clickable(enabled = !toggling) { onToggle(!enabled) }.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Enabled", color = colors.label, fontSize = 16.sp)
                        Text(
                            if (enabled) "The agent can load this skill." else "Disabled",
                            color = colors.secondaryLabel, fontSize = 13.sp, modifier = Modifier.testTag("skill.state"),
                        )
                    }
                    Switch(
                        checked = enabled,
                        onCheckedChange = onToggle,
                        enabled = !toggling,
                        colors = SwitchDefaults.colors(checkedTrackColor = colors.success, checkedThumbColor = Color.White),
                        modifier = Modifier.testTag("skill.toggle"),
                    )
                }
            }
            when (state) {
                SkillDetailUiState.Loading -> LoadingState("Loading skill...", Modifier.heightIn(min = 300.dp))
                SkillDetailUiState.Empty -> ContentUnavailable(Icons.Filled.Description, "No Content", "This skill has no content.")
                is SkillDetailUiState.Error -> ContentUnavailable(Icons.Filled.Warning, "Could Not Load Skill", state.message, actionLabel = "Try Again", onAction = onRefresh)
                is SkillDetailUiState.Success -> {
                    if (state.detail.content.isNotBlank()) {
                        SkillMarkdown(displayMarkdown(state.detail.content), Modifier.padding(horizontal = 20.dp).testTag("skill.content"))
                    }
                    if (state.detail.linkedFiles.isNotEmpty()) {
                        Column(Modifier.padding(horizontal = 20.dp)) {
                            Text("Linked Files", color = colors.secondaryLabel, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 6.dp))
                            state.detail.linkedFiles.forEachIndexed { index, file ->
                                Row(
                                    Modifier.fillMaxWidth().clickable { onOpenFile(file) }.padding(vertical = 9.dp).testTag("skill.file.$file"),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(Modifier.size(34.dp).clip(CircleShape).background(colors.label.copy(alpha = 0.08f)), contentAlignment = Alignment.Center) {
                                        Icon(Icons.Filled.Description, null, tint = colors.label, modifier = Modifier.size(18.dp))
                                    }
                                    Text(file, color = colors.label, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 12.dp))
                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = colors.tertiaryLabel, modifier = Modifier.size(20.dp))
                                }
                                if (index < state.detail.linkedFiles.lastIndex) HorizontalDivider(Modifier.padding(start = 46.dp), color = colors.separator.copy(alpha = 0.6f))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.size(24.dp))
        }
    }
    toggleError?.let { message ->
        AlertDialog(
            onDismissRequest = onDismissError,
            title = { Text("Could Not Update Skill") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = onDismissError) { Text("OK") } },
        )
    }
}

internal fun displayMarkdown(content: String): String {
    val text = content.replace("\r\n", "\n")
    if (!text.startsWith("---\n")) return text
    val end = text.indexOf("\n---", startIndex = 4)
    if (end < 0) return text
    val front = text.substring(4, end)
    val rest = text.substring(end + 4).trimStart('\n')
    return "```yaml\n$front\n```\n\n$rest"
}

@Composable
internal fun SkillMarkdown(content: String, modifier: Modifier = Modifier) {
    val colors = HermexTheme.colors
    val body = TextStyle(color = colors.label, fontSize = 16.sp, lineHeight = 23.sp)
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
            code = body.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 18.sp),
            inlineCode = body.copy(fontFamily = FontFamily.Monospace, fontSize = 14.sp),
            quote = body.copy(color = colors.secondaryLabel),
            table = body.copy(fontSize = 15.sp),
        ),
    )
}

@Composable
internal fun SkillFileRoute(viewModel: SkillFileViewModel, title: String, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SkillFileScreen(state, title, onBack, viewModel::load)
}

@Composable
internal fun SkillFileScreen(state: SkillFileUiState, title: String, onBack: () -> Unit, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val colors = HermexTheme.colors
    PanelScaffold(modifier) {
        PanelTopBar(title = title.substringAfterLast('/'), onBack = onBack, backTag = "skillFile.back")
        when (state) {
            SkillFileUiState.Loading -> LoadingState("Loading file...")
            SkillFileUiState.Binary -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ContentUnavailable(Icons.Filled.Description, "No Preview", "Preview is not available for this file type.")
            }
            SkillFileUiState.Empty -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ContentUnavailable(Icons.Filled.Description, "No Content", "This file appears to be empty.")
            }
            is SkillFileUiState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ContentUnavailable(Icons.Filled.Warning, "Could Not Load File", state.message, actionLabel = "Try Again", onAction = onRetry)
            }
            is SkillFileUiState.Success -> Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).windowInsetsPadding(WindowInsets.navigationBars).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.file.truncated) Text("Preview truncated", color = colors.secondaryLabel, fontSize = 12.sp)
                val text = state.file.text.orEmpty()
                if (title.endsWith(".md", ignoreCase = true)) {
                    SkillMarkdown(displayMarkdown(text), Modifier.testTag("skillFile.text"))
                } else {
                    SelectionContainer {
                        Text(
                            text,
                            color = colors.label, fontSize = 13.sp, fontFamily = FontFamily.Monospace,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.secondaryBackground).padding(14.dp).testTag("skillFile.text"),
                        )
                    }
                }
            }
        }
    }
}
