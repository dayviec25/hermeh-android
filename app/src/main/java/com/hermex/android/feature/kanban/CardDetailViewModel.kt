package com.hermex.android.feature.kanban

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hermex.android.core.data.KanbanRepository
import com.hermex.android.core.model.CardDetail
import com.hermex.android.core.model.CardDraft
import com.hermex.android.core.model.CardId
import com.hermex.android.core.model.KanbanCard
import com.hermex.android.core.model.KanbanStatus
import com.hermex.android.core.model.WorkerLog
import com.hermex.android.core.network.KanbanFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface CommentPhase {
    data object Idle : CommentPhase
    data object Sending : CommentPhase
    data object Added : CommentPhase
    data class Failed(val message: String) : CommentPhase
}

sealed interface CardDetailUiState {
    data object Loading : CardDetailUiState
    data object Missing : CardDetailUiState
    data class Error(val message: String) : CardDetailUiState
    data class Ready(
        val detail: CardDetail,
        val log: WorkerLog?,
        val comment: String,
        val commentPhase: CommentPhase,
        val refreshing: Boolean,
        val busy: Boolean,
        val notice: String?,
        val editor: EditorUi?,
        val closedWith: String?,
    ) : CardDetailUiState
}

class CardDetailViewModel(
    private val savedState: SavedStateHandle,
    private val repository: KanbanRepository,
    private val board: String,
    private val cardId: CardId,
) : ViewModel() {

    private val _state = MutableStateFlow<CardDetailUiState>(CardDetailUiState.Loading)
    val state: StateFlow<CardDetailUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun refresh() {
        updateReady { it.copy(refreshing = true) }
        load()
    }

    fun retry() {
        _state.value = CardDetailUiState.Loading
        load()
    }

    fun onCommentChange(text: String) {
        savedState[COMMENT] = text
        updateReady { it.copy(comment = text, commentPhase = if (it.commentPhase is CommentPhase.Sending) it.commentPhase else CommentPhase.Idle) }
    }

    fun sendComment() {
        val ready = _state.value as? CardDetailUiState.Ready ?: return
        val text = ready.comment.trim()
        if (text.isEmpty()) {
            updateReady { it.copy(commentPhase = CommentPhase.Failed("Comment cannot be blank.")) }
            return
        }
        updateReady { it.copy(commentPhase = CommentPhase.Sending) }
        viewModelScope.launch {
            try {
                repository.comment(board, cardId, text)
                savedState[COMMENT] = ""
                updateReady { it.copy(comment = "", commentPhase = CommentPhase.Added) }
                load()
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Throwable) {
                updateReady { it.copy(commentPhase = CommentPhase.Failed(KanbanViewModel.message(error))) }
            }
        }
    }

    fun move(to: KanbanStatus) = mutate("Moved to ${to.title}") { repository.setStatus(board, cardId, to) }

    fun block(reason: String?) = mutate("Blocked") { repository.setStatus(board, cardId, KanbanStatus.Blocked, reason) }

    fun unblock() = mutate("Unblocked") { repository.setStatus(board, cardId, KanbanStatus.Ready) }

    fun complete() = mutate("Completed") { repository.setStatus(board, cardId, KanbanStatus.Done) }

    fun archive() = mutate("Archived", closes = true) { repository.setStatus(board, cardId, KanbanStatus.Archived) }

    fun dismissNotice() = updateReady { it.copy(notice = null) }

    fun openEditor() = updateReady { it.copy(editor = EditorUi(EditorMode.Edit(it.detail.card), saving = false, error = null)) }

    fun closeEditor() = updateReady { it.copy(editor = null) }

    fun save(draft: CardDraft) {
        val ready = _state.value as? CardDetailUiState.Ready ?: return
        if (draft.title.isBlank()) {
            updateReady { it.copy(editor = it.editor?.copy(error = "Title is required.")) }
            return
        }
        updateReady { it.copy(editor = it.editor?.copy(saving = true, error = null)) }
        viewModelScope.launch {
            try {
                repository.edit(board, ready.detail.card, draft)
                updateReady { it.copy(editor = null, notice = "Updated") }
                load()
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Throwable) {
                updateReady { it.copy(editor = it.editor?.copy(saving = false, error = KanbanViewModel.message(error))) }
            }
        }
    }

    private fun mutate(done: String, closes: Boolean = false, action: suspend () -> KanbanCard) {
        updateReady { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                action()
                if (closes) {
                    updateReady { it.copy(busy = false, closedWith = done) }
                } else {
                    updateReady { it.copy(busy = false, notice = done) }
                    load()
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Throwable) {
                updateReady { it.copy(busy = false, notice = KanbanViewModel.message(error)) }
            }
        }
    }

    private fun load() {
        viewModelScope.launch {
            try {
                val log = async { runCatching { repository.log(board, cardId) }.getOrNull() }
                val detail = repository.detail(board, cardId)
                val worker = log.await()
                val previous = _state.value as? CardDetailUiState.Ready
                _state.value = previous?.copy(detail = detail, log = worker, refreshing = false)
                    ?: CardDetailUiState.Ready(
                        detail = detail,
                        log = worker,
                        comment = savedState.get<String>(COMMENT).orEmpty(),
                        commentPhase = CommentPhase.Idle,
                        refreshing = false,
                        busy = false,
                        notice = null,
                        editor = null,
                        closedWith = null,
                    )
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (missing: KanbanFailure.NotFound) {
                _state.value = CardDetailUiState.Missing
            } catch (error: Throwable) {
                val previous = _state.value
                _state.value = if (previous is CardDetailUiState.Ready) {
                    previous.copy(refreshing = false, notice = KanbanViewModel.message(error))
                } else {
                    CardDetailUiState.Error(KanbanViewModel.message(error))
                }
            }
        }
    }

    private fun updateReady(transform: (CardDetailUiState.Ready) -> CardDetailUiState.Ready) {
        _state.update { current -> if (current is CardDetailUiState.Ready) transform(current) else current }
    }

    private companion object {
        const val COMMENT = "comment"
    }
}
