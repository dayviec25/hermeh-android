package com.hermex.android.feature.home

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Drafts
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hermex.android.R
import com.hermex.android.core.designsystem.HermexColors
import com.hermex.android.core.designsystem.AutomationAccessibilityRefresh
import com.hermex.android.core.designsystem.HermexTheme
import com.hermex.android.core.designsystem.HermexWordmark
import com.hermex.android.core.designsystem.glassSurface
import kotlinx.coroutines.delay

enum class HomeDestination(val slug: String, val title: String, @DrawableRes val icon: Int) {
    Bots("bots", "Bots", R.drawable.ic_lucide_bot),
    Tasks("tasks", "Tasks", R.drawable.ic_lucide_calendar_clock),
    Kanban("kanban", "Kanban", R.drawable.ic_lucide_columns3),
    Skills("skills", "Skills", R.drawable.ic_lucide_hammer),
    Memory("memory", "Memory", R.drawable.ic_lucide_brain),
    Insights("insights", "Insights", R.drawable.ic_lucide_chart_column_increasing),
}

data class HomeActions(
    val onRefresh: () -> Unit,
    val onLoadMore: () -> Unit,
    val onOpenSession: (SessionRowUi) -> Unit,
    val onNewChat: () -> Unit,
    val onDestination: (HomeDestination) -> Unit,
    val onSettings: () -> Unit,
    val onArchived: () -> Unit,
    val onOpenSearch: () -> Unit,
    val onCloseSearch: () -> Unit,
    val onQuery: (String) -> Unit,
    val onToggleProfiles: () -> Unit,
    val onSelectProfile: (String) -> Unit,
    val onShowAll: () -> Unit,
    val onToggleProjects: () -> Unit,
    val onSelectProject: (String) -> Unit,
    val onClearProject: () -> Unit,
    val onAddProject: () -> Unit,
    val onDismissProjectSheet: () -> Unit,
    val onCreateProject: (String, String) -> Unit,
    val onRename: (SessionRowUi, String) -> Unit,
    val onTogglePinned: (SessionRowUi) -> Unit,
    val onToggleUnread: (SessionRowUi) -> Unit,
    val onDuplicate: (SessionRowUi) -> Unit,
    val onArchive: (SessionRowUi) -> Unit,
    val onDelete: (SessionRowUi) -> Unit,
    val onUndoArchive: (String) -> Unit,
    val onDismissToast: (Long) -> Unit,
)

