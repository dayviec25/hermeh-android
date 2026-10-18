package com.hermex.android.feature.tasks

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hermex.android.core.data.ModelCatalog
import com.hermex.android.core.data.ModelOption
import com.hermex.android.core.data.TasksRepository
import com.hermex.android.core.model.CronDraft
import com.hermex.android.core.model.CronJob
import com.hermex.android.core.model.CronRun
import com.hermex.android.core.model.CronSaveResult
import com.hermex.android.core.model.DeliveryTarget
import com.hermex.android.core.model.Skill
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal fun Throwable.userMessage(): String = message?.takeIf { it.isNotBlank() } ?: "Something went wrong."

internal sealed interface TasksUiState {
    data object Loading : TasksUiState
    data object Empty : TasksUiState
    data class Error(val message: String) : TasksUiState
    data class Success(val jobs: List<CronJob>) : TasksUiState
}

internal data class TasksScreenState(
    val content: TasksUiState = TasksUiState.Loading,
    val filter: TaskFilter = TaskFilter.All,
    val refreshing: Boolean = false,
    val pending: Set<String> = emptySet(),
    val alert: String? = null,
) {
    val jobs: List<CronJob> get() = (content as? TasksUiState.Success)?.jobs.orEmpty()
    val visible: List<CronJob> get() = jobs.filter(filter::matches).sortedWith(compareBy<CronJob> { it.nextRunAt == null }.thenBy { it.nextRunAt }.thenBy { it.displayName.lowercase() })
    val running: List<CronJob> get() = jobs.filter { it.isRunning }
    fun count(filter: TaskFilter) = jobs.count(filter::matches)
    val stallAge: Double? get() = jobs.filter { it.enabled != false }.mapNotNull { it.schedulerHeartbeatAge }.filter { it > 180 }.maxOrNull()
}

internal class TasksViewModel(private val repo: TasksRepository) : ViewModel() {
    private val _state = MutableStateFlow(TasksScreenState())
    val state: StateFlow<TasksScreenState> = _state.asStateFlow()
    private var loadJob: Job? = null

    fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.update { it.copy(refreshing = true) }
            try {
                val jobs = repo.jobs()
                _state.update { it.copy(content = if (jobs.isEmpty()) TasksUiState.Empty else TasksUiState.Success(jobs), refreshing = false) }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                _state.update {
                    val keep = it.content is TasksUiState.Success
                    it.copy(
                        content = if (keep) it.content else TasksUiState.Error(error.userMessage()),
                        alert = if (keep) error.userMessage() else null,
                        refreshing = false,
                    )
                }
            }
        }
    }

    fun setFilter(filter: TaskFilter) = _state.update { it.copy(filter = filter) }

    fun dismissAlert() = _state.update { it.copy(alert = null) }

    fun runNow(job: CronJob) = act(job) { repo.trigger(it) }

    fun togglePause(job: CronJob) = act(job) { if (it.shouldResume) repo.resume(it) else repo.pause(it) }

    fun delete(job: CronJob) = act(job, remove = true) {
        repo.delete(it)
        null
    }

    private fun act(job: CronJob, remove: Boolean = false, block: suspend (CronJob) -> CronJob?) {
        if (job.id in _state.value.pending) return
        _state.update { it.copy(pending = it.pending + job.id) }
        viewModelScope.launch {
            try {
                val updated = block(job)
                _state.update { state ->
                    val jobs = when {
                        remove -> state.jobs.filterNot { it.id == job.id }
                        updated != null -> state.jobs.map { if (it.id == updated.id) updated else it }
                        else -> state.jobs
                    }
                    state.copy(content = if (jobs.isEmpty()) TasksUiState.Empty else TasksUiState.Success(jobs))
                }
                load()
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                _state.update { it.copy(alert = error.userMessage()) }
            } finally {
                _state.update { it.copy(pending = it.pending - job.id) }
            }
        }
    }
}

internal sealed interface TaskDetailUiState {
    data object Loading : TaskDetailUiState
    data class Error(val message: String) : TaskDetailUiState
    data object Gone : TaskDetailUiState
    data class Success(val job: CronJob) : TaskDetailUiState
}

internal sealed interface RunsState {
    data object Loading : RunsState
    data class Error(val message: String) : RunsState
    data class Loaded(val runs: List<CronRun>) : RunsState
}

internal enum class RunOutcome { Running, Completed, Failed }

