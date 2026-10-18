package com.hermex.android.feature.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hermex.android.core.designsystem.HermexTheme
import com.hermex.android.core.model.CronJob
import com.hermex.android.core.model.CronRun

@Composable
internal fun TaskDetailRoute(
    viewModel: TaskDetailViewModel,
    onBack: () -> Unit,
    onEdit: (CronJob) -> Unit,
    onOpenRun: (CronJob, CronRun, String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.load() }
    LaunchedEffect(state.deleted) { if (state.deleted) onBack() }
    TaskDetailScreen(
        state = state,
        onBack = onBack,
        onRefresh = viewModel::load,
        onRunNow = viewModel::runNow,
        onTogglePause = viewModel::togglePause,
        onEdit = { state.job?.let(onEdit) },
        onDelete = viewModel::delete,
        onOpenRun = { run -> state.job?.let { onOpenRun(it, run, runTitle(run)) } },
        onRetryRuns = viewModel::retryRuns,
        onLoadMore = viewModel::loadMore,
        onDismissAlert = viewModel::dismissAlert,
    )
}

private fun runTitle(run: CronRun): String = (run.endedAt ?: run.startedAt)?.let(TaskFormat::date) ?: run.id

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun TaskDetailScreen(
    state: TaskDetailScreenState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onRunNow: () -> Unit,
    onTogglePause: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onOpenRun: (CronRun) -> Unit,
    onRetryRuns: () -> Unit,
    onLoadMore: () -> Unit,
    onDismissAlert: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HermexTheme.colors
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmRun by remember { mutableStateOf(false) }
    val job = state.job
    val disabled = state.busy || job == null
    val requestRun = { if (job?.shouldResume == true) confirmRun = true else onRunNow() }
    PanelScaffold(modifier) {
        PanelTopBar(title = job?.displayName ?: "Task", onBack = onBack, backTag = "task.back") {
            BarIcon(Icons.Filled.Refresh, "Refresh", "task.refresh", onClick = onRefresh)
            Box {
                BarIcon(Icons.Filled.MoreHoriz, "Task Actions", "task.menu", enabled = !disabled) { menu = true }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    val tagged = Modifier.semantics { testTagsAsResourceId = true }
                    DropdownMenuItem(
                        text = { Text("Run Now") },
                        leadingIcon = { Icon(Icons.Filled.PlayArrow, null) },
                        enabled = !state.runNowPending,
                        onClick = {
                            menu = false
                            requestRun()
                        },
                        modifier = tagged.testTag("task.menu.runNow"),
                    )
                    DropdownMenuItem(
                        text = { Text(if (job?.shouldResume == true) "Resume" else "Pause") },
                        leadingIcon = { Icon(if (job?.shouldResume == true) Icons.Filled.PlayCircleOutline else Icons.Filled.Pause, null) },
                        onClick = {
                            menu = false
                            onTogglePause()
                        },
                        modifier = tagged.testTag("task.menu.pause"),
                    )
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        leadingIcon = { Icon(Icons.Filled.Edit, null) },
                        onClick = {
                            menu = false
                            onEdit()
                        },
                        modifier = tagged.testTag("task.edit"),
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Delete", color = colors.danger) },
                        leadingIcon = { Icon(Icons.Filled.Delete, null, tint = colors.danger) },
                        onClick = {
                            menu = false
                            confirmDelete = true
                        },
                        modifier = tagged.testTag("task.delete"),
                    )
                }
            }
        }
        when (val content = state.content) {
            TaskDetailUiState.Loading -> LoadingState("Loading task...")
            is TaskDetailUiState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ContentUnavailable(Icons.Filled.Warning, "Could Not Load Task", content.message, actionLabel = "Try Again", onAction = onRefresh)
            }
            TaskDetailUiState.Gone -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ContentUnavailable(Icons.Filled.Warning, "Task Not Found", "This task is no longer on the server.")
            }
            is TaskDetailUiState.Success -> Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                HeaderCard(content.job, state, onRunNow = requestRun, onTogglePause = onTogglePause)
                content.job.prompt?.takeIf { it.isNotBlank() }?.let { prompt ->
                    SectionCard(title = "Prompt") {
                        SelectionContainer { Text(prompt, color = colors.label, fontSize = 15.sp, modifier = Modifier.testTag("task.prompt")) }
                    }
                }
                state.latestOutput?.takeIf { it.isNotBlank() }?.let { output ->
                    SectionCard(title = "Run Output") {
                        Text(output, color = colors.label, fontSize = 13.sp, fontFamily = FontFamily.Monospace, maxLines = 14, overflow = TextOverflow.Ellipsis)
                    }
                }
                SectionCard(title = "Configuration") {
                    val job = content.job
                    LabelValueRow("Schedule", job.scheduleText ?: "Not available")
                    job.editableSchedule?.takeIf { it != job.scheduleText }?.let {
                        Text(it, color = colors.tertiaryLabel, fontSize = 11.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(start = 82.dp))
                    }
                    LabelValueRow("Deliver", job.deliver?.takeIf { it.isNotBlank() } ?: "local")
                    job.model?.takeIf { it.isNotBlank() }?.let { LabelValueRow("Model", it) }
                    job.provider?.takeIf { it.isNotBlank() }?.let { LabelValueRow("Provider", it) }
                    job.profile?.takeIf { it.isNotBlank() }?.let { LabelValueRow("Profile", it) }
                    if (job.skills.isNotEmpty()) LabelValueRow("Skills", job.skills.joinToString(", "))
                }
                RunHistory(state, onOpenRun, onRetryRuns, onLoadMore)
                Spacer(Modifier.height(16.dp))
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete Task?") },
            text = { Text("“${job?.displayName.orEmpty()}” will be removed from the Hermes server.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDelete()
                    },
                    modifier = Modifier.semantics { testTagsAsResourceId = true }.testTag("task.delete.confirm"),
                ) { Text("Delete", color = colors.danger) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
    if (confirmRun) {
        AlertDialog(
            onDismissRequest = { confirmRun = false },
            title = { Text("Run Now?") },
            text = { Text("This also resumes the Task.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRun = false
                        onRunNow()
                    },
                    modifier = Modifier.semantics { testTagsAsResourceId = true }.testTag("task.runNow.confirm"),
                ) { Text("Run Now") }
            },
            dismissButton = { TextButton(onClick = { confirmRun = false }) { Text("Cancel") } },
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

@Composable
private fun HeaderCard(job: CronJob, state: TaskDetailScreenState, onRunNow: () -> Unit, onTogglePause: () -> Unit) {
    val colors = HermexTheme.colors
    val running = job.isRunning || state.runNowPending
    Column(Modifier.fillMaxWidth().clip(CardShape).background(colors.secondaryBackground)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(job.displayName, color = colors.label, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 3)
                    Text(job.scheduleText ?: "Not available", color = colors.secondaryLabel, fontSize = 14.sp, maxLines = 2)
                }
                Spacer(Modifier.width(8.dp))
                StatusBadge(if (running) "Running" else job.badgeText(), if (running) colors.working else job.badgeColor(), Modifier.testTag("task.status"))
            }
            when {
                running -> Column {
                    Text(job.fireClaimAt?.let { TaskFormat.elapsed(((System.currentTimeMillis() - it.toEpochMilli()) / 1000.0).coerceAtLeast(0.0)) } ?: "Starting", color = colors.working, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                    Text("Running now", color = colors.secondaryLabel, fontSize = 13.sp)
                }
                job.nextRunAt != null -> Column {
                    Text(TaskFormat.countdown(job.nextRunAt), color = colors.label, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                    Text("Next run · ${TaskFormat.date(job.nextRunAt)}", color = colors.secondaryLabel, fontSize = 13.sp)
                }
                job.lastRunAt != null -> Column {
                    Text("Not scheduled", color = colors.secondaryLabel, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Text("Last run · ${TaskFormat.date(job.lastRunAt)}", color = colors.secondaryLabel, fontSize = 13.sp)
                }
                else -> Text("Not scheduled", color = colors.secondaryLabel, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            }
            job.failureSummary?.let { failure ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.danger.copy(alpha = 0.10f)).padding(10.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(Icons.Filled.Warning, contentDescription = "Last run failed.", tint = colors.danger, modifier = Modifier.size(18.dp))
                    Text(failure, color = colors.label, fontSize = 13.sp, maxLines = 3, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
        HorizontalDivider(color = colors.separator.copy(alpha = 0.6f))
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            FooterButton(
                title = "Run now", icon = Icons.Filled.PlayArrow, tag = "task.runNow",
                waiting = state.runNowPending, enabled = !state.busy && !state.runNowPending, onClick = onRunNow,
                modifier = Modifier.weight(1f),
            )
            VerticalDivider(Modifier.fillMaxHeight(), color = colors.separator.copy(alpha = 0.6f))
            FooterButton(
                title = if (job.shouldResume) "Resume" else "Pause",
                icon = if (job.shouldResume) Icons.Filled.PlayCircleOutline else Icons.Filled.Pause,
                tag = "task.pause", waiting = state.busy, enabled = !state.busy, onClick = onTogglePause,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun FooterButton(
    title: String,
    icon: ImageVector,
    tag: String,
    waiting: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HermexTheme.colors
    Row(
        modifier.heightIn(min = 48.dp).clickable(enabled = enabled, onClick = onClick).testTag(tag),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (waiting) {
            CircularProgressIndicator(Modifier.size(14.dp), color = colors.secondaryLabel, strokeWidth = 2.dp)
        } else {
            Icon(icon, null, tint = colors.secondaryLabel, modifier = Modifier.size(16.dp))
        }
        Text(title, color = if (enabled) colors.label else colors.tertiaryLabel, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 7.dp))
    }
}

@Composable
private fun RunHistory(state: TaskDetailScreenState, onOpenRun: (CronRun) -> Unit, onRetry: () -> Unit, onLoadMore: () -> Unit) {
    val colors = HermexTheme.colors
    val runs = (state.runs as? RunsState.Loaded)?.runs
    SectionCard(title = if (runs != null) "Run History · ${runs.size}" else "Run History", modifier = Modifier.testTag("task.runs")) {
        when (val history = state.runs) {
            RunsState.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(14.dp), color = colors.secondaryLabel, strokeWidth = 2.dp)
                Text("Loading runs...", color = colors.secondaryLabel, fontSize = 13.sp, modifier = Modifier.padding(start = 8.dp))
            }
            is RunsState.Error -> Column {
                Text(history.message, color = colors.secondaryLabel, fontSize = 13.sp)
                Text("Try Again", color = colors.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp).clickable(onClick = onRetry))
            }
            is RunsState.Loaded -> if (history.runs.isEmpty()) {
                Text("This task has not produced any output yet.", color = colors.secondaryLabel, fontSize = 13.sp, modifier = Modifier.testTag("task.runs.empty"))
            } else {
                val shown = history.runs.take(state.visibleRuns)
                shown.forEachIndexed { index, run ->
                    if (index > 0) HorizontalDivider(color = colors.separator.copy(alpha = 0.5f))
                    RunRow(run, state.outcome(run), onClick = { onOpenRun(run) })
                }
                if (history.runs.size > shown.size) {
                    HorizontalDivider(color = colors.separator.copy(alpha = 0.5f))
                    Text(
                        "Load more", color = colors.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.fillMaxWidth().clickable(onClick = onLoadMore).padding(vertical = 10.dp).testTag("task.runs.more"),
                    )
                }
            }
        }
    }
}

@Composable
private fun RunRow(run: CronRun, outcome: RunOutcome?, onClick: () -> Unit) {
    val colors = HermexTheme.colors
    val dot = when (outcome) {
        RunOutcome.Running -> colors.working
        RunOutcome.Completed -> colors.success
        RunOutcome.Failed -> colors.danger
        null -> colors.tertiaryLabel
    }
    val detail = buildList {
        when (outcome) {
            RunOutcome.Running -> add("Running")
            RunOutcome.Failed -> add("Failed")
            RunOutcome.Completed, null -> Unit
        }
        val started = run.startedAt
        val ended = run.endedAt
        if (started != null && ended != null) {
            val seconds = (ended.toEpochMilli() - started.toEpochMilli()) / 1000.0
            if (seconds > 0) add(TaskFormat.duration(seconds))
        }
        run.model?.takeIf { it.isNotBlank() }?.let(::add)
        run.totalTokens?.takeIf { it > 0 }?.let { add("${TaskFormat.tokens(it)} tokens") }
        run.costUsd?.takeIf { it > 0 }?.let { add(TaskFormat.cost(it)) }
    }.joinToString(" · ")
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp).testTag("task.run.${run.id}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(dot))
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(runTitle(run), color = colors.label, fontSize = 14.sp, maxLines = 2, modifier = Modifier.weight(1f, fill = false))
                when (outcome) {
                    RunOutcome.Running -> StatusBadge("Running", colors.working, Modifier.padding(start = 8.dp))
                    RunOutcome.Completed -> StatusBadge("Completed", colors.success, Modifier.padding(start = 8.dp))
                    RunOutcome.Failed -> StatusBadge("Failed", colors.danger, Modifier.padding(start = 8.dp))
                    null -> Unit
                }
            }
            if (detail.isNotEmpty()) Text(detail, color = colors.secondaryLabel, fontSize = 12.sp, maxLines = 2)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = colors.tertiaryLabel, modifier = Modifier.size(20.dp))
    }
}