@Composable
fun HomeRoute(
    viewModel: HomeViewModel,
    initials: String,
    onOpenSession: (SessionRowUi) -> Unit,
    onNewChat: () -> Unit,
    onDestination: (HomeDestination) -> Unit,
    onSettings: () -> Unit,
    onArchived: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AutomationAccessibilityRefresh()
    val actions = remember(viewModel) {
        HomeActions(
            onRefresh = { viewModel.refresh() },
            onLoadMore = viewModel::loadMore,
            onOpenSession = { row ->
                viewModel.onOpened(row)
                onOpenSession(row)
            },
            onNewChat = onNewChat,
            onDestination = onDestination,
            onSettings = onSettings,
            onArchived = onArchived,
            onOpenSearch = viewModel::openSearch,
            onCloseSearch = viewModel::closeSearch,
            onQuery = viewModel::onQuery,
            onToggleProfiles = viewModel::toggleProfiles,
            onSelectProfile = viewModel::selectProfile,
            onShowAll = viewModel::showAll,
            onToggleProjects = viewModel::toggleProjects,
            onSelectProject = viewModel::selectProject,
            onClearProject = viewModel::clearProject,
            onAddProject = viewModel::openProjectSheet,
            onDismissProjectSheet = viewModel::dismissProjectSheet,
            onCreateProject = viewModel::createProject,
            onRename = viewModel::rename,
            onTogglePinned = viewModel::togglePinned,
            onToggleUnread = viewModel::toggleUnread,
            onDuplicate = viewModel::duplicate,
            onArchive = viewModel::archive,
            onDelete = viewModel::delete,
            onUndoArchive = viewModel::undoArchive,
            onDismissToast = viewModel::dismissToast,
        )
    }
    HomeScreen(state = state, initials = initials, actions = actions)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    initials: String,
    actions: HomeActions,
    modifier: Modifier = Modifier,
) {
    val colors = HermexTheme.colors
    var renaming by remember { mutableStateOf<SessionRowUi?>(null) }
    var deleting by remember { mutableStateOf<SessionRowUi?>(null) }
    val rowActions = SessionRowActions(
        onOpen = actions.onOpenSession,
        onRename = { renaming = it },
        onTogglePinned = actions.onTogglePinned,
        onToggleUnread = actions.onToggleUnread,
        onDuplicate = actions.onDuplicate,
        onArchive = actions.onArchive,
        onDelete = { deleting = it },
    )
    val search = state.search as? SearchUi.Open
    Box(modifier.fillMaxSize().background(colors.background)) {
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = actions.onRefresh,
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().testTag("home.list"),
                contentPadding = PaddingValues(bottom = 120.dp),
            ) {
                item(key = "header", contentType = "header") {
                    Header(initials = initials, search = search, actions = actions)
                }
                if (search != null && search.query.isNotBlank()) {
                    searchResults(search, rowActions)
                } else {
                    sidebar(state, actions)
                    sessionList(state, actions, rowActions)
                }
            }
        }
        if (search == null) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(end = 20.dp, bottom = 16.dp)
                    .glassSurface(CircleShape, elevated = true)
                    .clickable(onClick = actions.onNewChat)
                    .padding(horizontal = 22.dp, vertical = 15.dp)
                    .testTag("home.chat"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(painterResource(R.drawable.ic_edit_square), contentDescription = null, tint = colors.label, modifier = Modifier.size(22.dp))
                Text("Chat", color = colors.label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        state.toast?.let { toast ->
            Toast(toast, actions, Modifier.align(Alignment.BottomCenter))
        }
    }
    renaming?.let { row ->
        RenameSheet(row, onDismiss = { renaming = null }, onSave = { title ->
            renaming = null
            actions.onRename(row, title)
        })
    }
    deleting?.let { row ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete Session?") },
            text = { Text("“${row.title}” will be deleted from the Hermes server. This can't be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleting = null
                        actions.onDelete(row)
                    },
                    modifier = Modifier.semantics { testTagsAsResourceId = true }.testTag("session.delete.confirm"),
                ) { Text("Delete", color = colors.danger) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel", color = colors.label) } },
            containerColor = colors.secondaryBackground,
            titleContentColor = colors.label,
            textContentColor = colors.secondaryLabel,
        )
    }
    (state.projectSheet as? ProjectSheet.Editing)?.let { sheet ->
        ProjectCreationSheet(sheet, actions.onDismissProjectSheet, actions.onCreateProject)
    }
}

private data class SessionRowActions(
    val onOpen: (SessionRowUi) -> Unit,
    val onRename: (SessionRowUi) -> Unit,
    val onTogglePinned: (SessionRowUi) -> Unit,
    val onToggleUnread: (SessionRowUi) -> Unit,
    val onDuplicate: (SessionRowUi) -> Unit,
    val onArchive: (SessionRowUi) -> Unit,
    val onDelete: (SessionRowUi) -> Unit,
)

