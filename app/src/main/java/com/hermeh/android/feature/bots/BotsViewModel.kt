package com.hermeh.android.feature.bots

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hermeh.android.core.data.BotsRepository
import com.hermeh.android.core.model.BotLiveStatus
import com.hermeh.android.core.model.BotProfile
import com.hermeh.android.core.model.ProfileName
import com.hermeh.android.core.model.StoredKey
import com.hermeh.android.core.network.HermesFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

data class BotRowUi(
    val bot: BotProfile,
    val key: String,
    val initials: String,
    val subline: String,
    val time: String?,
    val live: BotLiveStatus?,
    val dimmed: Boolean,
)

data class BotChatTarget(val key: StoredKey, val profile: ProfileName, val title: String)

sealed interface BotsUiState {
    data object Loading : BotsUiState
    data object Empty : BotsUiState
    data class Error(val message: String) : BotsUiState
    data class Success(
        val rows: List<BotRowUi>,
        val query: String,
        val refreshing: Boolean,
        val opening: String?,
        val openChat: BotChatTarget?,
        val notice: String?,
    ) : BotsUiState
}

class BotsViewModel(
    private val savedState: SavedStateHandle,
    private val repository: BotsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<BotsUiState>(BotsUiState.Loading)
    val state: StateFlow<BotsUiState> = _state.asStateFlow()

    private var roster: List<BotProfile> = emptyList()
    private var query: String = savedState.get<String>(QUERY).orEmpty()
    private var loadJob: Job? = null
    private var pollJob: Job? = null

    init {
        load(silent = false)
        viewModelScope.launch { repository.sessionsChanged.collect { load(silent = true) } }
    }

    fun refresh() = load(silent = true)

    fun retry() {
        _state.value = BotsUiState.Loading
        load(silent = false)
    }

    fun onVisible() {
        load(silent = true)
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (isActive) {
                delay(POLL_MS)
                if (roster.any { it.live != null }) load(silent = true)
            }
        }
    }

    fun onHidden() {
        pollJob?.cancel()
        pollJob = null
    }

    fun onQueryChange(text: String) {
        query = text
        savedState[QUERY] = text
        publish()
    }

    fun open(bot: BotProfile) {
        val current = _state.value as? BotsUiState.Success ?: return
        if (current.opening != null) return
        updateSuccess { it.copy(opening = bot.profile.value, notice = null) }
        viewModelScope.launch {
            try {
                val key = repository.botChatKey(bot)
                updateSuccess { it.copy(opening = null, openChat = BotChatTarget(key, bot.profile, bot.name)) }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Throwable) {
                updateSuccess { it.copy(opening = null, notice = error.message ?: "Couldn't open this Bot Chat.") }
            }
        }
    }

    fun onChatOpened() = updateSuccess { it.copy(openChat = null) }

    fun dismissNotice() = updateSuccess { it.copy(notice = null) }

    private fun load(silent: Boolean) {
        if (loadJob?.isActive == true) return
        if (silent) updateSuccess { it.copy(refreshing = true) }
        loadJob = viewModelScope.launch {
            try {
                roster = repository.roster()
                publish(refreshing = false)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Throwable) {
                val current = _state.value
                if (current is BotsUiState.Success) {
                    _state.value = current.copy(refreshing = false, notice = "Could Not Load Profiles")
                } else {
                    _state.value = BotsUiState.Error(
                        (error as? HermesFailure)?.message ?: "Couldn't reach Hermes. Check the connection, then try again.",
                    )
                }
            }
        }
    }

    private fun publish(refreshing: Boolean? = null) {
        val previous = _state.value as? BotsUiState.Success
        if (roster.isEmpty()) {
            _state.value = BotsUiState.Empty
            return
        }
        val needle = query.trim()
        val now = Instant.now()
        val rows = roster
            .filter { bot ->
                if (needle.isEmpty()) {
                    !bot.hidden
                } else {
                    listOfNotNull(bot.name, bot.profile.value, bot.preview, bot.description).any { it.contains(needle, ignoreCase = true) }
                }
            }
            .sortedWith(
                compareBy<BotProfile> { if (it.pinned) 0 else 1 }
                    .thenBy { it.live?.ordinal ?: Int.MAX_VALUE }
                    .thenByDescending { it.lastActive ?: Instant.EPOCH }
                    .thenBy { it.profile.value },
            )
            .map { bot ->
                BotRowUi(
                    bot = bot,
                    key = bot.profile.value,
                    initials = initials(bot.name),
                    subline = bot.preview ?: bot.description ?: bot.profile.value,
                    time = bot.lastActive?.let { inboxDate(it, now) },
                    live = bot.live,
                    dimmed = bot.hidden,
                )
            }
        _state.value = BotsUiState.Success(
            rows = rows,
            query = query,
            refreshing = refreshing ?: previous?.refreshing ?: false,
            opening = previous?.opening,
            openChat = previous?.openChat,
            notice = previous?.notice,
        )
    }

    private fun updateSuccess(transform: (BotsUiState.Success) -> BotsUiState.Success) {
        _state.update { current -> if (current is BotsUiState.Success) transform(current) else current }
    }

    companion object {
        private const val QUERY = "query"
        private const val POLL_MS = 5_000L

        fun initials(name: String): String {
            val parts = name.trim().split(Regex("[\\s._-]+")).filter { it.isNotEmpty() }
            return when {
                parts.size >= 2 -> "${parts[0].first()}${parts[1].first()}".uppercase()
                parts.size == 1 -> parts[0].take(1).uppercase()
                else -> "?"
            }
        }

        private val timeFormat = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
        private val dayFormat = DateTimeFormatter.ofPattern("MMM d")

        fun inboxDate(then: Instant, now: Instant): String {
            val zone = ZoneId.systemDefault()
            val date = then.atZone(zone)
            val today = LocalDate.ofInstant(now, zone)
            val day = date.toLocalDate()
            return when {
                day == today -> timeFormat.format(date)
                day.isAfter(today.minusDays(7)) && then.isBefore(now) -> day.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
                else -> dayFormat.format(date)
            }
        }
    }
}
