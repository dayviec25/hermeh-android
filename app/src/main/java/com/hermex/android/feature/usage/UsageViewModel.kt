package com.hermex.android.feature.usage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hermex.android.core.data.UsageRepository
import com.hermex.android.core.model.UsageDay
import com.hermex.android.core.model.UsageReport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

internal enum class UsageMetric(val slug: String, val title: String) {
    Cost("cost", "Cost"),
    Tokens("tokens", "Tokens"),
    Sessions("sessions", "Sessions"),
}

internal data class UsageBucket(
    val date: LocalDate,
    val label: String,
    val input: Long,
    val output: Long,
    val cacheRead: Long,
    val cost: Double,
    val sessions: Int,
) {
    fun segments(metric: UsageMetric): List<Pair<UsageSegment, Double>> = when (metric) {
        UsageMetric.Tokens -> listOf(UsageSegment.Input to input.toDouble(), UsageSegment.Output to output.toDouble(), UsageSegment.CacheRead to cacheRead.toDouble())
        UsageMetric.Cost -> listOf(UsageSegment.Cost to cost)
        UsageMetric.Sessions -> listOf(UsageSegment.Sessions to sessions.toDouble())
    }

    fun total(metric: UsageMetric): Double = segments(metric).sumOf { it.second }
}

internal enum class UsageSegment(val title: String) { Input("Input"), Output("Output"), CacheRead("Cache read"), Cost("Cost"), Sessions("Sessions") }

internal data class UsageView(val report: UsageReport, val buckets: List<UsageBucket>) {
    val processedTokens: Long get() = report.totals.input + report.totals.output
    val cost: Double get() = report.totals.actualCost.takeIf { it > 0 } ?: report.totals.estimatedCost
    val cacheHitPercent: Double?
        get() {
            val denominator = report.totals.input + report.totals.cacheRead
            return if (denominator > 0) report.totals.cacheRead * 100.0 / denominator else null
        }
}

internal sealed interface UsageUiState {
    data object Loading : UsageUiState
    data object Empty : UsageUiState
    data class Error(val message: String) : UsageUiState
    data class Success(val view: UsageView) : UsageUiState
}

internal data class UsageScreenState(
    val days: Int = 30,
    val chosenMetric: UsageMetric? = null,
    val content: UsageUiState = UsageUiState.Loading,
    val refreshing: Boolean = false,
) {
    val metric: UsageMetric
        get() = chosenMetric ?: (content as? UsageUiState.Success)?.view?.let { view ->
            UsageMetric.entries.firstOrNull { metric -> view.buckets.any { it.total(metric) > 0 } }
        } ?: UsageMetric.Cost
}

internal class UsageViewModel(private val repo: UsageRepository, private val profile: String) : ViewModel() {
    private val _state = MutableStateFlow(UsageScreenState())
    val state: StateFlow<UsageScreenState> = _state.asStateFlow()
    private var loadJob: Job? = null

    init {
        load()
    }

    fun selectWindow(days: Int) {
        if (days == _state.value.days) return
        _state.update { it.copy(days = days, content = UsageUiState.Loading) }
        load()
    }

    fun selectMetric(metric: UsageMetric) = _state.update { it.copy(chosenMetric = metric) }

    fun load() {
        val days = _state.value.days
        loadJob?.cancel()
        _state.update { it.copy(refreshing = true) }
        loadJob = viewModelScope.launch {
            try {
                val report = repo.usage(days, profile)
                val empty = report.totals.sessions == 0 && report.daily.all { it.sessions == 0 && it.inputTokens == 0L && it.outputTokens == 0L }
                _state.update {
                    it.copy(
                        content = if (empty) UsageUiState.Empty else UsageUiState.Success(UsageView(report, buckets(report.daily, days))),
                        refreshing = false,
                    )
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                _state.update { it.copy(content = UsageUiState.Error(error.message ?: "Something went wrong."), refreshing = false) }
            }
        }
    }

    private fun buckets(daily: List<UsageDay>, days: Int): List<UsageBucket> {
        val byDay = daily.mapNotNull { day -> runCatching { LocalDate.parse(day.day.take(10)) }.getOrNull()?.let { it to day } }.toMap()
        // Server days are its own wall-clock dates, so the window ends on whichever is later: the
        // device's today or the server's newest day. Anchoring on the device alone drops days when
        // the host's calendar runs ahead.
        val end = maxOf(LocalDate.now(), byDay.keys.maxOrNull() ?: LocalDate.MIN)
        val start = minOf(end.minusDays(days - 1L), byDay.keys.minOrNull() ?: LocalDate.MAX)
        val label = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
        return generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(end) }.toList().map { date ->
            val day = byDay[date]
            UsageBucket(
                date = date,
                label = label.format(date),
                input = day?.inputTokens ?: 0,
                output = day?.outputTokens ?: 0,
                cacheRead = day?.cacheReadTokens ?: 0,
                cost = day?.let { if (it.actualCost > 0) it.actualCost else it.estimatedCost } ?: 0.0,
                sessions = day?.sessions ?: 0,
            )
        }
    }
}

internal object UsageFormat {
    fun tokens(value: Long): String = String.format(Locale.getDefault(), "%,d", value)
    fun cost(value: Double): String =
        if (value > 0 && value < 0.01) String.format(Locale.US, "$%.4f", value) else String.format(Locale.US, "$%.2f", value)
    fun percent(value: Double): String =
        if (value % 1.0 == 0.0) "${value.toInt()}%" else String.format(Locale.getDefault(), "%.1f%%", value)
}