private fun LazyListScope.sidebar(state: HomeUiState, actions: HomeActions) {
    items(HomeDestination.entries, key = { "nav-${it.slug}" }, contentType = { "nav" }) { destination ->
        NavRow(destination.title, destination.icon, testTag = "home.row.${destination.slug}", onClick = { actions.onDestination(destination) })
    }
    item(key = "nav-activeProfile", contentType = "nav") {
        NavRow(
            title = "Active Profile",
            icon = R.drawable.ic_lucide_user_round_cog,
            testTag = "home.row.activeProfile",
            expanded = state.profilesExpanded,
            onClick = actions.onToggleProfiles,
        )
    }
    if (state.profilesExpanded) {
        when (val profiles = state.profiles) {
            ProfilesUi.Loading -> item(key = "profiles-loading", contentType = "subStatus") { SubStatus("Loading profiles...") }
            is ProfilesUi.Error -> item(key = "profiles-error", contentType = "subStatus") { SubStatus(profiles.message) }
            is ProfilesUi.Ready -> {
                if (profiles.options.isEmpty()) item(key = "profiles-empty", contentType = "subStatus") { SubStatus("No profiles") }
                items(profiles.options, key = { "profile-${it.name}" }, contentType = { "profile" }) { option ->
                    SubRow(
                        title = option.displayName,
                        subtitle = option.model,
                        icon = R.drawable.ic_lucide_user_round,
                        selected = option.selected,
                        testTag = "profile.row.${option.name}",
                        onClick = { actions.onSelectProfile(option.name) },
                    )
                }
                item(key = "profile-all", contentType = "profile") {
                    SubRow(
                        title = "Show all",
                        subtitle = "Sessions from every profile",
                        icon = R.drawable.ic_lucide_user_round_cog,
                        selected = profiles.showAll,
                        testTag = "profile.showAll",
                        onClick = actions.onShowAll,
                    )
                }
            }
        }
    }
    item(key = "nav-projects", contentType = "nav") {
        NavRow(
            title = "Projects",
            icon = R.drawable.ic_lucide_folder,
            testTag = "home.row.projects",
            expanded = state.projectsExpanded,
            onClick = actions.onToggleProjects,
            trailing = if (state.projectsExpanded) {
                {
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).clickable(onClick = actions.onAddProject).testTag("project.add"),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Add project", tint = HermexTheme.colors.secondaryLabel)
                    }
                }
            } else {
                null
            },
        )
    }
    if (state.projectsExpanded) {
        when (val projects = state.projects) {
            ProjectsUi.Loading -> item(key = "projects-loading", contentType = "subStatus") { SubStatus("Loading projects...") }
            ProjectsUi.Unavailable -> item(key = "projects-unavailable", contentType = "subStatus") { SubStatus("Projects aren't available on this server.") }
            is ProjectsUi.Error -> item(key = "projects-error", contentType = "subStatus") { SubStatus(projects.message) }
            is ProjectsUi.Ready -> {
                if (projects.lanes.isEmpty()) {
                    item(key = "projects-empty", contentType = "subStatus") { SubStatus("No projects", Modifier.testTag("project.empty")) }
                }
                items(projects.lanes, key = { "project-${it.id}" }, contentType = { "project" }) { lane ->
                    SubRow(
                        title = lane.label,
                        subtitle = null,
                        icon = R.drawable.ic_lucide_folder,
                        iconTint = projectColor(lane.color),
                        selected = state.selectedProject?.id == lane.id,
                        count = lane.count,
                        testTag = "project.row.${lane.id}",
                        onClick = { actions.onSelectProject(lane.id) },
                    )
                }
            }
        }
    }
}

