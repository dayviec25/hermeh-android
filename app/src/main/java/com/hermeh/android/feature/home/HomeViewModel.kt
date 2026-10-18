package com.hermeh.android.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hermeh.android.core.data.AppStore
import com.hermeh.android.core.data.SessionRepository
import com.hermeh.android.core.model.Companion
import com.hermeh.android.core.model.DefaultCompanion
import com.hermeh.android.core.model.MascotColor
import com.hermeh.android.core.model.MascotId
import com.hermeh.android.core.model.MascotMood
import com.hermeh.android.core.model.DeleteOutcome
import com.hermeh.android.core.model.ProfileSummary
import com.hermeh.android.core.model.ProjectLane
import com.hermeh.android.core.model.SessionSummary
import com.hermeh.android.core.model.StoredKey
import com.hermeh.android.core.network.HermesFailure
import com.hermeh.android.core.model.ConnectionState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.LocalTime

data class SessionRowUi(
    val key: String,
    val profile: String,
    val title: String,
    val subtitle: String,
    val time: String,
    val unread: Boolean,
    val pinned: Boolean,
    val cwd: String?,
    val active: Boolean,
)

enum class PartOfDay { Morning, Afternoon, Evening }

sealed interface CompanionThought {
    val mood: MascotMood

    data class Offline(val connection: ConnectionState) : CompanionThought {
        override val mood: MascotMood get() = MascotMood.Offline
    }

    data object Loading : CompanionThought {
        override val mood: MascotMood get() = MascotMood.Idle
    }

    data class Working(val session: SessionRowUi, val others: Int) : CompanionThought {
        override val mood: MascotMood get() = MascotMood.Working
    }

    data class Replies(val first: SessionRowUi, val count: Int) : CompanionThought {
        override val mood: MascotMood get() = MascotMood.Happy
    }

    data object Intro : CompanionThought {
        override val mood: MascotMood get() = MascotMood.Happy
    }

    data class Greeting(val partOfDay: PartOfDay) : CompanionThought {
        override val mood: MascotMood get() = MascotMood.Idle
    }
}

fun companionThought(
    connection: ConnectionState,
    loaded: Boolean,
    rows: List<SessionRowUi>,
    companion: Companion,
    partOfDay: PartOfDay,
): CompanionThought {
    if (connection != ConnectionState.Online) return CompanionThought.Offline(connection)
    if (!loaded && rows.isEmpty()) return CompanionThought.Loading
    val active = rows.filter { it.active }
    if (active.isNotEmpty()) return CompanionThought.Working(active.first(), active.size - 1)
    val unread = rows.filter { it.unread }
    if (unread.isNotEmpty()) return CompanionThought.Replies(unread.first(), unread.size)
    if (!companion.named) return CompanionThought.Intro
    return CompanionThought.Greeting(partOfDay)
}

fun partOfDay(time: LocalTime): PartOfDay = when (time.hour) {
    in 5..11 -> PartOfDay.Morning
    in 12..16 -> PartOfDay.Afternoon
    else -> PartOfDay.Evening
}

sealed interface SessionsContent {
    data object Loading : SessionsContent
    data object Empty : SessionsContent
    data class Success(val pinned: List<SessionRowUi>, val rows: List<SessionRowUi>) : SessionsContent
    data class Error(val message: String) : SessionsContent
}

data class ProfileOptionUi(val name: String, val displayName: String, val model: String, val selected: Boolean)

sealed interface ProfilesUi {
    data object Loading : ProfilesUi
    data class Error(val message: String) : ProfilesUi
    data class Ready(val options: List<ProfileOptionUi>, val showAll: Boolean) : ProfilesUi
}

data class ProjectRowUi(val id: String, val label: String, val count: Int, val color: String?)

sealed interface ProjectsUi {
    data object Loading : ProjectsUi
    data object Unavailable : ProjectsUi
    data class Error(val message: String) : ProjectsUi
    data class Ready(val lanes: List<ProjectRowUi>) : ProjectsUi
}

