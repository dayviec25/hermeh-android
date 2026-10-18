package com.hermeh.android.feature.memory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hermeh.android.core.data.MemoryCanonicalizer
import com.hermeh.android.core.data.MemoryConflict
import com.hermeh.android.core.data.MemoryRepository
import com.hermeh.android.core.model.MemoryEntry
import com.hermeh.android.core.model.MemorySection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal fun Throwable.userMessage(): String = message?.takeIf { it.isNotBlank() } ?: "Something went wrong."

internal sealed interface MemoryUiState {
    data object Loading : MemoryUiState
    data class Error(val message: String) : MemoryUiState
    data class Success(val entries: List<MemoryEntry>) : MemoryUiState
}

internal data class MemoryDraft(
    val section: MemorySection,
    val text: String,
    val loaded: String,
    val limit: Int?,
    val readOnly: Boolean,
    val saving: Boolean = false,
    val reloading: Boolean = false,
    val conflicted: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false,
) {
    val count: Int get() = MemoryCanonicalizer.count(text)
    val isOver: Boolean get() = limit != null && count > limit
    val canSave: Boolean get() = !saving && !reloading && !readOnly && !isOver
}

internal data class MemoryScreenState(
    val content: MemoryUiState = MemoryUiState.Loading,
    val refreshing: Boolean = false,
    val draft: MemoryDraft? = null,
)

internal class MemoryViewModel(private val repo: MemoryRepository, private val profile: String) : ViewModel() {
    private val _state = MutableStateFlow(MemoryScreenState())
    val state: StateFlow<MemoryScreenState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(refreshing = true) }
        viewModelScope.launch {
            try {
                val snapshot = repo.load(profile)
                _state.update { it.copy(content = MemoryUiState.Success(snapshot.entries), refreshing = false) }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                _state.update {
                    if (it.content is MemoryUiState.Success) it.copy(refreshing = false) else it.copy(content = MemoryUiState.Error(error.userMessage()), refreshing = false)
                }
            }
        }
    }

    fun beginEdit(section: MemorySection) {
        val entry = (_state.value.content as? MemoryUiState.Success)?.entries?.firstOrNull { it.section == section } ?: return
        _state.update { it.copy(draft = MemoryDraft(section, entry.text, entry.text, entry.limit, entry.readOnly)) }
    }

    fun endEdit() = _state.update { it.copy(draft = null) }

    fun editText(text: String) = _state.update { state -> state.copy(draft = state.draft?.copy(text = text, error = null)) }

    fun save() {
        val draft = _state.value.draft ?: return
        if (!draft.canSave) return
        _state.update { it.copy(draft = draft.copy(saving = true, error = null)) }
        viewModelScope.launch {
            try {
                val written = repo.save(profile, draft.section, draft.text, draft.loaded)
                _state.update { state ->
                    val content = state.content as? MemoryUiState.Success
                    state.copy(
                        content = content?.copy(entries = content.entries.map { if (it.section == draft.section) it.copy(text = written) else it }) ?: state.content,
                        draft = state.draft?.copy(saving = false, saved = true, conflicted = false),
                    )
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (conflict: MemoryConflict) {
                _state.update { it.copy(draft = it.draft?.copy(saving = false, conflicted = true)) }
            } catch (error: Exception) {
                _state.update { it.copy(draft = it.draft?.copy(saving = false, error = "Could not save memory. ${error.userMessage()}")) }
            }
        }
    }

    fun keepDraft() = _state.update { it.copy(draft = it.draft?.copy(conflicted = false)) }

    fun reloadDraft() {
        val draft = _state.value.draft ?: return
        _state.update { it.copy(draft = draft.copy(reloading = true)) }
        viewModelScope.launch {
            try {
                val text = repo.reload(profile, draft.section)
                _state.update { state ->
                    val content = state.content as? MemoryUiState.Success
                    state.copy(
                        content = content?.copy(entries = content.entries.map { if (it.section == draft.section) it.copy(text = text) else it }) ?: state.content,
                        draft = state.draft?.copy(text = text, loaded = text, reloading = false, conflicted = false, error = null),
                    )
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                _state.update { it.copy(draft = it.draft?.copy(reloading = false, error = error.userMessage())) }
            }
        }
    }
}
