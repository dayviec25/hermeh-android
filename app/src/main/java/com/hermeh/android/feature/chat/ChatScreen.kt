package com.hermeh.android.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.runtime.rememberCoroutineScope
import android.content.ClipData
import kotlinx.coroutines.launch
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hermeh.android.R
import com.hermeh.android.core.data.ModelOption
import com.hermeh.android.core.designsystem.GlassCircleButton
import com.hermeh.android.core.designsystem.AutomationAccessibilityRefresh
import com.hermeh.android.core.designsystem.HermehShapes
import com.hermeh.android.core.designsystem.HermehTheme
import com.hermeh.android.core.designsystem.HermehType
import com.hermeh.android.core.designsystem.Mascot
import com.hermeh.android.core.model.Companion
import com.hermeh.android.core.model.MascotCatalog
import com.hermeh.android.core.model.MascotMood
import com.hermeh.android.core.designsystem.glassSurface
import com.hermeh.android.core.model.ApprovalChoice
import com.hermeh.android.core.model.PendingRequest
import com.hermeh.android.core.model.ConnectionState
import com.hermeh.android.core.model.RunState
import com.hermeh.android.core.model.TranscriptItem
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant

val REASONING_LEVELS = listOf("none", "minimal", "low", "medium", "high", "xhigh", "max", "ultra")

fun reasoningLabel(level: String?): String = when (level) {
    null, "" -> "Default"
    "xhigh" -> "XHigh"
    else -> level.replaceFirstChar { it.uppercase() }
}

@Composable
fun ChatRoute(
    viewModel: ChatViewModel,
    focusComposer: Boolean,
    onBack: () -> Unit,
    onOpenFiles: (String) -> Unit,
    onOpenFork: (ForkTarget) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val companion by viewModel.companion.collectAsStateWithLifecycle()
    AutomationAccessibilityRefresh()
    val fork = (state as? ChatUiState.Ready)?.openFork
    LaunchedEffect(fork) {
        if (fork != null) {
            viewModel.consumeFork()
            onOpenFork(fork)
        }
    }
    ChatScreen(
        state = state,
        companion = companion,
        focusComposer = focusComposer,
        onBack = onBack,
        onOpenFiles = onOpenFiles,
        onFork = viewModel::fork,
        onRetry = viewModel::retry,
        onInput = viewModel::onInputChange,
        onSend = viewModel::send,
        onStop = viewModel::stop,
        onApproval = viewModel::answerApproval,
        onAnswer = viewModel::answerText,
        onOpenModels = viewModel::openModelPicker,
        onDismissModels = viewModel::dismissModelPicker,
        onSelectModel = viewModel::selectModel,
        onReasoning = viewModel::selectReasoning,
        onDismissBanner = viewModel::dismissBanner,
    )
}

