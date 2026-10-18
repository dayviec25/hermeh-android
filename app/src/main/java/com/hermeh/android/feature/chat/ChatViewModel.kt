package com.hermeh.android.feature.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hermeh.android.core.data.AttachedSession
import com.hermeh.android.core.data.ChatRepository
import com.hermeh.android.core.data.ModelCatalog
import com.hermeh.android.core.data.ModelOption
import com.hermeh.android.core.model.ApprovalChoice
import com.hermeh.android.core.model.Companion
import com.hermeh.android.core.model.DefaultCompanion
import com.hermeh.android.core.model.PendingRequest
import com.hermeh.android.core.model.ProfileName
import com.hermeh.android.core.model.RunState
import com.hermeh.android.core.model.RuntimeId
import com.hermeh.android.core.model.StoredKey
import com.hermeh.android.core.model.TranscriptItem
import com.hermeh.android.core.network.GatewayEnvelope
import com.hermeh.android.core.network.GatewayEvent
import com.hermeh.android.core.model.ConnectionState
import com.hermeh.android.core.network.GatewayStatus
import com.hermeh.android.core.network.HermesFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant

sealed interface ModelPickerState {
    data object Loading : ModelPickerState
    data class Ready(val catalog: ModelCatalog) : ModelPickerState
    data class Error(val message: String) : ModelPickerState
}

data class ForkTarget(val key: String, val profile: String, val title: String?)

sealed interface ChatUiState {
    data object Loading : ChatUiState
    data class Error(val message: String) : ChatUiState
    data class Ready(
        val chat: ChatState,
        val profile: ProfileName,
        val input: String,
        val answering: Set<String>,
        val picker: ModelPickerState?,
        val banner: String?,
        val savedCopy: Boolean = false,
        val refreshing: Boolean = false,
        val forking: Boolean = false,
        val openFork: ForkTarget? = null,
        val connection: ConnectionState = ConnectionState.Online,
    ) : ChatUiState
}

