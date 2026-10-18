package com.hermex.android.feature.kanban

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hermex.android.core.data.KanbanOverview
import com.hermex.android.core.data.KanbanRepository
import com.hermex.android.core.model.CardDraft
import com.hermex.android.core.model.CardId
import com.hermex.android.core.model.DispatchResult
import com.hermex.android.core.model.KanbanBoard
import com.hermex.android.core.model.KanbanCard
import com.hermex.android.core.model.KanbanStatus
import com.hermex.android.core.network.HermesFailure
import com.hermex.android.core.network.KanbanFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CardRowUi(
    val card: KanbanCard,
    val key: String,
    val priority: String,
    val age: String?,
    val assignee: String,
    val preview: String,
)

sealed interface EditorMode {
    data object New : EditorMode
    data class Edit(val card: KanbanCard) : EditorMode
}

data class EditorUi(val mode: EditorMode, val saving: Boolean, val error: String?)

sealed interface DispatchUi {
    data object Idle : DispatchUi
    data class Running(val dryRun: Boolean) : DispatchUi
    data class Finished(val result: DispatchResult) : DispatchUi
    data class Failed(val dryRun: Boolean, val message: String) : DispatchUi
}

sealed interface KanbanUiState {
    data object Loading : KanbanUiState
    data class Error(val title: String, val detail: String) : KanbanUiState
    data class Ready(
        val boards: List<KanbanBoard>,
        val board: KanbanBoard,
        val status: KanbanStatus,
        val counts: Map<KanbanStatus, Int>,
        val rows: List<CardRowUi>,
        val query: String,
        val refreshing: Boolean,
        val profiles: List<String>,
        val busy: Set<CardId>,
        val notice: String?,
        val editor: EditorUi?,
        val dispatch: DispatchUi,
    ) : KanbanUiState
}

