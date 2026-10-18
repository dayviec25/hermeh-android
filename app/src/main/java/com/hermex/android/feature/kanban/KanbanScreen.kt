package com.hermex.android.feature.kanban

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hermex.android.core.designsystem.GlassCircleButton
import com.hermex.android.core.designsystem.HermexTheme
import com.hermex.android.core.model.KanbanBoard
import com.hermex.android.core.model.KanbanCard
import com.hermex.android.core.model.KanbanStatus

@Composable
fun KanbanRoute(
    viewModel: KanbanViewModel,
    detailViewModel: @Composable (board: String, cardId: String) -> CardDetailViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var openCard by rememberSaveable { mutableStateOf<String?>(null) }
    val ready = state as? KanbanUiState.Ready
    val cardId = openCard
    if (cardId != null && ready != null) {
        BackHandler { openCard = null; viewModel.refresh() }
        CardDetailRoute(
            viewModel = detailViewModel(ready.board.slug, cardId),
            profiles = ready.profiles,
            onClose = { notice ->
                openCard = null
                viewModel.refresh()
                if (notice != null) viewModel.showNotice(notice)
            },
            modifier = modifier,
        )
        return
    }
    KanbanScreen(
        state = state,
        onBack = onBack,
        onRetry = viewModel::retry,
        onRefresh = viewModel::refresh,
        onSelectBoard = viewModel::selectBoard,
        onSelectStatus = viewModel::selectStatus,
        onQueryChange = viewModel::onQueryChange,
        onOpenCard = { openCard = it.id.value },
        onAction = { card, action ->
            when (action) {
                is CardAction.Move -> viewModel.move(card, action.to)
                CardAction.Unblock -> viewModel.unblock(card)
                CardAction.Complete -> viewModel.complete(card)
                CardAction.Archive -> viewModel.archive(card)
                CardAction.Block -> Unit
            }
        },
        onBlock = viewModel::block,
        onNewCard = viewModel::openNewCard,
        onDismissNotice = viewModel::dismissNotice,
        onDispatch = viewModel::dispatch,
        onDismissDispatch = viewModel::dismissDispatch,
        modifier = modifier,
    )
    val editor = ready?.editor
    if (editor != null) {
        CardEditorDialog(editor = editor, profiles = ready.profiles, onSave = viewModel::save, onDismiss = viewModel::closeEditor)
    }
}

