package com.hermex.android.feature.kanban

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hermex.android.core.designsystem.GlassCircleButton
import com.hermex.android.core.designsystem.HermexTheme
import com.hermex.android.core.model.CardDetail
import com.hermex.android.core.model.KanbanStatus
import com.hermex.android.core.model.WorkerLog
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography

@Composable
fun CardDetailRoute(
    viewModel: CardDetailViewModel,
    profiles: List<String>,
    onClose: (notice: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val closedWith = (state as? CardDetailUiState.Ready)?.closedWith
    LaunchedEffect(closedWith) {
        if (closedWith != null) onClose(closedWith)
    }
    var blocking by remember { mutableStateOf(false) }
    CardDetailScreen(
        state = state,
        onBack = { onClose(null) },
        onRetry = viewModel::retry,
        onRefresh = viewModel::refresh,
        onEdit = viewModel::openEditor,
        onAction = { action ->
            when (action) {
                is CardAction.Move -> viewModel.move(action.to)
                CardAction.Block -> blocking = true
                CardAction.Unblock -> viewModel.unblock()
                CardAction.Complete -> viewModel.complete()
                CardAction.Archive -> viewModel.archive()
            }
        },
        onCommentChange = viewModel::onCommentChange,
        onSendComment = viewModel::sendComment,
        onDismissNotice = viewModel::dismissNotice,
        modifier = modifier,
    )
    if (blocking) {
        BlockDialog(onConfirm = { blocking = false; viewModel.block(it) }, onDismiss = { blocking = false })
    }
    val editor = (state as? CardDetailUiState.Ready)?.editor
    if (editor != null) {
        CardEditorDialog(editor = editor, profiles = profiles, onSave = viewModel::save, onDismiss = viewModel::closeEditor)
    }
}

@Composable
fun CardDetailScreen(
    state: CardDetailUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
    onEdit: () -> Unit,
    onAction: (CardAction) -> Unit,
    onCommentChange: (String) -> Unit,
    onSendComment: () -> Unit,
    onDismissNotice: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HermexTheme.colors
    val ready = state as? CardDetailUiState.Ready
    Column(modifier.fillMaxSize().background(colors.background).windowInsetsPadding(WindowInsets.statusBars).imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            GlassCircleButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack, Modifier.testTag("card.back"))
            Text(
                ready?.detail?.card?.title ?: "Loading",
                color = colors.label,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp).testTag("card.title"),
            )
            if (ready != null) {
                val card = ready.detail.card
                GlassPill {
                    PillIconButton(Icons.Outlined.Edit, "Edit", onEdit, Modifier.testTag("card.edit"), enabled = !ready.busy)
                    MoveMenu(card.moveDestinations, enabled = !ready.busy, onMove = { onAction(CardAction.Move(it)) })
                    MoreMenu(card.actions().filter { it !is CardAction.Move }, enabled = !ready.busy, onAction = onAction)
                }
            }
        }
        when (state) {
            CardDetailUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.secondaryLabel, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
            }
            CardDetailUiState.Missing -> Unavailable("No Content", "This Card no longer exists on this Board. The Board has been refreshed.", null)
            is CardDetailUiState.Error -> Unavailable("Error", state.message, onRetry)
            is CardDetailUiState.Ready -> {
                if (state.busy) {
                    Text("Updating task...", color = colors.secondaryLabel, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 32.dp, vertical = 4.dp))
                }
                state.notice?.let { NoticeBanner(it, onDismissNotice) }
                PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
                    DetailList(state, onCommentChange, onSendComment)
                }
            }
        }
    }
}

