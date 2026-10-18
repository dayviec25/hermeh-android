package com.hermex.android.feature.chat

import com.hermex.android.core.model.NoticeKind
import com.hermex.android.core.model.PendingRequest
import com.hermex.android.core.model.RunState
import com.hermex.android.core.model.ToolStatus
import com.hermex.android.core.model.TranscriptItem
import com.hermex.android.core.network.GatewayEvent
import java.time.Instant

enum class TurnEnding { Completed, Interrupted, Failed }

data class ChatState(
    val items: List<TranscriptItem> = emptyList(),
    val pending: List<PendingRequest> = emptyList(),
    val run: RunState = RunState.Idle,
    val turnSince: Instant? = null,
    val caption: String = CAPTION_WORKING,
    val hostRunning: Boolean = false,
    val stopRequested: Boolean = false,
    val ending: TurnEnding? = null,
    val title: String? = null,
    val model: String? = null,
    val provider: String? = null,
    val reasoning: String? = null,
    val storedKey: String? = null,
    val cwd: String? = null,
    val nextLocalId: Long = 0,
) {
    val isRunning: Boolean get() = run != RunState.Idle && run != RunState.Reconnecting || hostRunning
}

const val CAPTION_WORKING = "Hermes is working"
const val CAPTION_STARTING = "Starting response"
const val CAPTION_RECONNECTING = "Reconnecting stream"
const val CAPTION_WAITING = "Waiting for you"
const val CAPTION_STOPPING = "Stopping response"

fun reduce(state: ChatState, event: GatewayEvent, now: Instant): ChatState = when (event) {
    GatewayEvent.MessageStart -> state.beginTurn(now, CAPTION_STARTING).copy(ending = null)
    is GatewayEvent.MessageDelta -> if (event.text.isEmpty()) state else state.appendAnswer(event.text).working(now)
    is GatewayEvent.MessageInterim -> state.interim(event.text, event.alreadyStreamed).working(now)
    is GatewayEvent.ThinkingDelta -> {
        val caption = event.text.trim()
        if (caption.isEmpty()) state else state.copy(caption = caption).let {
            if (it.run is RunState.Working) it.copy(run = RunState.Working(it.turnSince ?: now, caption)) else it
        }
    }
    is GatewayEvent.ReasoningDelta -> if (event.text.isEmpty()) state else state.appendReasoning(event.text)
    is GatewayEvent.ToolStart -> state.settleStreaming().let { s ->
        val id = "t-${event.toolId.ifEmpty { "local-${s.nextLocalId}" }}"
        s.copy(
            items = s.items.filterNot { it.id == id } + TranscriptItem.Tool(
                id = id,
                name = event.name,
                context = event.context,
                argsJson = event.argsJson,
                status = ToolStatus.Running,
                output = null,
            ),
            nextLocalId = s.nextLocalId + 1,
        ).working(now)
    }
    is GatewayEvent.ToolComplete -> state.copy(items = state.items.map { item ->
        if (item is TranscriptItem.Tool && item.id == "t-${event.toolId}") {
            item.copy(
                status = event.error?.let { ToolStatus.Failed(it) } ?: ToolStatus.Done(event.durationSeconds),
                output = event.output ?: item.output,
            )
        } else {
            item
        }
    })
    is GatewayEvent.ServerRequest -> state.copy(
        pending = state.pending.filterNot { it.envelopeId == event.request.envelopeId } + event.request,
        run = RunState.WaitingForYou,
        turnSince = state.turnSince ?: now,
    )
    is GatewayEvent.RequestCancel -> state.withdraw(event.envelopeId, now)
    is GatewayEvent.MessageComplete -> state.complete(event).let { if (!it.hostRunning) it.finish() else it }
    is GatewayEvent.SessionInfo -> state.info(event, now)
    is GatewayEvent.SessionTitle -> if (event.title.isBlank() || (event.storedKey != null && state.storedKey != null && event.storedKey != state.storedKey)) {
        state
    } else {
        state.copy(title = event.title)
    }
    is GatewayEvent.Error -> {
        val noticed = state.settleStreaming().addNotice(event.message, NoticeKind.Error)
        if (state.run == RunState.Idle && !state.hostRunning) noticed else noticed.copy(ending = TurnEnding.Failed)
    }
    GatewayEvent.SessionsChanged -> state
    is GatewayEvent.Unknown -> state
}

fun ChatState.addLocalUser(text: String, now: Instant): ChatState = copy(
    items = items + TranscriptItem.User("local-$nextLocalId", text, steer = false),
    nextLocalId = nextLocalId + 1,
    ending = null,
    stopRequested = false,
).beginTurn(now, CAPTION_STARTING)

fun ChatState.requestStop(): ChatState = copy(stopRequested = true, run = RunState.Stopping, pending = emptyList())

fun ChatState.removePending(envelopeId: String, now: Instant): ChatState = withdraw(envelopeId, now)

fun ChatState.failSend(message: String): ChatState = addNotice(message, NoticeKind.Error).finish()

fun ChatState.finish(): ChatState {
    val interrupted = stopRequested || ending == TurnEnding.Interrupted
    val items = items.map { item ->
        when {
            item is TranscriptItem.Assistant && item.streaming -> item.copy(streaming = false)
            item is TranscriptItem.Tool && item.status == ToolStatus.Running ->
                item.copy(status = if (interrupted) ToolStatus.Interrupted else item.status)
            else -> item
        }
    }
    return copy(
        items = items,
        run = RunState.Idle,
        pending = emptyList(),
        turnSince = null,
        stopRequested = false,
        hostRunning = false,
        caption = CAPTION_WORKING,
    )
}