@Composable
fun KanbanScreen(
    state: KanbanUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
    onSelectBoard: (String) -> Unit,
    onSelectStatus: (KanbanStatus) -> Unit,
    onQueryChange: (String) -> Unit,
    onOpenCard: (KanbanCard) -> Unit,
    onAction: (KanbanCard, CardAction) -> Unit,
    onBlock: (KanbanCard, String?) -> Unit,
    onNewCard: () -> Unit,
    onDismissNotice: () -> Unit,
    onDispatch: (Boolean) -> Unit,
    onDismissDispatch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HermexTheme.colors
    var showsDispatcher by rememberSaveable { mutableStateOf(false) }
    var blocking by remember { mutableStateOf<KanbanCard?>(null) }
    Column(modifier.fillMaxSize().background(colors.background).windowInsetsPadding(WindowInsets.statusBars)) {
        val ready = state as? KanbanUiState.Ready
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassCircleButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack, Modifier.testTag("kanban.back"))
            Box(Modifier.weight(1f).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                if (ready != null) {
                    BoardPicker(ready.boards, ready.board, onSelectBoard)
                } else {
                    Text("Kanban", color = colors.label, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            GlassPill {
                PillIconButton(Icons.Outlined.Add, "New Card", onNewCard, Modifier.testTag("kanban.new"), enabled = ready != null)
                PillIconButton(Icons.Outlined.Bolt, "Dispatcher", { showsDispatcher = true }, Modifier.testTag("kanban.dispatch"), enabled = ready != null)
            }
        }
        when (state) {
            KanbanUiState.Loading -> Column(
                Modifier.fillMaxSize().testTag("kanban.loading"),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator(color = colors.secondaryLabel, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                Text("Loading Kanban", color = colors.secondaryLabel, fontSize = 15.sp, modifier = Modifier.padding(top = 12.dp))
            }
            is KanbanUiState.Error -> Unavailable(state.title, state.detail, onRetry, Modifier.testTag("kanban.error"))
            is KanbanUiState.Ready -> BoardContent(
                state = state,
                onRefresh = onRefresh,
                onSelectStatus = onSelectStatus,
                onQueryChange = onQueryChange,
                onOpenCard = onOpenCard,
                onAction = { card, action -> if (action == CardAction.Block) blocking = card else onAction(card, action) },
                onDismissNotice = onDismissNotice,
            )
        }
    }
    val ready = state as? KanbanUiState.Ready
    if (showsDispatcher && ready != null) {
        DispatcherSheet(ready.dispatch, onDispatch, onDismissDispatch, onClose = { showsDispatcher = false })
    }
    blocking?.let { card ->
        BlockDialog(onConfirm = { reason -> blocking = null; onBlock(card, reason) }, onDismiss = { blocking = null })
    }
}

@Composable
private fun BoardContent(
    state: KanbanUiState.Ready,
    onRefresh: () -> Unit,
    onSelectStatus: (KanbanStatus) -> Unit,
    onQueryChange: (String) -> Unit,
    onOpenCard: (KanbanCard) -> Unit,
    onAction: (KanbanCard, CardAction) -> Unit,
    onDismissNotice: () -> Unit,
) {
    val colors = HermexTheme.colors
    SearchField(state.query, onQueryChange, "Search Cards", Modifier.padding(horizontal = 16.dp, vertical = 4.dp).testTag("kanban.search"))
    StatusSelector(state.counts, state.status, onSelectStatus)
    state.notice?.let { NoticeBanner(it, onDismissNotice) }
    HorizontalDivider(color = colors.separator.copy(alpha = 0.5f), modifier = Modifier.padding(top = 4.dp))
    PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize().testTag("kanban.list"),
            contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp),
        ) {
            if (state.rows.isEmpty()) {
                item(key = "empty", contentType = "empty") {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 64.dp).testTag("kanban.empty"),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        val filtered = state.query.isNotBlank()
                        Text(
                            if (filtered) "No matching Cards" else "No Cards in this Status",
                            color = colors.label,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            if (filtered) "Change or clear the filters to see more Cards." else "Choose another Status or refresh the Board.",
                            color = colors.secondaryLabel,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            } else {
                items(state.rows, key = { it.key }, contentType = { "card" }) { row ->
                    CardRow(
                        row = row,
                        busy = row.card.id in state.busy,
                        onOpen = { onOpenCard(row.card) },
                        onAction = { onAction(row.card, it) },
                    )
                }
            }
        }
    }
}