@Composable
fun ChatScreen(
    state: ChatUiState,
    companion: Companion,
    focusComposer: Boolean,
    onBack: () -> Unit,
    onOpenFiles: (String) -> Unit,
    onFork: (TranscriptItem) -> Unit,
    onRetry: () -> Unit,
    onInput: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onApproval: (PendingRequest.Approval, ApprovalChoice) -> Unit,
    onAnswer: (PendingRequest, String) -> Unit,
    onOpenModels: () -> Unit,
    onDismissModels: () -> Unit,
    onSelectModel: (ModelOption) -> Unit,
    onReasoning: (String) -> Unit,
    onDismissBanner: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HermehTheme.colors
    Column(
        modifier
            .fillMaxSize()
            .background(colors.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .imePadding(),
    ) {
        val ready = state as? ChatUiState.Ready
        val cwd = ready?.chat?.cwd
        ChatHeader(
            title = ready?.chat?.title?.takeIf { it.isNotBlank() } ?: "New chat",
            subtitle = ready?.profile?.value?.let { "${companion.name} · $it" } ?: companion.name,
            companion = companion,
            mood = ready?.let { headerMood(it.chat.run, it.chat.pending.isNotEmpty(), it.connection) } ?: MascotMood.Idle,
            onBack = onBack,
            onFiles = cwd?.let { path -> { onOpenFiles(path) } },
        )
        when (state) {
            ChatUiState.Loading -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.secondaryLabel, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
            }
            is ChatUiState.Error -> Column(
                Modifier.weight(1f).fillMaxWidth().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(state.message, color = colors.secondaryLabel, fontSize = 14.sp, modifier = Modifier.testTag("chat.error"))
                TextButton(onClick = onRetry) { Text("Try again", color = colors.accent) }
            }
            is ChatUiState.Ready -> {
                if (state.savedCopy) SavedCopyNote(onRetry)
                if (state.chat.items.isEmpty() && !state.refreshing) {
                    EmptyChat(companion, onStarter = onInput, modifier = Modifier.weight(1f))
                } else {
                    Transcript(state.chat.items, companion, onFork, Modifier.weight(1f))
                }
                if (state.forking) ForkingNote()
                state.banner?.let { Banner(it, onDismissBanner) }
                state.chat.pending.forEach { request ->
                    when (request) {
                        is PendingRequest.Approval -> ApprovalCard(request, answering = request.envelopeId in state.answering, onChoice = { onApproval(request, it) })
                        is PendingRequest.Clarify -> TextRequestCard(
                            title = "${companion.name} has a question",
                            prompt = request.question,
                            options = request.options,
                            secure = false,
                            answering = request.envelopeId in state.answering,
                            onAnswer = { onAnswer(request, it) },
                        )
                        is PendingRequest.Credential -> TextRequestCard(
                            title = if (request.kind == "sudo") "Password needed" else "Secret needed",
                            prompt = request.prompt,
                            options = emptyList(),
                            secure = true,
                            answering = request.envelopeId in state.answering,
                            onAnswer = { onAnswer(request, it) },
                        )
                    }
                }
                RunStatusCapsule(state.chat.run, state.connection, companion)
                Composer(
                    state = state,
                    companionName = companion.name,
                    focusOnOpen = focusComposer,
                    onInput = onInput,
                    onSend = onSend,
                    onStop = onStop,
                    onOpenModels = onOpenModels,
                    onReasoning = onReasoning,
                )
                state.picker?.let { ModelPickerSheet(it, state.chat.model, onDismissModels, onSelectModel) }
            }
        }
    }
}

@Composable
private fun ChatHeader(
    title: String,
    subtitle: String,
    companion: Companion,
    mood: MascotMood,
    onBack: () -> Unit,
    onFiles: (() -> Unit)?,
) {
    val colors = HermehTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassCircleButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack, Modifier.testTag("chat.back"))
        Spacer(Modifier.size(12.dp))
        Mascot(
            MascotCatalog.find(companion.mascot),
            colors.mascot,
            mood,
            28.dp,
            Modifier.testTag("chat.mascot"),
            name = companion.name,
            animated = false,
        )
        Column(Modifier.weight(1f).padding(start = 10.dp, end = 14.dp)) {
            Text(
                title,
                color = colors.label,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("chat.title"),
            )
            if (subtitle.isNotEmpty()) {
                Text(subtitle, color = colors.secondaryLabel, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("chat.subtitle"))
            }
        }
        GlassCircleButton(
            painterResource(R.drawable.ic_lucide_folder),
            "Workspace files",
            onClick = { onFiles?.invoke() },
            modifier = Modifier.alpha(if (onFiles != null) 1f else 0.4f).testTag("chat.files"),
        )
    }
}

private sealed interface TranscriptRow {
    val key: String

    data class Entry(val item: TranscriptItem) : TranscriptRow {
        override val key: String get() = item.id
    }

    data class ToolToggle(val groupId: String, val hidden: Int, val expanded: Boolean) : TranscriptRow {
        override val key: String get() = "toggle-$groupId"
    }

    data class TurnHeader(val firstId: String) : TranscriptRow {
        override val key: String get() = "turn-$firstId"
    }
}

private fun transcriptRows(items: List<TranscriptItem>, expanded: Set<String>): List<TranscriptRow> {
    val rows = ArrayList<TranscriptRow>(items.size)
    var index = 0
    while (index < items.size) {
        val item = items[index]
        val previous = items.getOrNull(index - 1)
        if (item !is TranscriptItem.User && (previous == null || previous is TranscriptItem.User)) rows += TranscriptRow.TurnHeader(item.id)
        if (item !is TranscriptItem.Tool) {
            rows += TranscriptRow.Entry(item)
            index += 1
            continue
        }
        var end = index
        while (end + 1 < items.size && items[end + 1] is TranscriptItem.Tool) end += 1
        val run = items.subList(index, end + 1)
        if (run.size >= 2) {
            val groupId = run.first().id
            val open = groupId in expanded
            rows += TranscriptRow.ToolToggle(groupId, run.size - 1, open)
            if (open) run.forEach { rows += TranscriptRow.Entry(it) } else rows += TranscriptRow.Entry(run.last())
        } else {
            rows += TranscriptRow.Entry(item)
        }
        index = end + 1
    }
    return rows
}

