package com.hermex.android.feature.skills

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hermex.android.core.data.SkillsRepository
import com.hermex.android.core.model.Skill
import com.hermex.android.core.model.SkillDetail
import com.hermex.android.core.model.SkillFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal fun Throwable.userMessage(): String = message?.takeIf { it.isNotBlank() } ?: "Something went wrong."

internal data class SkillGroup(val category: String, val skills: List<Skill>)

internal sealed interface SkillsUiState {
    data object Loading : SkillsUiState
    data object Empty : SkillsUiState
    data class Error(val message: String) : SkillsUiState
    data class Success(val skills: List<Skill>) : SkillsUiState
}

internal data class SkillsScreenState(
    val content: SkillsUiState = SkillsUiState.Loading,
    val query: String = "",
    val refreshing: Boolean = false,
    val toggling: Set<String> = emptySet(),
    val toggleError: String? = null,
) {
    val groups: List<SkillGroup>
        get() {
            val skills = (content as? SkillsUiState.Success)?.skills.orEmpty()
            val needle = query.trim().lowercase()
            val matching = if (needle.isEmpty()) skills else skills.filter {
                it.name.lowercase().contains(needle) ||
                    it.description.orEmpty().lowercase().contains(needle) ||
                    it.category.orEmpty().lowercase().contains(needle)
            }
            return matching.groupBy { it.category?.takeIf { c -> c.isNotBlank() } ?: "Uncategorized" }
                .map { (category, list) -> SkillGroup(category, list.sortedBy { it.name.lowercase() }) }
                .sortedBy { it.category.lowercase() }
        }
}

internal class SkillsViewModel(private val repo: SkillsRepository, private val profile: String) : ViewModel() {
    private val _state = MutableStateFlow(SkillsScreenState())
    val state: StateFlow<SkillsScreenState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(refreshing = true) }
        viewModelScope.launch {
            try {
                val skills = repo.skills(profile)
                _state.update { it.copy(content = if (skills.isEmpty()) SkillsUiState.Empty else SkillsUiState.Success(skills), refreshing = false) }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                _state.update {
                    if (it.content is SkillsUiState.Success) it.copy(refreshing = false) else it.copy(content = SkillsUiState.Error(error.userMessage()), refreshing = false)
                }
            }
        }
    }

    fun search(query: String) = _state.update { it.copy(query = query) }

    fun skill(name: String): Skill? = (_state.value.content as? SkillsUiState.Success)?.skills?.firstOrNull { it.name == name }

    fun setEnabled(name: String, enabled: Boolean) {
        if (name in _state.value.toggling) return
        val before = skill(name)?.enabled ?: return
        apply(name, enabled, toggling = true)
        viewModelScope.launch {
            try {
                val confirmed = repo.setEnabled(name, enabled, profile)
                apply(name, confirmed, toggling = false)
            } catch (cancel: CancellationException) {
                apply(name, before, toggling = false)
                throw cancel
            } catch (error: Exception) {
                apply(name, before, toggling = false)
                _state.update { it.copy(toggleError = error.userMessage()) }
            }
        }
    }

    fun dismissToggleError() = _state.update { it.copy(toggleError = null) }

    private fun apply(name: String, enabled: Boolean, toggling: Boolean) = _state.update { state ->
        val content = state.content as? SkillsUiState.Success ?: return@update state
        state.copy(
            content = SkillsUiState.Success(content.skills.map { if (it.name == name) it.copy(enabled = enabled) else it }),
            toggling = if (toggling) state.toggling + name else state.toggling - name,
        )
    }
}

internal sealed interface SkillDetailUiState {
    data object Loading : SkillDetailUiState
    data object Empty : SkillDetailUiState
    data class Error(val message: String) : SkillDetailUiState
    data class Success(val detail: SkillDetail) : SkillDetailUiState
}

internal class SkillDetailViewModel(handle: SavedStateHandle, private val repo: SkillsRepository, private val profile: String) : ViewModel() {
    private val name: String = requireNotNull(handle.get<String>("name")) { "SkillDetail without name" }
    private val _state = MutableStateFlow<SkillDetailUiState>(SkillDetailUiState.Loading)
    val state: StateFlow<SkillDetailUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            try {
                val detail = repo.detail(name, profile)
                _state.value = if (detail.content.isBlank() && detail.linkedFiles.isEmpty()) SkillDetailUiState.Empty else SkillDetailUiState.Success(detail)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                if (_state.value !is SkillDetailUiState.Success) _state.value = SkillDetailUiState.Error(error.userMessage())
            }
        }
    }
}

internal sealed interface SkillFileUiState {
    data object Loading : SkillFileUiState
    data object Empty : SkillFileUiState
    data object Binary : SkillFileUiState
    data class Error(val message: String) : SkillFileUiState
    data class Success(val file: SkillFile) : SkillFileUiState
}

internal class SkillFileViewModel(handle: SavedStateHandle, private val repo: SkillsRepository, private val profile: String) : ViewModel() {
    private val name: String = requireNotNull(handle.get<String>("name")) { "SkillFile without name" }
    private val file: String = requireNotNull(handle.get<String>("file")) { "SkillFile without file" }
    private val _state = MutableStateFlow<SkillFileUiState>(SkillFileUiState.Loading)
    val state: StateFlow<SkillFileUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.value = SkillFileUiState.Loading
        viewModelScope.launch {
            _state.value = try {
                val loaded = repo.file(name, profile, file)
                when {
                    loaded == null -> SkillFileUiState.Empty
                    loaded.binary -> SkillFileUiState.Binary
                    loaded.text.isNullOrEmpty() -> SkillFileUiState.Empty
                    else -> SkillFileUiState.Success(loaded)
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                SkillFileUiState.Error(error.userMessage())
            }
        }
    }
}