sealed interface SearchUi {
    data object Closed : SearchUi
    data class Open(val query: String, val searching: Boolean, val results: List<SessionRowUi>) : SearchUi
}

sealed interface ProjectSheet {
    data object Hidden : ProjectSheet
    data class Editing(val saving: Boolean, val error: String?) : ProjectSheet
}

data class HomeToast(val id: Long, val message: String, val undoArchiveKey: String?)

data class HomeUiState(
    val sessions: SessionsContent,
    val refreshing: Boolean,
    val loadingMore: Boolean,
    val hasMore: Boolean,
    val profile: String,
    val showAll: Boolean,
    val profilesExpanded: Boolean,
    val profiles: ProfilesUi,
    val projectsExpanded: Boolean,
    val projects: ProjectsUi,
    val selectedProject: ProjectRowUi?,
    val search: SearchUi,
    val toast: HomeToast?,
    val projectSheet: ProjectSheet,
    val connection: ConnectionState,
    val companion: Companion,
    val thought: CompanionThought,
)

private data class LoadStatus(
    val loaded: Boolean = false,
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val error: String? = null,
)

private data class Panels(
    val profilesExpanded: Boolean = false,
    val profiles: List<ProfileSummary>? = null,
    val profilesError: String? = null,
    val projectsExpanded: Boolean = false,
    val lanes: List<ProjectLane>? = null,
    val projectsError: String? = null,
    val projectsUnavailable: Boolean = false,
    val selectedProject: String? = null,
    val toast: HomeToast? = null,
    val projectSheet: ProjectSheet = ProjectSheet.Hidden,
)

private data class Listing(val loaded: Boolean, val rows: List<SessionRowUi>)

