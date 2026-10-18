package com.hermex.android.feature.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hermex.android.core.data.AttachedSession
import com.hermex.android.core.data.ChatRepository
import com.hermex.android.core.data.ModelCatalog
import com.hermex.android.core.data.ModelOption
import com.hermex.android.core.model.ApprovalChoice
import com.hermex.android.core.model.PendingRequest
import com.hermex.android.core.model.ProfileName
import com.hermex.android.core.model.RunState
import com.hermex.android.core.model.RuntimeId
import com.hermex.android.core.model.StoredKey
import com.hermex.android.core.network.GatewayEnvelope
import com.hermex.android.core.network.GatewayEvent
import com.hermex.android.core.network.GatewayStatus
import com.hermex.android.core.network.HermesFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    ) : ChatUiState
}

class ChatViewModel(
    savedState: SavedStateHandle,
    private val repository: ChatRepository,
    private val onSessionsChanged: () -> Unit,
) : ViewModel() {

    private val profile = ProfileName(savedState.get<String>("profile") ?: "default")
    private var storedKey: StoredKey? = savedState.get<String>("key")?.takeIf { it.isNotEmpty() }?.let(::StoredKey)
    private val initialTitle: String? = savedState.get<String>("title")

    private val _state = MutableStateFlow<ChatUiState>(ChatUiState.Loading)
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

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
            chat = ChatState(title = initialTitle, storedKey = key?.value),
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
        try {
            val items = repository.history(key, profile)
            _state.value = base.copy(chat = base.chat.copy(items = items))
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (error: Throwable) {
            _state.value = ChatUiState.Error(error.message ?: "Couldn't load this conversation.")
            return
        }
        attach(reattach = false)
    }

    fun retry() {
        _state.value = ChatUiState.Loading
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
            )
        }
    }

    private fun onGatewayStatus(status: GatewayStatus) {
        when (status) {
            is GatewayStatus.Ready -> {
                val first = seenGeneration == 0
                seenGeneration = status.generation
                if (!first && runtime != null) viewModelScope.launch { attach(reattach = true) }
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
        updateChat { reduce(it, envelope.event, Instant.now()) }
        if (envelope.event is GatewayEvent.RequestCancel) {
            updateReady { it.copy(answering = it.answering - envelope.event.envelopeId) }
        }
    }

    private fun updateChat(transform: (ChatState) -> ChatState) = updateReady { it.copy(chat = transform(it.chat)) }

    private fun updateReady(transform: (ChatUiState.Ready) -> ChatUiState.Ready) {
        _state.update { current -> if (current is ChatUiState.Ready) transform(current) else current }
    }
}