@Composable
private fun Transcript(items: List<TranscriptItem>, companion: Companion, onFork: (TranscriptItem) -> Unit, modifier: Modifier) {
    val listState = rememberLazyListState()
    var expanded by rememberSaveable { mutableStateOf(emptySet<String>()) }
    val rows = remember(items, expanded) { transcriptRows(items, expanded).asReversed() }
    LaunchedEffect(items.size) {
        if (listState.firstVisibleItemIndex <= 1) listState.scrollToItem(0)
    }
    LazyColumn(
        state = listState,
        reverseLayout = true,
        modifier = modifier.fillMaxWidth().testTag("chat.transcript"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.Bottom),
    ) {
        items(rows, key = { it.key }, contentType = { row ->
            when (row) {
                is TranscriptRow.Entry -> row.item::class.simpleName
                is TranscriptRow.ToolToggle -> "toggle"
                is TranscriptRow.TurnHeader -> "turnHeader"
            }
        }) { row ->
            when (row) {
                is TranscriptRow.TurnHeader -> TurnHeader(companion)
                is TranscriptRow.ToolToggle -> ToolGroupToggle(row) {
                    expanded = if (row.expanded) expanded - row.groupId else expanded + row.groupId
                }
                is TranscriptRow.Entry -> when (val item = row.item) {
                    is TranscriptItem.User -> MessageMenuHost(item, item.text, onFork) { UserBubble(item) }
                    is TranscriptItem.Assistant -> MessageMenuHost(item, item.markdown, onFork) { AssistantText(item, Modifier.testTag("chat.assistant")) }
                    is TranscriptItem.Thinking -> ThinkingRow(item)
                    is TranscriptItem.Tool -> ToolRow(item)
                    is TranscriptItem.Notice -> NoticeRow(item)
                }
            }
        }
    }
}

@Composable
private fun ToolGroupToggle(row: TranscriptRow.ToolToggle, onToggle: () -> Unit) {
    val colors = HermehTheme.colors
    Row(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onToggle)
            .heightIn(min = 36.dp)
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .testTag("chat.toolGroup.toggle"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(if (row.expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null, tint = colors.secondaryLabel, modifier = Modifier.size(18.dp))
        Text(
            if (row.expanded) "Hide previous ${row.hidden}" else "Show previous ${row.hidden}",
            color = colors.secondaryLabel,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
private fun MessageMenuHost(item: TranscriptItem, text: String, onFork: (TranscriptItem) -> Unit, content: @Composable () -> Unit) {
    val colors = HermehTheme.colors
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    Box(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = {}, onLongClick = { menu = true }, onLongClickLabel = "Message actions")
            .testTag("message.${item.id}"),
    ) {
        content()
        DropdownMenu(
            expanded = menu,
            onDismissRequest = { menu = false },
            containerColor = colors.tertiaryBackground,
            modifier = Modifier.semantics { testTagsAsResourceId = true },
        ) {
            DropdownMenuItem(
                text = { Text("Copy", color = colors.label) },
                leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null, tint = colors.label) },
                onClick = {
                    menu = false
                    scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("message", text))) }
                },
                modifier = Modifier.testTag("message.menu.copy"),
            )
            DropdownMenuItem(
                text = { Text("Fork From Here", color = colors.label) },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.CallSplit, contentDescription = null, tint = colors.label) },
                onClick = {
                    menu = false
                    onFork(item)
                },
                modifier = Modifier.testTag("message.menu.fork"),
            )
        }
    }
}