class KanbanViewModel(
    private val savedState: SavedStateHandle,
    private val repository: KanbanRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<KanbanUiState>(KanbanUiState.Loading)
    val state: StateFlow<KanbanUiState> = _state.asStateFlow()

    private var overview: KanbanOverview? = null
    private var status: KanbanStatus = KanbanStatus.fromWire(savedState.get<String>(STATUS)) ?: KanbanStatus.Triage
    private var query: String = savedState.get<String>(QUERY).orEmpty()

    init {
        load(silent = false)
    }

    fun refresh() = load(silent = true)

    fun retry() {
        _state.value = KanbanUiState.Loading
        load(silent = false)
    }

    fun selectBoard(slug: String) {
        savedState[BOARD] = slug
        updateReady { it.copy(refreshing = true) }
        load(silent = true)
    }

    fun selectStatus(next: KanbanStatus) {
        status = next
        savedState[STATUS] = next.wire
        publish()
    }

    fun onQueryChange(text: String) {
        query = text
        savedState[QUERY] = text
        publish()
    }

    fun dismissNotice() = updateReady { it.copy(notice = null) }

    fun showNotice(text: String) = updateReady { it.copy(notice = text) }

    fun openNewCard() = updateReady { it.copy(editor = EditorUi(EditorMode.New, saving = false, error = null)) }

    fun closeEditor() = updateReady { it.copy(editor = null) }

    fun save(draft: CardDraft) {
        val board = overview?.board?.slug ?: return
        if (draft.title.isBlank()) {
            updateReady { it.copy(editor = it.editor?.copy(error = "Title is required.")) }
            return
        }
        updateReady { it.copy(editor = it.editor?.copy(saving = true, error = null)) }
        viewModelScope.launch {
            try {
                val card = repository.create(board, draft)
                card.status?.let { landed ->
                    status = landed
                    savedState[STATUS] = landed.wire
                }
                updateReady { it.copy(editor = null, notice = "Created ${card.title}") }
                load(silent = true)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Throwable) {
                updateReady { it.copy(editor = it.editor?.copy(saving = false, error = message(error))) }
            }
        }
    }

    fun move(card: KanbanCard, to: KanbanStatus) = mutate(card, "Moved to ${to.title}") { repository.setStatus(it, card.id, to) }

    fun block(card: KanbanCard, reason: String?) =
        mutate(card, "Blocked") { repository.setStatus(it, card.id, KanbanStatus.Blocked, reason) }

    fun unblock(card: KanbanCard) = mutate(card, "Unblocked") { repository.setStatus(it, card.id, KanbanStatus.Ready) }

    fun complete(card: KanbanCard) = mutate(card, "Completed") { repository.setStatus(it, card.id, KanbanStatus.Done) }

    fun archive(card: KanbanCard) = mutate(card, "Archived") { repository.setStatus(it, card.id, KanbanStatus.Archived) }

    fun dispatch(dryRun: Boolean) {
        val board = overview?.board?.slug ?: return
        updateReady { it.copy(dispatch = DispatchUi.Running(dryRun)) }
        viewModelScope.launch {
            try {
                val result = repository.dispatch(board, dryRun)
                updateReady { it.copy(dispatch = DispatchUi.Finished(result)) }
                if (!dryRun) load(silent = true)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Throwable) {
                updateReady { it.copy(dispatch = DispatchUi.Failed(dryRun, message(error))) }
            }
        }
    }

    fun dismissDispatch() = updateReady { it.copy(dispatch = DispatchUi.Idle) }

    private fun mutate(card: KanbanCard, done: String, action: suspend (String) -> KanbanCard) {
        val board = overview?.board?.slug ?: return
        updateReady { it.copy(busy = it.busy + card.id) }
        viewModelScope.launch {
            try {
                action(board)
                updateReady { it.copy(busy = it.busy - card.id, notice = done) }
                load(silent = true)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Throwable) {
                updateReady { it.copy(busy = it.busy - card.id, notice = message(error)) }
            }
        }
    }

    private fun load(silent: Boolean) {
        if (silent) updateReady { it.copy(refreshing = true) }
        viewModelScope.launch {
            try {
                val fresh = repository.overview(savedState.get<String>(BOARD))
                overview = fresh
                savedState[BOARD] = fresh.board.slug
                publish(refreshing = false)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Throwable) {
                val current = _state.value
                if (current is KanbanUiState.Ready) {
                    _state.value = current.copy(refreshing = false, notice = "Could not refresh this Board. Previously loaded Cards remain visible.")
                } else {
                    _state.value = errorState(error)
                }
            }
        }
    }

    private fun publish(refreshing: Boolean? = null) {
        val data = overview ?: return
        val previous = _state.value as? KanbanUiState.Ready
        val needle = query.trim()
        val rows = data.snapshot.columns[status].orEmpty()
            .filter { card ->
                needle.isEmpty() || card.title.contains(needle, ignoreCase = true) ||
                    card.id.value.contains(needle, ignoreCase = true) || card.body.contains(needle, ignoreCase = true)
            }
            .sortedWith(compareByDescending<KanbanCard> { it.priority }.thenBy { it.ageSeconds ?: Long.MAX_VALUE })
            .map { it.toRow() }
        _state.value = KanbanUiState.Ready(
            boards = data.boards,
            board = data.board,
            status = status,
            counts = KanbanStatus.board.associateWith { data.snapshot.columns[it].orEmpty().size },
            rows = rows,
            query = query,
            refreshing = refreshing ?: previous?.refreshing ?: false,
            profiles = data.profiles,
            busy = previous?.busy.orEmpty(),
            notice = previous?.notice,
            editor = previous?.editor,
            dispatch = previous?.dispatch ?: DispatchUi.Idle,
        )
    }

    private fun updateReady(transform: (KanbanUiState.Ready) -> KanbanUiState.Ready) {
        _state.update { current -> if (current is KanbanUiState.Ready) transform(current) else current }
    }

    companion object {
        private const val STATUS = "status"
        private const val QUERY = "query"
        private const val BOARD = "board"

        fun errorState(error: Throwable): KanbanUiState.Error = when (error) {
            is KanbanFailure.PluginAbsent -> KanbanUiState.Error(
                "Kanban isn’t available on this server.",
                "Turn on the Kanban plugin on the Hermes host, then try again.",
            )
            is HermesFailure.Transport -> KanbanUiState.Error(
                "Kanban could not reach the server.",
                "Check your connection, then try again.",
            )
            is HermesFailure.SignInExpired -> KanbanUiState.Error(
                "Sign in is required for Kanban.",
                "Return to the server login screen, then try again.",
            )
            is HermesFailure.Rejected -> KanbanUiState.Error(
                "The Kanban server is unavailable.",
                "Check that the Hermes server is awake, then try again.",
            )
            else -> KanbanUiState.Error(
                "This server's Kanban response is incompatible with Hermex.",
                "No Kanban changes were made.",
            )
        }

        fun message(error: Throwable): String = when (error) {
            is KanbanFailure -> error.message.orEmpty()
            is HermesFailure -> error.message.orEmpty()
            else -> "The server refused or did not apply this update."
        }
    }
}

internal fun KanbanCard.toRow() = CardRowUi(
    card = this,
    key = id.value,
    priority = "P$priority",
    age = ageSeconds?.let(::abbreviatedAge),
    assignee = assignee ?: "Unassigned",
    preview = plainPreview(body),
)