internal data class TaskDetailScreenState(
    val content: TaskDetailUiState = TaskDetailUiState.Loading,
    val runs: RunsState = RunsState.Loading,
    val visibleRuns: Int = 20,
    val latestOutput: String? = null,
    val busy: Boolean = false,
    val runNowPending: Boolean = false,
    val alert: String? = null,
    val deleted: Boolean = false,
) {
    val job: CronJob? get() = (content as? TaskDetailUiState.Success)?.job

    fun outcome(run: CronRun): RunOutcome? {
        if (run.isActive) return RunOutcome.Running
        val job = job ?: return null
        val runs = (runs as? RunsState.Loaded)?.runs.orEmpty()
        val latest = runs.firstOrNull { !it.isActive }
        if (run.id != latest?.id) return null
        return when {
            job.hasFailedRun -> RunOutcome.Failed
            job.lastStatus == "ok" -> RunOutcome.Completed
            else -> null
        }
    }
}

internal class TaskDetailViewModel(handle: SavedStateHandle, private val repo: TasksRepository) : ViewModel() {
    private val jobId: String = requireNotNull(handle.get<String>("id")) { "TaskDetail without id" }
    private val _state = MutableStateFlow(TaskDetailScreenState())
    val state: StateFlow<TaskDetailScreenState> = _state.asStateFlow()
    private var poller: Job? = null

    fun load() {
        viewModelScope.launch { refresh(showErrors = true) }
    }

    private suspend fun refresh(showErrors: Boolean) {
        try {
            val job = repo.job(jobId)
            if (job == null) {
                _state.update { it.copy(content = TaskDetailUiState.Gone) }
                return
            }
            _state.update { it.copy(content = TaskDetailUiState.Success(job)) }
            loadRuns(job)
            watchWhileRunning()
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (error: Exception) {
            if (!showErrors) return
            _state.update {
                if (it.job != null) it.copy(alert = error.userMessage()) else it.copy(content = TaskDetailUiState.Error(error.userMessage()))
            }
        }
    }

    private suspend fun loadRuns(job: CronJob) {
        try {
            val runs = repo.runs(job)
            _state.update { it.copy(runs = RunsState.Loaded(runs)) }
            val latest = runs.firstOrNull { !it.isActive }
            if (job.hasFailedRun && latest != null) {
                val output = runCatchingCancellable { repo.runOutput(job, latest) }
                _state.update { it.copy(latestOutput = output) }
            } else {
                _state.update { it.copy(latestOutput = null) }
            }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (error: Exception) {
            _state.update { if (it.runs is RunsState.Loaded) it else it.copy(runs = RunsState.Error(error.userMessage())) }
        }
    }

    fun retryRuns() {
        val job = _state.value.job ?: return
        _state.update { it.copy(runs = RunsState.Loading) }
        viewModelScope.launch { loadRuns(job) }
    }

    fun loadMore() = _state.update { it.copy(visibleRuns = it.visibleRuns + 20) }

    private fun watchWhileRunning() {
        val state = _state.value
        val running = state.job?.isRunning == true || state.runNowPending ||
            (state.runs as? RunsState.Loaded)?.runs?.any { it.isActive } == true
        if (!running || poller?.isActive == true) return
        poller = viewModelScope.launch {
            while (isActive) {
                delay(POLL_MS)
                refresh(showErrors = false)
                val now = _state.value
                val still = now.job?.isRunning == true || now.runNowPending ||
                    (now.runs as? RunsState.Loaded)?.runs?.any { it.isActive } == true
                if (!still) break
            }
        }
    }

    fun runNow() {
        val job = _state.value.job ?: return
        if (_state.value.runNowPending) return
        _state.update { it.copy(runNowPending = true) }
        watchWhileRunning()
        viewModelScope.launch {
            try {
                val updated = repo.trigger(job)
                if (updated != null) _state.update { it.copy(content = TaskDetailUiState.Success(updated)) }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                _state.update { it.copy(alert = error.userMessage()) }
            } finally {
                _state.update { it.copy(runNowPending = false) }
                refresh(showErrors = false)
            }
        }
    }

    fun togglePause() = mutate { job -> if (job.shouldResume) repo.resume(job) else repo.pause(job) }

    fun delete() {
        val job = _state.value.job ?: return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                repo.delete(job)
                _state.update { it.copy(deleted = true, busy = false) }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                _state.update { it.copy(alert = error.userMessage(), busy = false) }
            }
        }
    }

    fun dismissAlert() = _state.update { it.copy(alert = null) }

