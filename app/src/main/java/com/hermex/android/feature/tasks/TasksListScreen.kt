package com.hermex.android.feature.tasks

import com.hermex.android.core.designsystem.PanelScaffold
import com.hermex.android.core.designsystem.PanelTopBar
import com.hermex.android.core.designsystem.BarIcon
import com.hermex.android.core.designsystem.StatusBadge
import com.hermex.android.core.designsystem.Segmented
import com.hermex.android.core.designsystem.ContentUnavailable
import com.hermex.android.core.designsystem.LoadingState
import com.hermex.android.core.designsystem.LabelValueRow
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hermex.android.core.designsystem.HermexTheme
import com.hermex.android.core.model.CronJob

@Composable
internal fun TasksListRoute(
    viewModel: TasksViewModel,
    onBack: () -> Unit,
    onOpen: (CronJob) -> Unit,
    onNew: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.load() }
    TasksListScreen(
        state = state,
        onBack = onBack,
        onNew = onNew,
        onRefresh = viewModel::load,
        onFilter = viewModel::setFilter,
        onOpen = onOpen,
        onRunNow = viewModel::runNow,
        onTogglePause = viewModel::togglePause,
        onDelete = viewModel::delete,
        onDismissAlert = viewModel::dismissAlert,
    )
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun TasksListScreen(
    state: TasksScreenState,
    onBack: () -> Unit,
    onNew: () -> Unit,
    onRefresh: () -> Unit,
    onFilter: (TaskFilter) -> Unit,
    onOpen: (CronJob) -> Unit,
    onRunNow: (CronJob) -> Unit,
    onTogglePause: (CronJob) -> Unit,
    onDelete: (CronJob) -> Unit,
    onDismissAlert: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HermexTheme.colors
    var pendingDelete by remember { mutableStateOf<CronJob?>(null) }
    var pendingRun by remember { mutableStateOf<CronJob?>(null) }
    PanelScaffold(modifier) {
        PanelTopBar(title = "Tasks", onBack = onBack, backTag = "tasks.back") {
            BarIcon(Icons.Filled.Add, "New Task", "tasks.new", onClick = onNew)
            BarIcon(Icons.Filled.Refresh, "Refresh", "tasks.refresh", busy = state.refreshing && state.content !is TasksUiState.Loading, onClick = onRefresh)
        }
        when (val content = state.content) {
            TasksUiState.Loading -> LoadingState("Loading tasks...")
            is TasksUiState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ContentUnavailable(
                    Icons.Filled.Warning, "Could Not Load Tasks", content.message,
                    actionLabel = "Try Again", actionTag = "tasks.retry", onAction = onRefresh,
                )
            }
            TasksUiState.Empty -> Box(Modifier.fillMaxSize().testTag("tasks.empty"), contentAlignment = Alignment.Center) {
                ContentUnavailable(Icons.Filled.CalendarMonth, "No Tasks", "Scheduled jobs from the Hermes server will appear here.")
            }
            is TasksUiState.Success -> {
                Segmented(
                    options = TaskFilter.entries.map { it.slug to "${it.title} ${state.count(it)}" },
                    selected = state.filter.slug,
                    onSelect = { slug -> onFilter(TaskFilter.entries.first { it.slug == slug }) },
                    tagPrefix = "tasks.filter",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                val visible = state.visible
                LazyColumn(
                    Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
                ) {
                    state.stallAge?.let { age ->
                        item(key = "stall", contentType = "stall") {
                            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.Top) {
                                Icon(Icons.Filled.Warning, null, tint = SystemOrange, modifier = Modifier.size(18.dp))
                                Text(
                                    "Scheduled Tasks aren't running. The host's scheduler hasn't checked in for ${TaskFormat.stalled(age)}.",
                                    color = colors.label, fontSize = 13.sp, modifier = Modifier.padding(start = 8.dp),
                                )
                            }
                        }
                    }
                    item(key = "running", contentType = "running") {
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(colors.secondaryBackground)
                                .padding(horizontal = 20.dp, vertical = 16.dp).testTag("tasks.runningNow"),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.Bolt, null, tint = colors.accent, modifier = Modifier.size(24.dp))
                            Text("Running now", color = colors.label, fontSize = 17.sp, modifier = Modifier.padding(start = 14.dp).weight(1f))
                            Text("${state.running.size}", color = colors.secondaryLabel, fontSize = 17.sp)
                        }
                    }
                    item(key = "header", contentType = "header") {
                        Text(
                            "Scheduled Jobs",
                            color = colors.secondaryLabel, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 20.dp, top = 24.dp, bottom = 10.dp),
                        )
                    }
                    if (visible.isEmpty()) {
                        item(key = "nomatch", contentType = "nomatch") {
                            ContentUnavailable(Icons.Filled.FilterList, "No Matching Tasks", "No tasks match this filter.")
                        }
                    }
                    itemsIndexed(visible, key = { _, job -> job.id }, contentType = { _, _ -> "job" }) { index, job ->
                        val shape = when {
                            visible.size == 1 -> RoundedCornerShape(22.dp)
                            index == 0 -> RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
                            index == visible.lastIndex -> RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp)
                            else -> RoundedCornerShape(0.dp)
                        }
                        Column(Modifier.clip(shape).background(colors.secondaryBackground)) {
                            JobCard(
                                job = job,
                                pending = job.id in state.pending,
                                onOpen = { onOpen(job) },
                                onRunNow = { if (job.shouldResume) pendingRun = job else onRunNow(job) },
                                onTogglePause = { onTogglePause(job) },
                                onDelete = { pendingDelete = job },
                            )
                            if (index != visible.lastIndex) HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = colors.separator.copy(alpha = 0.6f))
                        }
                    }
                }
            }
        }
    }
    pendingDelete?.let { job ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete Task?") },
            text = { Text("“${job.displayName}” will be removed from the Hermes server.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        onDelete(job)
                    },
                    modifier = Modifier.semantics { testTagsAsResourceId = true }.testTag("tasks.delete.confirm"),
                ) { Text("Delete", color = colors.danger) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }
    pendingRun?.let { job ->
        AlertDialog(
            onDismissRequest = { pendingRun = null },
            title = { Text("Run Now?") },
            text = { Text("This also resumes the Task.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingRun = null
                        onRunNow(job)
                    },
                    modifier = Modifier.semantics { testTagsAsResourceId = true }.testTag("tasks.runNow.confirm"),
                ) { Text("Run Now") }
            },
            dismissButton = { TextButton(onClick = { pendingRun = null }) { Text("Cancel") } },
        )
    }
    state.alert?.let { message ->
        AlertDialog(
            onDismissRequest = onDismissAlert,
            title = { Text("Could Not Update Task") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = onDismissAlert) { Text("OK") } },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
private fun JobCard(
    job: CronJob,
    pending: Boolean,
    onOpen: () -> Unit,
    onRunNow: () -> Unit,
    onTogglePause: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = HermexTheme.colors
    var menu by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onOpen, onLongClick = { menu = true })
                .padding(start = 20.dp, end = 12.dp, top = 18.dp, bottom = 18.dp)
                .testTag("tasks.row.${job.id}"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        job.displayName,
                        color = colors.label, fontSize = 19.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(10.dp))
                    StatusBadge(if (pending) "Updating" else job.badgeText(), job.badgeColor())
                }
                job.prompt?.takeIf { it.isNotBlank() }?.let {
                    Text(it, color = colors.secondaryLabel, fontSize = 16.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                Column(Modifier.padding(top = 4.dp)) {
                    LabelValueRow("Schedule", job.editableSchedule ?: job.scheduleText ?: "Not available")
                    job.nextRunAt?.let { LabelValueRow("Next", TaskFormat.date(it)) }
                    job.lastRunAt?.let { LabelValueRow("Last", TaskFormat.date(it)) }
                    LabelValueRow("Deliver", job.deliver?.takeIf { it.isNotBlank() } ?: "local")
                    if (job.skills.isNotEmpty()) LabelValueRow("Skills", job.skills.joinToString(", "))
                    job.model?.takeIf { it.isNotBlank() }?.let { LabelValueRow("Model", it) }
                }
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight, null,
                tint = colors.tertiaryLabel, modifier = Modifier.padding(start = 6.dp).size(24.dp),
            )
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            val tagged = Modifier.semantics { testTagsAsResourceId = true }
            DropdownMenuItem(
                text = { Text("Run Now") },
                enabled = !pending,
                onClick = {
                    menu = false
                    onRunNow()
                },
                modifier = tagged.testTag("tasks.menu.runNow"),
            )
            DropdownMenuItem(
                text = { Text(if (job.shouldResume) "Resume" else "Pause") },
                enabled = !pending,
                onClick = {
                    menu = false
                    onTogglePause()
                },
                modifier = tagged.testTag("tasks.menu.pause"),
            )
            DropdownMenuItem(
                text = { Text("Delete", color = colors.danger) },
                enabled = !pending,
                onClick = {
                    menu = false
                    onDelete()
                },
                modifier = tagged.testTag("tasks.menu.delete"),
            )
        }
    }
}