private fun ChatState.beginTurn(now: Instant, caption: String): ChatState {
    val since = turnSince ?: now
    return copy(run = RunState.Working(since, caption), turnSince = since, caption = caption)
}

private fun ChatState.working(now: Instant): ChatState = when (run) {
    is RunState.Working -> if (run.caption == CAPTION_STARTING) copy(run = RunState.Working(run.since, CAPTION_WORKING), caption = CAPTION_WORKING) else this
    RunState.Idle, RunState.Reconnecting -> beginTurn(now, CAPTION_WORKING)
    RunState.WaitingForYou, RunState.Stopping -> this
}

private fun ChatState.appendAnswer(text: String): ChatState {
    val last = items.lastOrNull()
    return if (last is TranscriptItem.Assistant && last.streaming) {
        copy(items = items.dropLast(1) + last.copy(markdown = last.markdown + text))
    } else {
        copy(items = items + TranscriptItem.Assistant("s-$nextLocalId", text, streaming = true), nextLocalId = nextLocalId + 1)
    }
}

private fun ChatState.appendReasoning(text: String): ChatState {
    val last = items.lastOrNull()
    return if (last is TranscriptItem.Thinking) {
        copy(items = items.dropLast(1) + last.copy(text = last.text + text))
    } else {
        copy(items = items + TranscriptItem.Thinking("th-$nextLocalId", text), nextLocalId = nextLocalId + 1)
    }
}

private fun ChatState.interim(text: String, alreadyStreamed: Boolean): ChatState {
    val last = items.lastOrNull()
    if (last is TranscriptItem.Assistant && last.streaming) {
        val markdown = if (alreadyStreamed || text.isBlank()) last.markdown else text
        return copy(items = items.dropLast(1) + last.copy(markdown = markdown, streaming = false))
    }
    if (text.isBlank()) return this
    return copy(items = items + TranscriptItem.Assistant("i-$nextLocalId", text, streaming = false), nextLocalId = nextLocalId + 1)
}

private fun ChatState.settleStreaming(): ChatState {
    val last = items.lastOrNull()
    return if (last is TranscriptItem.Assistant && last.streaming) copy(items = items.dropLast(1) + last.copy(streaming = false)) else this
}

private fun ChatState.withdraw(envelopeId: String, now: Instant): ChatState {
    val remaining = pending.filterNot { it.envelopeId == envelopeId }
    val run = when {
        remaining.isNotEmpty() -> RunState.WaitingForYou
        run == RunState.WaitingForYou -> RunState.Working(turnSince ?: now, CAPTION_WORKING)
        else -> run
    }
    return copy(pending = remaining, run = run)
}

private fun ChatState.complete(event: GatewayEvent.MessageComplete): ChatState {
    val ending = when (event.status) {
        "interrupted" -> TurnEnding.Interrupted
        "error" -> TurnEnding.Failed
        else -> TurnEnding.Completed
    }
    val showText = event.text.isNotBlank() && (ending != TurnEnding.Failed || event.partial)
    val last = items.lastOrNull()
    var next = when {
        last is TranscriptItem.Assistant && last.streaming ->
            copy(items = items.dropLast(1) + last.copy(markdown = if (showText) event.text else last.markdown, streaming = false))
        showText && !(last is TranscriptItem.Assistant && last.markdown.trim() == event.text.trim()) ->
            copy(items = items + TranscriptItem.Assistant("c-$nextLocalId", event.text, streaming = false), nextLocalId = nextLocalId + 1)
        else -> this
    }
    next = when (ending) {
        TurnEnding.Interrupted -> next.addNotice("Stopped", NoticeKind.Interrupted)
        TurnEnding.Failed -> if (showText) next else next.addNotice("The response failed.", NoticeKind.Error)
        TurnEnding.Completed -> next
    }
    return next.copy(ending = ending)
}

private fun ChatState.info(event: GatewayEvent.SessionInfo, now: Instant): ChatState {
    var next = copy(
        model = event.model?.takeIf { it.isNotEmpty() } ?: model,
        provider = event.provider?.takeIf { it.isNotEmpty() } ?: provider,
        reasoning = event.reasoningEffort ?: reasoning,
        title = event.title?.takeIf { it.isNotBlank() } ?: title,
        storedKey = event.storedKey ?: storedKey,
        cwd = event.cwd?.takeIf { it.isNotBlank() } ?: cwd,
    )
    val running = event.running ?: return next
    next = next.copy(hostRunning = running)
    return if (running) {
        if (next.run == RunState.Idle || next.run == RunState.Reconnecting) {
            val since = event.turnStartedAt?.let { Instant.ofEpochMilli((it * 1000).toLong()) } ?: now
            next.copy(run = RunState.Working(since, CAPTION_WORKING), turnSince = since)
        } else {
            next
        }
    } else if (next.run != RunState.Idle) {
        next.finish()
    } else {
        next
    }
}

private fun ChatState.addNotice(text: String, kind: NoticeKind): ChatState = copy(
    items = items + TranscriptItem.Notice("n-$nextLocalId", text, kind),
    nextLocalId = nextLocalId + 1,
)