@Composable
private fun DetailList(state: CardDetailUiState.Ready, onCommentChange: (String) -> Unit, onSendComment: () -> Unit) {
    val colors = HermexTheme.colors
    val detail = state.detail
    val card = detail.card
    LazyColumn(Modifier.fillMaxSize().testTag("card.detail"), contentPadding = PaddingValues(top = 4.dp, bottom = 40.dp)) {
        item(key = "description", contentType = "section") {
            SectionCard("Description") {
                if (card.body.isBlank()) {
                    Text("No Content", color = colors.secondaryLabel, fontSize = 15.sp)
                } else {
                    KanbanMarkdown(card.body, Modifier.testTag("card.description"))
                }
            }
        }
        item(key = "metadata", contentType = "section") { Metadata(detail) }
        item(key = "comments", contentType = "section") {
            SectionCard(countLabel(detail.comments.size, "Comment")) {
                if (detail.comments.isEmpty()) {
                    Text("No comments yet", color = colors.secondaryLabel, fontSize = 15.sp, modifier = Modifier.testTag("card.comments.empty"))
                }
                detail.comments.forEach { comment ->
                    Column(Modifier.fillMaxWidth().testTag("card.comment.${comment.id}"), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        KanbanMarkdown(comment.body)
                        Text(
                            listOfNotNull(comment.author, formatDate(comment.createdAt)).joinToString("  ·  "),
                            color = colors.secondaryLabel,
                            fontSize = 12.sp,
                        )
                    }
                }
                CommentComposer(state, onCommentChange, onSendComment)
            }
        }
        item(key = "dependencies", contentType = "section") {
            SectionCard("Dependencies") {
                DependencyGroup(countLabel(detail.prerequisites.size, "Prerequisite"), detail.prerequisites)
                DependencyGroup(countLabel(detail.dependents.size, "Dependent"), detail.dependents)
            }
        }
        item(key = "log", contentType = "section") { WorkerLogSection(state.log) }
        if (detail.runs.isNotEmpty()) {
            item(key = "runs", contentType = "section") {
                SectionCard("Operational History") {
                    detail.runs.forEach { run ->
                        LabeledValue(run.id, listOfNotNull(run.status.ifBlank { null }, run.profile, formatDate(run.startedAt)).joinToString(" · "), mono = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun Metadata(detail: CardDetail) {
    val card = detail.card
    SectionCard("Metadata") {
        LabeledValue("Card ID", card.id.value, mono = true)
        LabeledValue("Status", card.status?.title ?: "Unknown Status", Modifier.testTag("card.status"))
        LabeledValue("Profile", card.assignee ?: "Unassigned")
        card.tenant?.let { LabeledValue("Tenant", it) }
        LabeledValue("Priority", "P${card.priority}")
        card.workspaceKind?.let { LabeledValue("Workspace", it.title) }
        formatDate(card.createdAt)?.let { LabeledValue("Created", it) }
        formatDate(card.updatedAt)?.let { LabeledValue("Updated", it) }
        if (card.skills.isNotEmpty()) LabeledValue("Skills", card.skills.joinToString(", "))
        card.maxRuntimeSeconds?.let { LabeledValue("Maximum Runtime", fullDuration(it)) }
        card.currentRunId?.let { LabeledValue("Run ID", it, mono = true) }
        if (card.status == KanbanStatus.Blocked) card.blockReason?.let { LabeledValue("Block Reason", it) }
    }
}

@Composable
private fun CommentComposer(state: CardDetailUiState.Ready, onCommentChange: (String) -> Unit, onSendComment: () -> Unit) {
    val colors = HermexTheme.colors
    val sending = state.commentPhase is CommentPhase.Sending
    OutlinedTextField(
        value = state.comment,
        onValueChange = onCommentChange,
        placeholder = { Text("Comment") },
        enabled = !sending,
        minLines = 2,
        maxLines = 6,
        shape = RoundedCornerShape(10.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = colors.accent,
            unfocusedBorderColor = colors.separator,
            cursorColor = colors.accent,
            focusedTextColor = colors.label,
            unfocusedTextColor = colors.label,
            unfocusedContainerColor = colors.tertiaryBackground,
            focusedContainerColor = colors.tertiaryBackground,
        ),
        modifier = Modifier.fillMaxWidth().testTag("card.comment.input"),
    )
    Button(
        onClick = onSendComment,
        enabled = !sending && state.comment.isNotBlank(),
        colors = ButtonDefaults.buttonColors(containerColor = colors.label, contentColor = colors.background),
        modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp).testTag("card.comment.send"),
    ) {
        if (sending) {
            CircularProgressIndicator(strokeWidth = 2.dp, color = colors.background, modifier = Modifier.size(18.dp))
        } else {
            Text(if (state.commentPhase is CommentPhase.Failed) "Try Again" else "Send", fontWeight = FontWeight.SemiBold)
        }
    }
    when (val phase = state.commentPhase) {
        CommentPhase.Added -> Text("Added", color = colors.success, fontSize = 13.sp, modifier = Modifier.testTag("card.comment.added"))
        is CommentPhase.Failed -> Text(phase.message, color = colors.danger, fontSize = 13.sp)
        CommentPhase.Idle, CommentPhase.Sending -> Unit
    }
    Text("Comments cannot be edited or deleted after sending.", color = colors.secondaryLabel, fontSize = 12.sp)
}

@Composable
private fun DependencyGroup(title: String, ids: List<String>) {
    val colors = HermexTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, color = colors.label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        ids.forEach { Text(it, color = colors.secondaryLabel, fontSize = 14.sp, fontFamily = FontFamily.Monospace) }
    }
}

@Composable
private fun WorkerLogSection(log: WorkerLog?) {
    val colors = HermexTheme.colors
    SectionCard("Worker Log") {
        if (log == null || !log.exists || log.content.isBlank()) {
            Text("No Content", color = colors.secondaryLabel, fontSize = 15.sp, modifier = Modifier.testTag("card.log.empty"))
        } else {
            if (log.truncated) Text("Only the last part of this log is shown.", color = colors.secondaryLabel, fontSize = 12.sp)
            Text(
                log.content,
                color = colors.label,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.codeBackground)
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp)
                    .testTag("card.log"),
            )
        }
    }
}

@Composable
private fun MoveMenu(destinations: List<KanbanStatus>, enabled: Boolean, onMove: (KanbanStatus) -> Unit) {
    val colors = HermexTheme.colors
    var open by remember { mutableStateOf(false) }
    Box {
        val active = enabled && destinations.isNotEmpty()
        TextButton(onClick = { open = true }, enabled = active, modifier = Modifier.heightIn(min = 44.dp).testTag("card.move")) {
            Text("Move", color = if (active) colors.label else colors.tertiaryLabel, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.semantics { testTagsAsResourceId = true }) {
            destinations.forEach { status ->
                DropdownMenuItem(
                    text = { Text(status.title) },
                    onClick = { open = false; onMove(status) },
                    modifier = Modifier.testTag("card.move.${status.wire}"),
                )
            }
        }
    }
}

@Composable
private fun MoreMenu(actions: List<CardAction>, enabled: Boolean, onAction: (CardAction) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        PillIconButton(Icons.Outlined.MoreHoriz, "Card Actions", { open = true }, Modifier.testTag("card.menu"), enabled = enabled && actions.isNotEmpty())
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.semantics { testTagsAsResourceId = true }) {
            actions.forEach { action ->
                DropdownMenuItem(
                    text = { Text(action.label(), color = if (action == CardAction.Archive) HermexTheme.colors.danger else HermexTheme.colors.label) },
                    onClick = { open = false; onAction(action) },
                    modifier = Modifier.testTag(action.tag()),
                )
            }
        }
    }
}

@Composable
internal fun KanbanMarkdown(content: String, modifier: Modifier = Modifier) {
    val colors = HermexTheme.colors
    val body = TextStyle(color = colors.label, fontSize = 15.sp, lineHeight = 21.sp)
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
            h1 = body.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold, lineHeight = 26.sp),
            h2 = body.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold),
            h3 = body.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
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
            table = body.copy(fontSize = 14.sp),
        ),
    )
}