    private fun mutate(block: suspend (CronJob) -> CronJob?) {
        val job = _state.value.job ?: return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                val updated = block(job)
                if (updated != null) _state.update { it.copy(content = TaskDetailUiState.Success(updated)) }
                refresh(showErrors = false)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                _state.update { it.copy(alert = error.userMessage()) }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    private companion object {
        const val POLL_MS = 2_500L
    }
}

internal suspend fun <T> runCatchingCancellable(block: suspend () -> T): T? = try {
    block()
} catch (cancel: CancellationException) {
    throw cancel
} catch (_: Exception) {
    null
}

internal sealed interface RunOutputUiState {
    data object Loading : RunOutputUiState
    data object Empty : RunOutputUiState
    data class Error(val message: String) : RunOutputUiState
    data class Success(val text: String) : RunOutputUiState
}

internal class RunOutputViewModel(handle: SavedStateHandle, private val repo: TasksRepository) : ViewModel() {
    private val jobId: String = requireNotNull(handle.get<String>("jobId")) { "RunOutput without jobId" }
    private val runId: String = requireNotNull(handle.get<String>("runId")) { "RunOutput without runId" }
    private val _state = MutableStateFlow<RunOutputUiState>(RunOutputUiState.Loading)
    val state: StateFlow<RunOutputUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.value = RunOutputUiState.Loading
        viewModelScope.launch {
            _state.value = try {
                val job = repo.job(jobId) ?: throw IllegalStateException("This task is no longer on the server.")
                val text = repo.runOutput(job, CronRun(runId, null, null, false, null, null, null, null))
                if (text.isNullOrBlank()) RunOutputUiState.Empty else RunOutputUiState.Success(text)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                RunOutputUiState.Error(error.userMessage())
            }
        }
    }
}

internal sealed interface EditorUiState {
    data object Loading : EditorUiState
    data class Error(val message: String) : EditorUiState
    data class Editing(val draft: CronDraft) : EditorUiState
}

internal data class EditorScreenState(
    val content: EditorUiState = EditorUiState.Loading,
    val isNew: Boolean = true,
    val targets: List<DeliveryTarget>? = null,
    val skills: List<Skill>? = null,
    val skillsError: String? = null,
    val models: ModelCatalog? = null,
    val modelsError: String? = null,
    val saving: Boolean = false,
    val error: String? = null,
    val warning: String? = null,
    val saved: Boolean = false,
) {
    val draft: CronDraft? get() = (content as? EditorUiState.Editing)?.draft
}

internal class TaskEditorViewModel(
    handle: SavedStateHandle,
    private val repo: TasksRepository,
    private val profile: String,
) : ViewModel() {
    private val jobId: String? = handle.get<String>("id")
    private var job: CronJob? = null
    private val _state = MutableStateFlow(EditorScreenState(isNew = jobId == null))
    val state: StateFlow<EditorScreenState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            if (jobId == null) {
                _state.update { it.copy(content = EditorUiState.Editing(CronDraft())) }
            } else {
                try {
                    val found = repo.job(jobId) ?: throw IllegalStateException("This task is no longer on the server.")
                    job = found
                    _state.update { it.copy(content = EditorUiState.Editing(CronDraft.from(found))) }
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (error: Exception) {
                    _state.update { it.copy(content = EditorUiState.Error(error.userMessage())) }
                    return@launch
                }
            }
            val scope = job?.profile?.takeIf { it.isNotBlank() } ?: profile
            launch { runCatchingCancellable { repo.deliveryTargets(scope) }?.let { t -> _state.update { it.copy(targets = t) } } }
            launch { loadSkills(scope) }
            launch {
                try {
                    val catalog = repo.models(scope)
                    _state.update { it.copy(models = catalog, modelsError = null) }
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (error: Exception) {
                    _state.update { it.copy(modelsError = error.userMessage()) }
                }
            }
        }
    }

    private suspend fun loadSkills(scope: String) {
        try {
            val skills = repo.skills(scope).filter { it.enabled }.sortedBy { it.name.lowercase() }
            _state.update { it.copy(skills = skills, skillsError = null) }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (error: Exception) {
            _state.update { it.copy(skillsError = error.userMessage()) }
        }
    }

    fun edit(transform: (CronDraft) -> CronDraft) = _state.update { state ->
        val draft = state.draft ?: return@update state
        state.copy(content = EditorUiState.Editing(transform(draft)), error = null)
    }

    fun pickModel(option: ModelOption?) = edit { it.copy(model = option?.id, provider = option?.provider) }

    fun toggleSkill(name: String) = edit { draft ->
        draft.copy(skills = if (name in draft.skills) draft.skills - name else draft.skills + name)
    }

    fun save() {
        val draft = _state.value.draft ?: return
        draft.validationMessage?.let { message ->
            _state.update { it.copy(error = message) }
            return
        }
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                val existing = job
                if (existing == null) {
                    when (val result = repo.create(profile, draft)) {
                        is CronSaveResult.Saved -> _state.update { it.copy(saved = true) }
                        is CronSaveResult.SavedWithWarning ->
                            if (result.warning != null) _state.update { it.copy(warning = result.warning) } else _state.update { it.copy(saved = true) }
                    }
                } else {
                    repo.update(existing, draft)
                    _state.update { it.copy(saved = true) }
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                _state.update { it.copy(error = error.userMessage()) }
            } finally {
                _state.update { it.copy(saving = false) }
            }
        }
    }

    fun acknowledgeWarning() = _state.update { it.copy(warning = null, saved = true) }
}