private data class SearchState(val open: Boolean = false, val query: String = "", val searching: Boolean = false, val server: List<SessionSummary> = emptyList())

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val repository: SessionRepository,
    private val serverId: String,
    private val activeProfile: StateFlow<String>,
    private val showAllProfiles: StateFlow<Boolean>,
    private val pickProfile: suspend (String, Boolean) -> Unit,
    sessionsChanged: Flow<Unit>,
    connection: Flow<ConnectionState>,
    companion: Flow<Companion>,
    private val companionStore: AppStore,
) : ViewModel() {

    private val status = MutableStateFlow(LoadStatus())
    private val panels = MutableStateFlow(Panels())
    private val search = MutableStateFlow(SearchState())
    private var nextOffset = 0
    private var refreshJob: Job? = null
    private var toastIds = 0L

    private val scope = combine(activeProfile, showAllProfiles) { profile, all -> profile to all }.distinctUntilChanged()

    private val sessions: Flow<List<SessionSummary>> = scope.flatMapLatest { (profile, all) ->
        repository.sessions(serverId, if (all) null else profile)
    }

    private val clock: Flow<PartOfDay> = flow {
        while (true) {
            emit(partOfDay(LocalTime.now()))
            delay(60_000)
        }
    }.distinctUntilChanged()

    private val listing = combine(sessions, status, panels, search, scope) { list, load, panel, find, (profile, all) ->
        val now = Instant.now()
        val lane = panel.selectedProject?.let { id -> panel.lanes?.firstOrNull { it.id == id } }
        val allRows = list.map { it.toRow(now) }
        val rows = if (lane == null) allRows else allRows.filter { StoredKey(it.key) in lane.sessionKeys }
        val content = when {
            rows.isNotEmpty() -> SessionsContent.Success(rows.filter { it.pinned }, rows.filterNot { it.pinned })
            !load.loaded -> SessionsContent.Loading
            load.error != null && list.isEmpty() -> SessionsContent.Error(load.error)
            else -> SessionsContent.Empty
        }
        HomeUiState(
            sessions = content,
            refreshing = load.refreshing,
            loadingMore = load.loadingMore,
            hasMore = load.hasMore && lane == null,
            profile = profile,
            showAll = all,
            profilesExpanded = panel.profilesExpanded,
            profiles = when {
                panel.profiles != null -> ProfilesUi.Ready(
                    panel.profiles.map {
                        ProfileOptionUi(it.name.value, it.displayName, it.model ?: "Default model unavailable", !all && it.name.value == profile)
                    },
                    all,
                )
                panel.profilesError != null -> ProfilesUi.Error(panel.profilesError)
                else -> ProfilesUi.Loading
            },
            projectsExpanded = panel.projectsExpanded,
            projects = when {
                panel.projectsUnavailable -> ProjectsUi.Unavailable
                panel.lanes != null -> ProjectsUi.Ready(panel.lanes.map { it.toRow() })
                panel.projectsError != null -> ProjectsUi.Error(panel.projectsError)
                else -> ProjectsUi.Loading
            },
            selectedProject = lane?.toRow(),
            search = if (!find.open) SearchUi.Closed else SearchUi.Open(find.query, find.searching, searchResults(find, list, now)),
            toast = panel.toast,
            projectSheet = panel.projectSheet,
            connection = ConnectionState.Online,
            companion = DefaultCompanion,
            thought = CompanionThought.Loading,
        ) to Listing(load.loaded, allRows)
    }

    val state: StateFlow<HomeUiState> = combine(listing, connection, companion, clock) { (base, listed), link, buddy, part ->
        base.copy(
            connection = link,
            companion = buddy,
            thought = companionThought(link, listed.loaded, listed.rows, buddy, part),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initialState())

    init {
        refresh()
        viewModelScope.launch { scope.drop(1).collect { reloadScope() } }
        viewModelScope.launch { sessionsChanged.debounce(600).collect { refresh(silent = true) } }
        viewModelScope.launch {
            search.distinctUntilChanged { a, b -> a.query == b.query && a.open == b.open }.debounce(300).collectLatestQuery()
        }
    }

    private fun initialState() = HomeUiState(
        sessions = SessionsContent.Loading,
        refreshing = false,
        loadingMore = false,
        hasMore = false,
        profile = activeProfile.value,
        showAll = showAllProfiles.value,
        profilesExpanded = false,
        profiles = ProfilesUi.Loading,
        projectsExpanded = false,
        projects = ProjectsUi.Loading,
        selectedProject = null,
        search = SearchUi.Closed,
        toast = null,
        projectSheet = ProjectSheet.Hidden,
        connection = ConnectionState.Online,
        companion = DefaultCompanion,
        thought = CompanionThought.Loading,
    )

    fun renameCompanion(name: String) {
        viewModelScope.launch { companionStore.setCompanionName(name) }
    }

    fun setCompanionColor(color: MascotColor) {
        viewModelScope.launch { companionStore.setCompanionColor(color) }
    }

    fun setCompanionMascot(id: MascotId) {
        viewModelScope.launch { companionStore.setCompanionMascot(id) }
    }

    fun refresh(silent: Boolean = false) {
        if (refreshJob?.isActive == true) return
        status.update { it.copy(refreshing = !silent) }
        refreshJob = viewModelScope.launch {
            val error = try {
                val result = if (showAllProfiles.value) {
                    repository.loadAllProfiles(serverId)
                } else {
                    repository.refresh(serverId, activeProfile.value, upTo = nextOffset.coerceAtLeast(PAGE))
                }
                nextOffset = result.added.coerceAtLeast(PAGE)
                status.update { it.copy(hasMore = result.hasMore) }
                null
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Throwable) {
                error.message ?: "Couldn't load sessions."
            }
            status.update { it.copy(loaded = true, refreshing = false, error = error) }
            if (panels.value.projectsExpanded) loadProjects()
        }
    }

    fun loadMore() {
        val current = status.value
        if (!current.hasMore || current.loadingMore || refreshJob?.isActive == true || showAllProfiles.value) return
        status.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            try {
                val result = repository.loadPage(serverId, activeProfile.value, nextOffset)
                nextOffset += PAGE
                status.update { it.copy(loadingMore = false, hasMore = result.hasMore && result.added > 0) }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Throwable) {
                status.update { it.copy(loadingMore = false) }
                toast(error.message ?: "Couldn't load more sessions.")
            }
        }
    }

    fun toggleProfiles() {
        val expand = !panels.value.profilesExpanded
        panels.update { it.copy(profilesExpanded = expand) }
        if (expand) loadProfiles()
    }

    fun selectProfile(name: String) {
        panels.update { it.copy(selectedProject = null, lanes = null) }
        viewModelScope.launch { pickProfile(name, false) }
    }

    fun showAll() {
        panels.update { it.copy(selectedProject = null) }
        viewModelScope.launch { pickProfile(activeProfile.value, true) }
    }

    fun toggleProjects() {
        val expand = !panels.value.projectsExpanded
        panels.update { it.copy(projectsExpanded = expand) }
        if (expand) loadProjects()
    }

    fun selectProject(id: String) {
        panels.update { it.copy(selectedProject = if (it.selectedProject == id) null else id) }
    }

    fun clearProject() = panels.update { it.copy(selectedProject = null) }

    fun openProjectSheet() = panels.update { it.copy(projectSheet = ProjectSheet.Editing(saving = false, error = null)) }

    fun dismissProjectSheet() = panels.update { it.copy(projectSheet = ProjectSheet.Hidden) }

    fun createProject(name: String, folder: String) {
        val trimmedName = name.trim()
        val trimmedFolder = folder.trim()
        if (trimmedName.isEmpty() || trimmedFolder.isEmpty()) {
            panels.update { it.copy(projectSheet = ProjectSheet.Editing(false, "Enter a name and a folder.")) }
            return
        }
        panels.update { it.copy(projectSheet = ProjectSheet.Editing(true, null)) }
        viewModelScope.launch {
            try {
                repository.createProject(activeProfile.value, trimmedName, trimmedFolder)
                panels.update { it.copy(projectSheet = ProjectSheet.Hidden, projectsExpanded = true) }
                loadProjects()
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Throwable) {
                panels.update { it.copy(projectSheet = ProjectSheet.Editing(false, error.message ?: "Couldn't create the project.")) }
            }
        }
    }

    fun openSearch() = search.update { it.copy(open = true) }

    fun closeSearch() = search.update { SearchState() }

    fun onQuery(query: String) = search.update { it.copy(query = query, searching = query.isNotBlank()) }

    fun onOpened(row: SessionRowUi) {
        if (!row.unread) return
        viewModelScope.launch { runCatching { repository.setUnread(serverId, StoredKey(row.key), false) } }
    }

    fun togglePinned(row: SessionRowUi) = act { repository.setPinned(serverId, StoredKey(row.key), !row.pinned) }

    fun toggleUnread(row: SessionRowUi) = act { repository.setUnread(serverId, StoredKey(row.key), !row.unread) }

    fun rename(row: SessionRowUi, title: String) = act { repository.rename(serverId, StoredKey(row.key), title) }

    fun archive(row: SessionRowUi) = act {
        repository.setArchived(serverId, StoredKey(row.key), true)
        toast("Archived", undoArchiveKey = row.key)
    }

    fun undoArchive(key: String) {
        panels.update { it.copy(toast = null) }
        act {
            repository.setArchived(serverId, StoredKey(key), false)
            refresh(silent = true)
        }
    }

    fun duplicate(row: SessionRowUi) = act {
        val result = repository.duplicate(serverId, StoredKey(row.key), row.profile, row.title)
        refresh(silent = true)
        toast(if (result.title != null) "Duplicated as “${result.title}”" else "Duplicated")
    }

    fun delete(row: SessionRowUi) = act {
        when (repository.delete(serverId, StoredKey(row.key), row.profile)) {
            DeleteOutcome.Deleted -> toast("Deleted")
            DeleteOutcome.BusyHere -> toast("This session is still replying here. Stop the reply first, then delete it.")
            DeleteOutcome.OpenElsewhere -> toast("This session is open in another app, so nothing was deleted. Close it there, then try again.")
        }
    }

    fun dismissToast(id: Long) = panels.update { if (it.toast?.id == id) it.copy(toast = null) else it }

    private fun act(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: HermesFailure) {
                toast(error.message ?: "Hermes refused the change.")
            } catch (error: Throwable) {
                toast(error.message ?: "Something went wrong.")
            }
        }
    }

    private fun toast(message: String, undoArchiveKey: String? = null) {
        toastIds += 1
        panels.update { it.copy(toast = HomeToast(toastIds, message, undoArchiveKey)) }
    }

    private fun reloadScope() {
        nextOffset = 0
        refreshJob?.cancel()
        status.update { it.copy(hasMore = false, refreshing = false) }
        refresh(silent = true)
        if (panels.value.projectsExpanded) loadProjects()
    }

    private fun loadProfiles() {
        viewModelScope.launch {
            try {
                val profiles = repository.profiles()
                panels.update { it.copy(profiles = profiles, profilesError = null) }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Throwable) {
                panels.update { it.copy(profilesError = if (it.profiles == null) "Could not load profiles" else null) }
            }
        }
    }

    private fun loadProjects() {
        viewModelScope.launch {
            try {
                val lanes = repository.projects(activeProfile.value)
                panels.update { it.copy(lanes = lanes, projectsError = null) }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (failure: HermesFailure.Rpc) {
                if (failure.code == -32601) {
                    panels.update { it.copy(projectsUnavailable = true) }
                } else {
                    panels.update { it.copy(projectsError = failure.message) }
                }
            } catch (error: Throwable) {
                panels.update { it.copy(projectsError = if (it.lanes == null) error.message ?: "Couldn't load projects." else null) }
            }
        }
    }

    private suspend fun Flow<SearchState>.collectLatestQuery() {
        var job: Job? = null
        collect { find ->
            job?.cancel()
            val query = find.query.trim()
            if (!find.open || query.isEmpty()) {
                search.update { it.copy(server = emptyList(), searching = false) }
                return@collect
            }
            job = viewModelScope.launch {
                val found = runCatching { repository.search(query, activeProfile.value) }.getOrElse { error ->
                    if (error is CancellationException) throw error
                    emptyList()
                }
                search.update { if (it.query.trim() == query) it.copy(server = found, searching = false) else it }
            }
        }
    }

    private fun searchResults(find: SearchState, loaded: List<SessionSummary>, now: Instant): List<SessionRowUi> {
        val query = find.query.trim()
        if (query.isEmpty()) return emptyList()
        val local = loaded.filter { it.title.contains(query, ignoreCase = true) || it.preview.contains(query, ignoreCase = true) }
        val keys = local.mapTo(HashSet()) { it.key }
        val loadedByKey = loaded.associateBy { it.key }
        val server = find.server.filter { it.key !in keys }.map { loadedByKey[it.key] ?: it }
        return (local + server).map { it.toRow(now) }
    }

    companion object Paging {
        private const val PAGE = 50
    }
}

private fun ProjectLane.toRow() = ProjectRowUi(id, label, sessionCount, color)

private fun SessionSummary.toRow(now: Instant): SessionRowUi = SessionRowUi(
    key = key.value,
    profile = profile.value,
    title = title,
    subtitle = "$messageCount ${if (messageCount == 1) "message" else "messages"} • ${profile.value}",
    time = relativeTime(lastActive, now),
    unread = unread,
    pinned = pinned,
    cwd = cwd,
    active = isActive,
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