@Composable
private fun SavedCopyNote(onRetry: () -> Unit) {
    val colors = HermehTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(colors.secondaryBackground)
            .clickable(onClick = onRetry)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag("chat.savedCopy"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Filled.WifiOff, contentDescription = null, tint = colors.secondaryLabel, modifier = Modifier.size(16.dp))
        Text("Showing saved copy.", color = colors.secondaryLabel, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text("Try again", color = colors.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ForkingNote() {
    val colors = HermehTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).testTag("chat.forking"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        CircularProgressIndicator(color = colors.secondaryLabel, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
        Text("Forking conversation…", color = colors.secondaryLabel, fontSize = 13.sp)
    }
}

@Composable
private fun Banner(text: String, onDismiss: () -> Unit) {
    val colors = HermehTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(colors.danger.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag("chat.banner"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = colors.danger, modifier = Modifier.size(16.dp))
        Text(text, color = colors.label, fontSize = 13.sp, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
        Icon(Icons.Filled.Close, contentDescription = "Dismiss", tint = colors.secondaryLabel, modifier = Modifier.size(28.dp).clip(CircleShape).clickable(onClick = onDismiss).padding(6.dp))
    }
}

@Composable
private fun ApprovalCard(request: PendingRequest.Approval, answering: Boolean, onChoice: (ApprovalChoice) -> Unit) {
    val colors = HermehTheme.colors
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(shape)
            .background(colors.approval.copy(alpha = 0.10f))
            .border(1.dp, colors.approval.copy(alpha = 0.35f), shape)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .testTag("approval.card"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = colors.approval, modifier = Modifier.size(16.dp))
            Text("APPROVAL NEEDED", color = colors.approval, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp)
        }
        if (request.command.isNotBlank()) {
            Text(
                request.command,
                color = colors.label,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.codeBackground)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
        if (request.description.isNotBlank()) Text(request.description, color = colors.secondaryLabel, fontSize = 13.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            request.choices.forEach { choice ->
                val primary = choice == ApprovalChoice.Once
                val danger = choice == ApprovalChoice.Deny
                Text(
                    choice.label,
                    color = when {
                        primary -> colors.background
                        danger -> colors.danger
                        else -> colors.label
                    },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(
                            when {
                                primary -> colors.label
                                danger -> colors.danger.copy(alpha = 0.14f)
                                else -> colors.secondaryBackground
                            },
                        )
                        .clickable(enabled = !answering) { onChoice(choice) }
                        .heightIn(min = 40.dp)
                        .padding(horizontal = 18.dp, vertical = 10.dp)
                        .testTag("approval.${choice.wire}"),
                )
            }
        }
    }
}

@Composable
private fun TextRequestCard(
    title: String,
    prompt: String,
    options: List<String>,
    secure: Boolean,
    answering: Boolean,
    onAnswer: (String) -> Unit,
) {
    val colors = HermehTheme.colors
    var text by rememberSaveable { mutableStateOf("") }
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(shape)
            .background(colors.input.copy(alpha = 0.10f))
            .border(1.dp, colors.input.copy(alpha = 0.35f), shape)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title.uppercase(), color = colors.input, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Text(prompt, color = colors.label, fontSize = 14.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                Text(
                    option,
                    color = colors.label,
                    fontSize = 14.sp,
                    modifier = Modifier.clip(CircleShape).background(colors.secondaryBackground).clickable(enabled = !answering) { onAnswer(option) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                textStyle = TextStyle(color = colors.label, fontSize = 15.sp),
                cursorBrush = SolidColor(colors.label),
                visualTransformation = if (secure) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(colors.secondaryBackground).padding(10.dp),
            )
            TextButton(onClick = { onAnswer(text) }, enabled = !answering) { Text(if (secure && text.isEmpty()) "Skip" else "Send", color = colors.accent) }
        }
    }
}

@Composable
private fun RunStatusCapsule(run: RunState, connection: ConnectionState, companion: Companion) {
    val colors = HermehTheme.colors
    val working = "${companion.name} is working"
    val (caption, since, mood) = when (connection) {
        ConnectionState.WaitingForNetwork -> Triple(CAPTION_WAITING_NETWORK, null, MascotMood.Offline)
        ConnectionState.Reconnecting -> Triple(CAPTION_RECONNECTING, null, MascotMood.Offline)
        is ConnectionState.Failed -> Triple(CAPTION_DISCONNECTED, null, MascotMood.Offline)
        ConnectionState.Online -> when (run) {
            RunState.Idle -> return
            is RunState.Working -> Triple(if (run.caption == CAPTION_WORKING) working else run.caption, run.since, MascotMood.Working)
            RunState.WaitingForYou -> Triple(CAPTION_WAITING, null, MascotMood.WaitingForYou)
            RunState.Stopping -> Triple(CAPTION_STOPPING, null, MascotMood.Working)
            RunState.Reconnecting -> Triple(CAPTION_RECONNECTING, null, MascotMood.Offline)
        }
    }
    val offline = connection != ConnectionState.Online
    val spoken = if (!offline && run is RunState.Working) working else caption
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(run) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val firstSince = remember(run is RunState.Idle) { since ?: Instant.now() }
    val start = since ?: firstSince
    val elapsed = Duration.ofMillis((now - start.toEpochMilli()).coerceAtLeast(0))
    val timer = "%d:%02d".format(elapsed.toMinutes(), elapsed.seconds % 60)
    Box(Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
        Row(
            Modifier
                .glassSurface(CircleShape)
                .padding(start = 7.dp, end = 12.dp, top = 5.dp, bottom = 5.dp)
                .testTag("chat.runStatus")
                .clearAndSetSemantics { contentDescription = spoken },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Mascot(MascotCatalog.find(companion.mascot), colors.mascot, mood, 20.dp, name = companion.name, animated = false)
            if (!offline) Text(timer, color = colors.secondaryLabel, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
            Text(caption, color = colors.label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 240.dp))
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun Composer(
    state: ChatUiState.Ready,
    companionName: String,
    focusOnOpen: Boolean,
    onInput: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onOpenModels: () -> Unit,
    onReasoning: (String) -> Unit,
) {
    val colors = HermehTheme.colors
    val running = state.chat.isRunning
    val canSend = state.input.isNotBlank()
    val focus = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var focusedOnOpen by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (focusOnOpen && !focusedOnOpen) {
            focusedOnOpen = true
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }
    var field by remember { mutableStateOf(TextFieldValue(state.input, TextRange(state.input.length))) }
    if (field.text != state.input) field = TextFieldValue(state.input, TextRange(state.input.length))
    val submit = {
        focus.clearFocus()
        onSend()
    }
    var reasoningMenu by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .glassSurface(HermehShapes.composer, elevated = true)
                .padding(start = 18.dp, end = 10.dp, top = 14.dp, bottom = 10.dp),
        ) {
            BasicTextField(
                value = field,
                onValueChange = {
                    field = it
                    if (it.text != state.input) onInput(it.text)
                },
                textStyle = TextStyle(color = colors.label, fontSize = 16.sp, lineHeight = 22.sp),
                cursorBrush = SolidColor(colors.label),
                maxLines = 6,
                modifier = Modifier.fillMaxWidth().heightIn(min = 24.dp).padding(end = 8.dp).focusRequester(focusRequester).testTag("chat.input"),
                decorationBox = { inner ->
                    Box {
                        if (state.input.isEmpty()) Text("Message $companionName…", color = colors.tertiaryLabel, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        inner()
                    }
                },
            )
            Spacer(Modifier.size(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Add, contentDescription = "Attach", tint = colors.secondaryLabel, modifier = Modifier.size(36.dp).padding(6.dp))
                Chip(
                    text = state.chat.model?.takeIf { it.isNotBlank() } ?: "Model",
                    onClick = onOpenModels,
                    modifier = Modifier.widthIn(max = 150.dp).testTag("chat.modelChip"),
                )
                Box {
                    Chip(
                        text = reasoningLabel(state.chat.reasoning),
                        leading = { Icon(painterResource(R.drawable.ic_lucide_brain), contentDescription = null, tint = colors.secondaryLabel, modifier = Modifier.size(14.dp)) },
                        onClick = { reasoningMenu = true },
                        modifier = Modifier.testTag("chat.reasoningChip"),
                    )
                    DropdownMenu(
                        expanded = reasoningMenu,
                        onDismissRequest = { reasoningMenu = false },
                        modifier = Modifier.semantics { testTagsAsResourceId = true },
                    ) {
                        REASONING_LEVELS.forEach { level ->
                            DropdownMenuItem(
                                text = { Text(reasoningLabel(level)) },
                                onClick = {
                                    reasoningMenu = false
                                    onReasoning(level)
                                },
                                modifier = Modifier.testTag("reasoning.$level"),
                            )
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                Icon(Icons.Filled.Mic, contentDescription = "Voice input", tint = colors.secondaryLabel, modifier = Modifier.size(36.dp).padding(7.dp))
                Spacer(Modifier.size(4.dp))
                if (running && !canSend) {
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(colors.danger.copy(alpha = if (colors.isDark) 0.22f else 0.14f))
                            .clickable(onClick = onStop)
                            .testTag("chat.stop"),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Stop, contentDescription = "Stop", tint = colors.danger, modifier = Modifier.size(22.dp))
                    }
                } else {
                    val fill = when {
                        canSend -> colors.ink
                        colors.isDark -> colors.label.copy(alpha = 0.18f)
                        else -> colors.label.copy(alpha = 0.12f)
                    }
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(fill)
                            .clickable(enabled = canSend, onClick = submit)
                            .testTag("chat.send"),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.ArrowUpward,
                            contentDescription = "Send",
                            tint = if (canSend) colors.onInk else colors.secondaryLabel,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FooterChip(Icons.Outlined.AccountCircle, state.profile.value.replaceFirstChar { it.uppercase() })
        }
    }
}

@Composable
private fun Chip(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, leading: (@Composable () -> Unit)? = null) {
    val colors = HermehTheme.colors
    Row(
        modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .heightIn(min = 36.dp)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        leading?.invoke()
        Text(text, color = colors.secondaryLabel, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        Icon(Icons.Filled.ExpandMore, contentDescription = null, tint = colors.secondaryLabel, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun FooterChip(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    val colors = HermehTheme.colors
    Row(
        Modifier.glassSurface(CircleShape).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = colors.secondaryLabel, modifier = Modifier.size(16.dp))
        Text(text, color = colors.secondaryLabel, fontSize = 14.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
private fun ModelPickerSheet(
    picker: ModelPickerState,
    current: String?,
    onDismiss: () -> Unit,
    onSelect: (ModelOption) -> Unit,
) {
    val colors = HermehTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = colors.secondaryBackground) {
        Column(Modifier.fillMaxWidth().semantics { testTagsAsResourceId = true }.testTag("chat.modelPicker")) {
            Text("Model", color = colors.label, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            when (picker) {
                ModelPickerState.Loading -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(strokeWidth = 2.dp, color = colors.secondaryLabel, modifier = Modifier.size(22.dp))
                }
                is ModelPickerState.Error -> Text(picker.message, color = colors.danger, modifier = Modifier.padding(24.dp))
                is ModelPickerState.Ready -> LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(bottom = 24.dp)) {
                    picker.catalog.groups.forEach { (name, models) ->
                        item(key = "h-$name", contentType = "header") {
                            Text(name, color = colors.secondaryLabel, fontSize = 13.sp, modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 14.dp, bottom = 4.dp))
                        }
                        items(models, key = { "${it.provider}/${it.id}" }, contentType = { "model" }) { option ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelect(option) }
                                    .heightIn(min = 48.dp)
                                    .padding(horizontal = 24.dp, vertical = 10.dp)
                                    .testTag("model.option.${option.id}"),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(option.id, color = colors.label, fontSize = 16.sp, modifier = Modifier.weight(1f))
                                if (option.id == current) Text("Current", color = colors.secondaryLabel, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

private val STARTERS = listOf("Check on my server", "Summarize my recent sessions", "Plan a scheduled task")

private fun headerMood(run: RunState, pending: Boolean, connection: ConnectionState): MascotMood = when {
    connection != ConnectionState.Online -> MascotMood.Offline
    pending -> MascotMood.WaitingForYou
    else -> when (run) {
        RunState.Idle -> MascotMood.Idle
        is RunState.Working -> MascotMood.Working
        RunState.WaitingForYou -> MascotMood.WaitingForYou
        RunState.Stopping, RunState.Reconnecting -> MascotMood.Working
    }
}

@Composable
private fun EmptyChat(companion: Companion, onStarter: (String) -> Unit, modifier: Modifier = Modifier) {
    val colors = HermehTheme.colors
    BoxWithConstraints(modifier.fillMaxWidth().testTag("chat.empty")) {
        val width = maxWidth
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Mascot(MascotCatalog.find(companion.mascot), colors.mascot, MascotMood.Idle, 120.dp, name = companion.name)
            Spacer(Modifier.size(10.dp))
            Text(
                "What should we work on?",
                color = colors.label,
                style = HermehType.headline,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Spacer(Modifier.size(16.dp))
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .widthIn(min = width)
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            ) {
                STARTERS.forEachIndexed { index, prompt ->
                    Text(
                        prompt,
                        color = colors.label,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        modifier = Modifier
                            .clip(HermehShapes.capsule)
                            .background(colors.secondaryBackground)
                            .border(1.dp, colors.separator, HermehShapes.capsule)
                            .clickable { onStarter(prompt) }
                            .heightIn(min = 44.dp)
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .testTag("chat.starter.$index"),
                    )
                }
            }
        }
    }
}