@Composable
private fun BoardPicker(boards: List<KanbanBoard>, board: KanbanBoard, onSelect: (String) -> Unit) {
    val colors = HermexTheme.colors
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .clip(RoundedCornerShape(22.dp))
                .clickable { open = true }
                .heightIn(min = 44.dp)
                .padding(horizontal = 12.dp)
                .testTag("kanban.board"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                board.name,
                color = colors.label,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 180.dp),
            )
            Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "Switch Board", tint = colors.secondaryLabel, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.semantics { testTagsAsResourceId = true }) {
            boards.forEach { option ->
                DropdownMenuItem(
                    text = { Text("${option.name}  ·  ${countLabel(option.total, "Card")}") },
                    leadingIcon = {
                        if (option.slug == board.slug) Icon(Icons.Outlined.Check, contentDescription = null) else Spacer(Modifier.size(24.dp))
                    },
                    onClick = {
                        open = false
                        if (option.slug != board.slug) onSelect(option.slug)
                    },
                    modifier = Modifier.testTag("kanban.board.${option.slug}"),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DispatcherSheet(dispatch: DispatchUi, onDispatch: (Boolean) -> Unit, onDismissResult: () -> Unit, onClose: () -> Unit) {
    val colors = HermexTheme.colors
    var confirmRun by remember { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.secondaryBackground,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .semantics { testTagsAsResourceId = true }
                .padding(horizontal = 20.dp)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 16.dp)
                .testTag("kanban.dispatcher"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Dispatcher", color = colors.label, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                TextButton(onClick = onClose, modifier = Modifier.testTag("kanban.dispatcher.done")) { Text("Done", color = colors.accent, fontSize = 16.sp) }
            }
            val running = dispatch is DispatchUi.Running
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { onDispatch(true) },
                    enabled = !running,
                    modifier = Modifier.weight(1f).heightIn(min = 44.dp).testTag("kanban.dispatch.preview"),
                ) { Text("Preview Dispatch", color = colors.label) }
                Button(
                    onClick = { confirmRun = true },
                    enabled = !running,
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = androidx.compose.ui.graphics.Color.White),
                    modifier = Modifier.weight(1f).heightIn(min = 44.dp).testTag("kanban.dispatch.run"),
                ) { Text("Run Dispatcher") }
            }
            Text("Preview is advisory and may become stale. It never starts workers.", color = colors.secondaryLabel, fontSize = 13.sp)
            when (dispatch) {
                DispatchUi.Idle -> Unit
                is DispatchUi.Running -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(strokeWidth = 2.dp, color = colors.secondaryLabel, modifier = Modifier.size(16.dp))
                    Text(if (dispatch.dryRun) "Preview Dispatch" else "Running Dispatcher...", color = colors.secondaryLabel, fontSize = 13.sp)
                }
                is DispatchUi.Failed -> DispatchHeader(if (dispatch.dryRun) "Preview Dispatch" else "Run Dispatcher", null, onDismissResult) {
                    Text("Failed", color = colors.danger, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(dispatch.message, color = colors.secondaryLabel, fontSize = 13.sp)
                }
                is DispatchUi.Finished -> {
                    val result = dispatch.result
                    DispatchHeader(if (result.dryRun) "Preview Dispatch" else "Run Dispatcher", formatDate(result.completedAt), onDismissResult) {
                        Text("Done", color = colors.success, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.testTag("kanban.dispatch.result"))
                        MetricRow("Spawned", result.spawned, "Promoted", result.promoted)
                        MetricRow("Reclaimed", result.reclaimed, "Skipped—No Assignee", result.skippedUnassigned)
                        MetricRow("Skipped—Unknown Profile", result.skippedNonspawnable, "Auto-blocked", result.autoBlocked)
                        MetricRow("Timed Out", result.timedOut, "Crashed", result.crashed)
                        if (result.skippedUnassigned > 0) {
                            Text("The Dispatcher skips Ready Cards without an Assigned Profile.", color = colors.secondaryLabel, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
    if (confirmRun) {
        AlertDialog(
            modifier = Modifier.semantics { testTagsAsResourceId = true },
            onDismissRequest = { confirmRun = false },
            title = { Text("Run Dispatcher") },
            text = { Text("This may start up to 8 workers and consume API budget.") },
            confirmButton = {
                TextButton(onClick = { confirmRun = false; onDispatch(false) }, modifier = Modifier.testTag("kanban.dispatch.confirm")) {
                    Text("Run Dispatcher", color = colors.danger)
                }
            },
            dismissButton = { TextButton(onClick = { confirmRun = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun DispatchHeader(title: String, time: String?, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val colors = HermexTheme.colors
    HorizontalDivider(color = colors.separator.copy(alpha = 0.5f))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = colors.label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        if (time != null) Text("  $time", color = colors.secondaryLabel, fontSize = 12.sp)
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onDismiss) { Text("Dismiss", color = colors.accent, fontSize = 13.sp) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) { content() }
}

@Composable
private fun MetricRow(first: String, firstCount: Int, second: String, secondCount: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Metric(first, firstCount, Modifier.weight(1f))
        Metric(second, secondCount, Modifier.weight(1f))
    }
}

@Composable
private fun Metric(label: String, count: Int, modifier: Modifier = Modifier) {
    val colors = HermexTheme.colors
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = colors.label, fontSize = 12.sp, modifier = Modifier.weight(1f, fill = false))
        Text("$count", color = colors.label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun BlockDialog(onConfirm: (String?) -> Unit, onDismiss: () -> Unit) {
    var reason by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        modifier = Modifier.semantics { testTagsAsResourceId = true },
        onDismissRequest = onDismiss,
        title = { Text("Block") },
        text = {
            OutlinedTextField(
                value = reason,
                onValueChange = { reason = it },
                label = { Text("Reason") },
                modifier = Modifier.fillMaxWidth().testTag("card.block.reason"),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(reason.trim().ifEmpty { null }) }, modifier = Modifier.testTag("card.block.confirm")) { Text("Block") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