private fun LazyListScope.sessionList(state: HomeUiState, actions: HomeActions, rowActions: SessionRowActions) {
    when (val content = state.sessions) {
        SessionsContent.Loading -> {
            item(key = "sessions-title", contentType = "title") { SectionTitle("Sessions", state, actions) }
            item(key = "loading", contentType = "status") {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = HermexTheme.colors.secondaryLabel, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                }
            }
        }
        SessionsContent.Empty -> {
            item(key = "sessions-title", contentType = "title") { SectionTitle("Sessions", state, actions) }
            item(key = "empty", contentType = "status") {
                StatusText(
                    if (state.selectedProject != null) "No sessions in this project." else "No sessions yet. Start a chat to begin.",
                    Modifier.testTag("home.empty"),
                )
            }
        }
        is SessionsContent.Error -> {
            item(key = "sessions-title", contentType = "title") { SectionTitle("Sessions", state, actions) }
            item(key = "error", contentType = "status") { StatusText(content.message, Modifier.testTag("home.error")) }
        }
        is SessionsContent.Success -> {
            if (content.pinned.isNotEmpty()) {
                item(key = "pinned-title", contentType = "title") {
                    Text(
                        "Pinned",
                        style = MaterialTheme.typography.titleLarge,
                        color = HermexTheme.colors.label,
                        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 8.dp).testTag("home.pinned"),
                    )
                }
                items(content.pinned, key = { "p-${it.key}" }, contentType = { "session" }) { row -> SessionRow(row, rowActions) }
            }
            item(key = "sessions-title", contentType = "title") { SectionTitle("Sessions", state, actions) }
            items(content.rows, key = { it.key }, contentType = { "session" }) { row -> SessionRow(row, rowActions) }
            if (state.hasMore) {
                item(key = "load-more", contentType = "loadMore") {
                    LaunchedEffect(content.rows.size) { actions.onLoadMore() }
                    Box(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(enabled = !state.loadingMore, onClick = actions.onLoadMore).testTag("home.loadMore"),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (state.loadingMore) {
                            CircularProgressIndicator(color = HermexTheme.colors.secondaryLabel, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                        } else {
                            Text("Load more", color = HermexTheme.colors.secondaryLabel, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
    item(key = "archived", contentType = "archived") {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .clickable(onClick = actions.onArchived)
                .heightIn(min = 48.dp)
                .padding(horizontal = 24.dp)
                .testTag("home.archived"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val colors = HermexTheme.colors
            Icon(Icons.Outlined.Archive, contentDescription = null, tint = colors.secondaryLabel, modifier = Modifier.size(20.dp))
            Text("Archived Sessions", color = colors.secondaryLabel, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = colors.secondaryLabel, modifier = Modifier.size(18.dp))
        }
    }
}

private fun LazyListScope.searchResults(search: SearchUi.Open, rowActions: SessionRowActions) {
    item(key = "results-title", contentType = "title") {
        Text(
            "Results",
            style = MaterialTheme.typography.titleLarge,
            color = HermexTheme.colors.label,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 8.dp),
        )
    }
    if (search.results.isEmpty()) {
        item(key = "results-empty", contentType = "status") {
            StatusText(if (search.searching) "Searching..." else "No sessions match “${search.query.trim()}”.", Modifier.testTag("search.empty"))
        }
    }
    items(search.results, key = { "r-${it.key}" }, contentType = { "session" }) { row ->
        SessionRow(row, rowActions, testTag = "search.result.${row.key}")
    }
}

@Composable
private fun SectionTitle(title: String, state: HomeUiState, actions: HomeActions) {
    val colors = HermexTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 28.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = colors.label, modifier = Modifier.weight(1f))
        state.selectedProject?.let { project ->
            Row(
                Modifier
                    .clip(CircleShape)
                    .background(colors.accent.copy(alpha = 0.12f))
                    .border(1.dp, colors.accent.copy(alpha = 0.25f), CircleShape)
                    .clickable(onClick = actions.onClearProject)
                    .heightIn(min = 36.dp)
                    .padding(start = 12.dp, end = 8.dp)
                    .testTag("project.chip"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(project.label, color = colors.accent, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 140.dp))
                Icon(Icons.Filled.Close, contentDescription = "Show all projects", tint = colors.accent, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun Header(initials: String, search: SearchUi.Open?, actions: HomeActions) {
    val colors = HermexTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 16.dp).heightIn(min = 52.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (search == null) {
            HermexWordmark(color = colors.logo, modifier = Modifier.width(160.dp))
            Spacer(Modifier.weight(1f))
        }
        Row(
            Modifier.then(if (search != null) Modifier.weight(1f) else Modifier).glassSurface(CircleShape).padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).clickable(onClick = actions.onOpenSearch).testTag("home.search"),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Search, contentDescription = "Search", tint = colors.label, modifier = Modifier.size(24.dp))
            }
            if (search != null) {
                val focus = remember { FocusRequester() }
                LaunchedEffect(Unit) { focus.requestFocus() }
                BasicTextField(
                    value = search.query,
                    onValueChange = actions.onQuery,
                    singleLine = true,
                    textStyle = TextStyle(color = colors.label, fontSize = 16.sp),
                    cursorBrush = SolidColor(colors.label),
                    modifier = Modifier.weight(1f).focusRequester(focus).testTag("search.field"),
                    decorationBox = { inner ->
                        Box {
                            if (search.query.isEmpty()) Text("Search sessions", color = colors.tertiaryLabel, fontSize = 16.sp)
                            inner()
                        }
                    },
                )
                Box(
                    Modifier.size(44.dp).clip(CircleShape).clickable {
                        if (search.query.isNotEmpty()) actions.onQuery("") else actions.onCloseSearch()
                    }.testTag("search.clear"),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Close, contentDescription = if (search.query.isNotEmpty()) "Clear search" else "Close search", tint = colors.secondaryLabel, modifier = Modifier.size(20.dp))
                }
            } else {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).clickable(onClick = actions.onSettings).testTag("home.avatar"),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(38.dp).clip(CircleShape).background(colors.logo), contentAlignment = Alignment.Center) {
                        Text(initials, color = colors.onLogo, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun NavRow(
    title: String,
    @DrawableRes icon: Int,
    testTag: String,
    onClick: () -> Unit,
    expanded: Boolean? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = HermexTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onClick)
                .heightIn(min = 48.dp)
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .testTag(testTag),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                Icon(painterResource(icon), contentDescription = null, tint = colors.label, modifier = Modifier.size(21.dp))
            }
            Spacer(Modifier.width(18.dp))
            Text(title, color = colors.label, fontSize = 17.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            if (expanded != null) {
                val angle by animateFloatAsState(if (expanded) 90f else 0f, label = "chevron")
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = colors.secondaryLabel, modifier = Modifier.rotate(angle))
            }
        }
        trailing?.invoke()
    }
}

@Composable
private fun SubRow(
    title: String,
    subtitle: String?,
    @DrawableRes icon: Int,
    selected: Boolean,
    testTag: String,
    onClick: () -> Unit,
    iconTint: Color? = null,
    count: Int = 0,
) {
    val colors = HermexTheme.colors
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = 2.dp)
            .clip(shape)
            .then(if (selected) Modifier.background(colors.accent.copy(alpha = 0.10f)).border(1.dp, colors.accent.copy(alpha = 0.20f), shape) else Modifier)
            .clickable(onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(start = 18.dp, end = 12.dp, top = 4.dp, bottom = 4.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            tint = iconTint ?: if (selected) colors.accent else colors.label,
            modifier = Modifier.size(21.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(title, color = colors.label, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, color = colors.secondaryLabel, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (count > 0) Text("$count", color = colors.secondaryLabel, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        if (selected) Icon(Icons.Filled.CheckCircle, contentDescription = "Selected", tint = colors.accent, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun SubStatus(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        color = HermexTheme.colors.secondaryLabel,
        fontSize = 14.sp,
        modifier = modifier.fillMaxWidth().padding(start = 64.dp, end = 24.dp, top = 10.dp, bottom = 10.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
private fun SessionRow(row: SessionRowUi, actions: SessionRowActions, testTag: String = "session.row.${row.key}") {
    val colors = HermexTheme.colors
    var menu by remember { mutableStateOf(false) }
    Box {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .clip(RoundedCornerShape(10.dp))
                .combinedClickable(onClick = { actions.onOpen(row) }, onLongClick = { menu = true }, onLongClickLabel = "Session actions")
                .heightIn(min = 54.dp)
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .testTag(testTag),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                if (row.unread) {
                    Box(Modifier.padding(top = 6.dp, end = 6.dp).size(10.dp).clip(CircleShape).background(colors.accent).testTag("session.unread.${row.key}"))
                }
                Text(
                    row.title,
                    color = colors.label,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                Text(row.time, color = colors.secondaryLabel, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp))
            }
            Text(row.subtitle, color = colors.secondaryLabel, fontSize = 12.sp)
        }
        DropdownMenu(
            expanded = menu,
            onDismissRequest = { menu = false },
            containerColor = colors.tertiaryBackground,
            modifier = Modifier.semantics { testTagsAsResourceId = true },
        ) {
            fun pick(action: (SessionRowUi) -> Unit): () -> Unit = {
                menu = false
                action(row)
            }
            MenuItem("Rename", Icons.Outlined.Edit, "session.menu.rename", colors, pick(actions.onRename))
            MenuItem(if (row.pinned) "Unpin" else "Pin", Icons.Outlined.PushPin, "session.menu.pin", colors, pick(actions.onTogglePinned))
            MenuItem(
                if (row.unread) "Mark as Read" else "Mark as Unread",
                if (row.unread) Icons.Outlined.Drafts else Icons.Outlined.Email,
                "session.menu.unread",
                colors,
                pick(actions.onToggleUnread),
            )
            MenuItem("Duplicate", Icons.Outlined.ContentCopy, "session.menu.duplicate", colors, pick(actions.onDuplicate))
            MenuItem("Archive", Icons.Outlined.Archive, "session.menu.archive", colors, pick(actions.onArchive))
            MenuItem("Delete", Icons.Outlined.Delete, "session.menu.delete", colors, pick(actions.onDelete), danger = true)
        }
    }
}

@Composable
private fun MenuItem(title: String, icon: ImageVector, tag: String, colors: HermexColors, onClick: () -> Unit, danger: Boolean = false) {
    val tint = if (danger) colors.danger else colors.label
    DropdownMenuItem(
        text = { Text(title, color = tint) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = tint) },
        onClick = onClick,
        modifier = Modifier.testTag(tag),
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
private fun RenameSheet(row: SessionRowUi, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    val colors = HermexTheme.colors
    var text by rememberSaveable(row.key) { mutableStateOf(row.title) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.secondaryBackground,
    ) {
        Column(
            Modifier.fillMaxWidth().semantics { testTagsAsResourceId = true }.padding(horizontal = 24.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss) { Text("Cancel", color = colors.label) }
                Text("Rename Session", color = colors.label, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                TextButton(onClick = { onSave(text) }, enabled = text.isNotBlank(), modifier = Modifier.testTag("rename.save")) {
                    Text("Save", color = if (text.isNotBlank()) colors.accent else colors.tertiaryLabel, fontWeight = FontWeight.SemiBold)
                }
            }
            SheetField(value = text, onValueChange = { text = it.take(100) }, placeholder = "Session title", tag = "rename.field", autoFocus = true)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
private fun ProjectCreationSheet(sheet: ProjectSheet.Editing, onDismiss: () -> Unit, onCreate: (String, String) -> Unit) {
    val colors = HermexTheme.colors
    var name by rememberSaveable { mutableStateOf("") }
    var folder by rememberSaveable { mutableStateOf("") }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.secondaryBackground,
    ) {
        Column(
            Modifier.fillMaxWidth().semantics { testTagsAsResourceId = true }.padding(horizontal = 24.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss) { Text("Cancel", color = colors.label) }
                Text("New Project", color = colors.label, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                TextButton(onClick = { onCreate(name, folder) }, enabled = !sheet.saving, modifier = Modifier.testTag("project.create.save")) {
                    Text("Create", color = colors.accent, fontWeight = FontWeight.SemiBold)
                }
            }
            SheetField(value = name, onValueChange = { name = it }, placeholder = "Project name", tag = "project.create.name", autoFocus = true)
            SheetField(value = folder, onValueChange = { folder = it }, placeholder = "~/Projects/app", tag = "project.create.folder")
            Text("The folder on the Hermes host that this project's sessions work in.", color = colors.secondaryLabel, fontSize = 12.sp)
            sheet.error?.let { Text(it, color = colors.danger, fontSize = 13.sp, modifier = Modifier.testTag("project.create.error")) }
            if (sheet.saving) CircularProgressIndicator(color = colors.secondaryLabel, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun SheetField(value: String, onValueChange: (String) -> Unit, placeholder: String, tag: String, autoFocus: Boolean = false) {
    val colors = HermexTheme.colors
    val focus = remember { FocusRequester() }
    if (autoFocus) LaunchedEffect(Unit) { focus.requestFocus() }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(color = colors.label, fontSize = 16.sp),
        cursorBrush = SolidColor(colors.label),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.tertiaryBackground)
            .padding(horizontal = 14.dp, vertical = 14.dp)
            .focusRequester(focus)
            .testTag(tag),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) Text(placeholder, color = colors.tertiaryLabel, fontSize = 16.sp)
                inner()
            }
        },
    )
}

@Composable
private fun Toast(toast: HomeToast, actions: HomeActions, modifier: Modifier = Modifier) {
    val colors = HermexTheme.colors
    LaunchedEffect(toast.id) {
        delay(if (toast.undoArchiveKey != null) 6_000 else 4_000)
        actions.onDismissToast(toast.id)
    }
    Row(
        modifier
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(start = 20.dp, end = 20.dp, bottom = 84.dp)
            .glassSurface(RoundedCornerShape(16.dp), elevated = true)
            .padding(start = 16.dp, end = 8.dp)
            .heightIn(min = 48.dp)
            .testTag("toast"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(toast.message, color = colors.label, fontSize = 14.sp, modifier = Modifier.weight(1f, fill = false).padding(vertical = 10.dp).testTag("toast.message"))
        toast.undoArchiveKey?.let { key ->
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = { actions.onUndoArchive(key) }, modifier = Modifier.testTag("toast.undo")) {
                Text("Undo", color = colors.accent, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun StatusText(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        color = HermexTheme.colors.secondaryLabel,
        fontSize = 14.sp,
        modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
    )
}

private fun projectColor(name: String?): Color? = when (name?.lowercase()) {
    null, "" -> null
    "red" -> Color(0xFFFF453A)
    "orange" -> Color(0xFFFF9F0A)
    "yellow" -> Color(0xFFFFD60A)
    "green" -> Color(0xFF30D158)
    "teal", "cyan" -> Color(0xFF64D2FF)
    "blue" -> Color(0xFF0A84FF)
    "indigo" -> Color(0xFF5E5CE6)
    "purple" -> Color(0xFFBF5AF2)
    "pink" -> Color(0xFFFF375F)
    else -> runCatching { Color(android.graphics.Color.parseColor(name)) }.getOrNull()
}