class ChatViewModel(
    savedState: SavedStateHandle,
    private val repository: ChatRepository,
    companion: Flow<Companion>,
    private val onSessionsChanged: () -> Unit,
) : ViewModel() {

    val companion: StateFlow<Companion> = companion.stateIn(viewModelScope, SharingStarted.Eagerly, DefaultCompanion)

    private val profile = ProfileName(savedState.get<String>("profile") ?: "default")
    private var storedKey: StoredKey? = savedState.get<String>("key")?.takeIf { it.isNotEmpty() }?.let(::StoredKey)
    private val initialTitle: String? = savedState.get<String>("title")
    private val initialCwd: String? = savedState.get<String>("cwd")

    private val _state = MutableStateFlow<ChatUiState>(ChatUiState.Loading)
    val state: StateFlow<ChatUiState> = combine(_state, repository.connection) { state, connection ->
        if (state is ChatUiState.Ready) state.copy(connection = connection) else state
    }.stateIn(viewModelScope, SharingStarted.Eagerly, _state.value)

    private var runtime: RuntimeId? = null
    private var lastSeq = 0L
    private var seenGeneration = 0
    private var pendingReasoning: String? = null
    private var pendingModel: ModelOption? = null
    private val attachMutex = Mutex()

    init {
        viewModelScope.launch { repository.events.collect(::onEnvelope) }
        viewModelScope.launch { repository.status.collect(::onGatewayStatus) }
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val key = storedKey
        val base = ChatUiState.Ready(
            chat = ChatState(title = initialTitle, storedKey = key?.value, cwd = initialCwd),
            profile = profile,
            input = "",
            answering = emptySet(),
            picker = null,
            banner = null,
        )
        if (key == null) {
            _state.value = base
            return
        }
        val cached = runCatching { repository.cachedHistory(key) }.getOrElse { if (it is CancellationException) throw it else emptyList() }
        if (cached.isNotEmpty()) {
            _state.value = base.copy(chat = base.chat.copy(items = cached), refreshing = true)
        }
        try {
            val items = repository.history(key, profile)
            val current = _state.value as? ChatUiState.Ready ?: base
            _state.value = current.copy(chat = current.chat.copy(items = items), savedCopy = false, refreshing = false)
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (error: Throwable) {
            if (cached.isNotEmpty()) {
                updateReady { it.copy(savedCopy = true, refreshing = false) }
                return
            }
            _state.value = ChatUiState.Error(error.message ?: "Couldn't load this conversation.")
            return
        }
        attach(reattach = false)
    }

    private fun refreshHistory() {
        val key = storedKey ?: return
        viewModelScope.launch {
            val items = try {
                repository.history(key, profile)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Throwable) {
                return@launch
            }
            updateReady { ready ->
                if (ready.chat.isRunning || ready.chat.pending.isNotEmpty()) ready else ready.copy(chat = ready.chat.copy(items = items), savedCopy = false)
            }
        }
    }

    fun fork(item: TranscriptItem) {
        val ready = _state.value as? ChatUiState.Ready ?: return
        val id = runtime
        val reason = when {
            ready.savedCopy || id == null -> "Reconnect to the server to fork a conversation."
            ready.chat.isRunning -> "Wait for the current response to finish before forking."
            else -> null
        }
        val count = forkCount(ready.chat.items, item)
        if (reason != null || count == null || id == null) {
            updateReady { it.copy(banner = reason ?: "This message can't be forked yet. Try again in a moment.") }
            return
        }
        updateReady { it.copy(forking = true) }
        viewModelScope.launch {
            try {
                val branch = repository.branch(id, count)
                onSessionsChanged()
                updateReady { it.copy(forking = false, openFork = ForkTarget(branch.key.value, profile.value, branch.title)) }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (failure: HermesFailure.Rpc) {
                if (failure.code == 4001) attach(reattach = true)
                updateReady { it.copy(forking = false, banner = failure.message ?: "Hermes refused the fork.") }
            } catch (error: Throwable) {
                updateReady { it.copy(forking = false, banner = error.message ?: "Couldn't fork this conversation.") }
            }
        }
    }

    fun consumeFork() = updateReady { it.copy(openFork = null) }

    fun retry() {
        if ((_state.value as? ChatUiState.Ready)?.savedCopy == true) {
            updateReady { it.copy(refreshing = true) }
        } else {
            _state.value = ChatUiState.Loading
        }
        viewModelScope.launch { load() }
    }

    fun onInputChange(text: String) = updateReady { it.copy(input = text) }

    fun send() {
        val ready = _state.value as? ChatUiState.Ready ?: return
        val text = ready.input.trim()
        if (text.isEmpty()) return
        updateReady {
            val chat = it.chat.addLocalUser(text, Instant.now())
            it.copy(input = "", chat = if (chat.title.isNullOrBlank()) chat.copy(title = text.lineSequence().first().take(80)) else chat)
        }
        viewModelScope.launch {
            try {
                val id = ensureSession()
                val status = repository.submit(id, text)
                if (status == "rejected") updateChat { it.failSend("Hermes didn't accept this message.") }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (failure: HermesFailure.Rpc) {
                if (failure.code == 4001) attach(reattach = true)
                updateChat { it.failSend(failure.message ?: "Hermes refused the message.") }
            } catch (error: Throwable) {
                updateChat { it.failSend(error.message ?: "Couldn't send the message.") }
            }
        }
    }

    fun stop() {
        val id = runtime ?: return
        updateChat { it.requestStop() }
        viewModelScope.launch {
            runCatching { repository.interrupt(id) }.onFailure { error ->
                if (error is CancellationException) throw error
                updateReady { it.copy(banner = error.message ?: "Couldn't stop the response.") }
            }
        }
    }

    fun answerApproval(request: PendingRequest.Approval, choice: ApprovalChoice) {
        val id = runtime ?: return
        updateReady { it.copy(answering = it.answering + request.envelopeId) }
        viewModelScope.launch {
            try {
                val resolved = repository.respondApproval(id, request.requestId, choice)
                if (resolved == 0L) updateChat { it.removePending(request.envelopeId, Instant.now()) }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Throwable) {
                updateReady { it.copy(answering = it.answering - request.envelopeId, banner = error.message ?: "Couldn't answer the approval.") }
            }
        }
    }

    fun answerText(request: PendingRequest, value: String) {
        updateReady { it.copy(answering = it.answering + request.envelopeId) }
        viewModelScope.launch {
            val field = if (request is PendingRequest.Credential) "value" else "answer"
            runCatching { repository.answer(request.envelopeId, field, value) }
                .onSuccess { updateChat { it.removePending(request.envelopeId, Instant.now()) } }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    updateReady { it.copy(answering = it.answering - request.envelopeId, banner = error.message) }
                }
        }
    }

    fun openModelPicker() {
        updateReady { it.copy(picker = ModelPickerState.Loading) }
        viewModelScope.launch {
            try {
                val catalog = repository.modelOptions(runtime, profile)
                updateReady { if (it.picker == null) it else it.copy(picker = ModelPickerState.Ready(catalog)) }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Throwable) {
                updateReady { if (it.picker == null) it else it.copy(picker = ModelPickerState.Error(error.message ?: "Couldn't load models.")) }
            }
        }
    }

    fun dismissModelPicker() = updateReady { it.copy(picker = null) }

    fun selectModel(option: ModelOption) {
        updateReady { it.copy(picker = null) }
        val id = runtime
        if (id == null) {
            pendingModel = option
            updateChat { it.copy(model = option.id, provider = option.provider) }
            return
        }
        viewModelScope.launch {
            try {
                if (repository.setModel(id, profile, option)) {
                    updateChat { it.copy(model = option.id, provider = option.provider) }
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Throwable) {
                updateReady { it.copy(banner = error.message ?: "Couldn't change the model.") }
            }
        }
    }

    fun selectReasoning(level: String) {
        updateChat { it.copy(reasoning = level) }
        val id = runtime
        if (id == null) {
            pendingReasoning = level
            return
        }
        viewModelScope.launch {
            runCatching { repository.setReasoning(id, profile, level) }.onFailure { error ->
                if (error is CancellationException) throw error
                updateReady { it.copy(banner = error.message ?: "Couldn't change reasoning.") }
            }
        }
    }

    fun dismissBanner() = updateReady { it.copy(banner = null) }

    private suspend fun ensureSession(): RuntimeId = attachMutex.withLock {
        runtime?.let { return it }
        val key = storedKey
        val attached = if (key == null) {
            val model = pendingModel
            repository.create(profile, model?.id, model?.provider).also { onSessionsChanged() }
        } else {
            repository.resume(key, profile)
        }
        adopt(attached)
        pendingReasoning?.let { level ->
            runCatching { repository.setReasoning(attached.runtime, profile, level) }
            pendingReasoning = null
        }
        attached.runtime
    }

    private suspend fun attach(reattach: Boolean) {
        val key = storedKey ?: return
        attachMutex.withLock {
            val previous = runtime
            try {
                val attached = repository.resume(key, profile)
                val sameRuntime = previous != null && previous == attached.runtime
                adopt(attached, keepSeq = sameRuntime)
                if (reattach && sameRuntime) {
                    val replay = repository.eventsSince(attached.runtime, lastSeq)
                    replay.events.sortedBy { it.seq ?: 0 }.forEach { applyEnvelope(it) }
                } else if (reattach) {
                    val items = repository.history(key, profile)
                    updateChat { it.copy(items = items) }
                }
                updateChat { chat ->
                    when {
                        attached.running && chat.run == RunState.Idle ->
                            chat.copy(run = RunState.Working(Instant.now(), CAPTION_WORKING), turnSince = Instant.now(), hostRunning = true)
                        attached.running && chat.run == RunState.Reconnecting ->
                            chat.copy(run = RunState.Working(chat.turnSince ?: Instant.now(), CAPTION_WORKING), hostRunning = true)
                        !attached.running && chat.run != RunState.Idle -> chat.finish()
                        else -> chat
                    }
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Throwable) {
                updateReady { it.copy(banner = error.message ?: "Couldn't attach to this conversation.") }
            }
        }
    }

    private fun adopt(attached: AttachedSession, keepSeq: Boolean = false) {
        runtime = attached.runtime
        storedKey = attached.storedKey
        if (!keepSeq) lastSeq = 0
        updateChat {
            it.copy(
                storedKey = attached.storedKey.value,
                model = attached.model ?: it.model,
                provider = attached.provider ?: it.provider,
                title = it.title ?: attached.title,
                cwd = attached.cwd ?: it.cwd,
            )
        }
    }

    private fun onGatewayStatus(status: GatewayStatus) {
        when (status) {
            is GatewayStatus.Ready -> {
                val first = seenGeneration == 0
                seenGeneration = status.generation
                val saved = (_state.value as? ChatUiState.Ready)?.savedCopy == true
                when {
                    saved -> viewModelScope.launch { load() }
                    !first && runtime != null -> viewModelScope.launch { attach(reattach = true) }
                }
            }
            is GatewayStatus.Reconnecting -> {
                if (seenGeneration == 0) seenGeneration = -1
                updateChat { if (it.isRunning) it.copy(run = RunState.Reconnecting, caption = CAPTION_RECONNECTING) else it }
            }
            else -> Unit
        }
    }

    private fun onEnvelope(envelope: GatewayEnvelope) {
        if (envelope.event == GatewayEvent.SessionsChanged) {
            onSessionsChanged()
            return
        }
        val id = runtime ?: return
        if (envelope.sessionId != id.value) return
        applyEnvelope(envelope)
    }

    private fun applyEnvelope(envelope: GatewayEnvelope) {
        val seq = envelope.seq
        if (seq != null) {
            if (seq <= lastSeq) return
            lastSeq = seq
        }
        val wasRunning = (_state.value as? ChatUiState.Ready)?.chat?.isRunning == true
        updateChat { reduce(it, envelope.event, Instant.now()) }
        val nowRunning = (_state.value as? ChatUiState.Ready)?.chat?.isRunning == true
        if (wasRunning && !nowRunning) refreshHistory()
        if (envelope.event is GatewayEvent.RequestCancel) {
            updateReady { it.copy(answering = it.answering - envelope.event.envelopeId) }
        }
    }

    private fun forkCount(items: List<TranscriptItem>, target: TranscriptItem): Int? {
        if (!target.id.startsWith("u-") && !target.id.startsWith("a-")) return null
        var count = 0
        for (item in items) {
            val copied = (item is TranscriptItem.User && item.id.startsWith("u-")) || (item is TranscriptItem.Assistant && item.id.startsWith("a-"))
            if (copied) count += 1
            if (item.id == target.id) return if (copied) count else null
        }
        return null
    }

    private fun updateChat(transform: (ChatState) -> ChatState) = updateReady { it.copy(chat = transform(it.chat)) }

    private fun updateReady(transform: (ChatUiState.Ready) -> ChatUiState.Ready) {
        _state.update { current -> if (current is ChatUiState.Ready) transform(current) else current }
    }
}
