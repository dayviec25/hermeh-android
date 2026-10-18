package com.hermex.android.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hermex.android.core.data.SessionRepository
import com.hermex.android.core.model.SessionSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

data class SessionRowUi(
    val key: String,
    val profile: String,
    val title: String,
    val subtitle: String,
    val time: String,
    val unread: Boolean,
)

sealed interface SessionsContent {
    data object Loading : SessionsContent
    data object Empty : SessionsContent
    data class Success(val rows: List<SessionRowUi>) : SessionsContent
    data class Error(val message: String) : SessionsContent
}

data class HomeUiState(val sessions: SessionsContent, val refreshing: Boolean, val error: String?)

private data class LoadStatus(val loaded: Boolean, val refreshing: Boolean, val error: String?)

@OptIn(FlowPreview::class)
class HomeViewModel(
    private val repository: SessionRepository,
    private val serverId: String,
    sessionsChanged: Flow<Unit>,
) : ViewModel() {

    private val status = MutableStateFlow(LoadStatus(loaded = false, refreshing = false, error = null))

    val state: StateFlow<HomeUiState> = combine(repository.sessions(serverId), status) { sessions, load ->
        val content = when {
            sessions.isNotEmpty() -> SessionsContent.Success(sessions.map { it.toRow(Instant.now()) })
            !load.loaded -> SessionsContent.Loading
            load.error != null -> SessionsContent.Error(load.error)
            else -> SessionsContent.Empty
        }
        HomeUiState(content, load.refreshing, load.error)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState(SessionsContent.Loading, false, null))

    init {
        refresh()
        viewModelScope.launch {
            sessionsChanged.debounce(600).collect { refresh(silent = true) }
        }
    }

    fun refresh(silent: Boolean = false) {
        if (status.value.refreshing) return
        status.value = status.value.copy(refreshing = !silent)
        viewModelScope.launch {
            val error = try {
                repository.refresh(serverId)
                null
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Throwable) {
                error.message ?: "Couldn't load sessions."
            }
            status.value = LoadStatus(loaded = true, refreshing = false, error = error)
        }
    }
}

private fun SessionSummary.toRow(now: Instant): SessionRowUi = SessionRowUi(
    key = key.value,
    profile = profile.value,
    title = title,
    subtitle = "$messageCount ${if (messageCount == 1) "message" else "messages"} • ${profile.value}",
    time = relativeTime(lastActive, now),
    unread = unread,
)

fun relativeTime(then: Instant, now: Instant): String {
    val seconds = Duration.between(then, now).seconds.coerceAtLeast(0)
    return when {
        seconds < 60 -> "now"
        seconds < 3_600 -> "${seconds / 60}m ago"
        seconds < 86_400 -> "${seconds / 3_600}h ago"
        seconds < 604_800 -> "${seconds / 86_400}d ago"
        seconds < 2_592_000 -> "${seconds / 604_800}w ago"
        seconds < 31_536_000 -> "${seconds / 2_592_000}mo ago"
        else -> "${seconds / 31_536_000}y ago"
    }
}
