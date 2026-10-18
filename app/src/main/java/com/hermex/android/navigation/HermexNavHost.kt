package com.hermex.android.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.hermex.android.AppGraph
import com.hermex.android.core.data.AppearancePrefs
import com.hermex.android.core.data.LogoColor
import com.hermex.android.core.data.ThemeMode
import com.hermex.android.core.model.AuthState
import com.hermex.android.feature.archived.ArchivedRoute
import com.hermex.android.feature.archived.ArchivedViewModel
import com.hermex.android.feature.chat.ChatRoute
import com.hermex.android.feature.files.FileViewerRoute
import com.hermex.android.feature.files.FileViewerViewModel
import com.hermex.android.feature.files.FolderRoute
import com.hermex.android.feature.files.FolderViewModel
import com.hermex.android.feature.chat.ChatViewModel
import com.hermex.android.feature.home.HomeRoute
import com.hermex.android.feature.home.HomeViewModel
import com.hermex.android.feature.placeholder.PlaceholderScreen
import com.hermex.android.feature.settings.SettingsScreen
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable data object HomeRouteKey

@Serializable data class ChatRouteKey(val key: String? = null, val profile: String, val title: String? = null, val cwd: String? = null)

@Serializable data object SettingsRouteKey

@Serializable data class PlaceholderRouteKey(val title: String)

@Serializable data object ArchivedRouteKey

@Serializable data class FilesRouteKey(val path: String, val root: String)

@Serializable data class FileViewerRouteKey(val path: String)

@Composable
fun HermexNavHost(graph: AppGraph, auth: AuthState.SignedIn) {
    val nav = rememberNavController()
    val scope = rememberCoroutineScope()
    val profile by graph.auth.activeProfile.collectAsStateWithLifecycle()
    NavHost(navController = nav, startDestination = HomeRouteKey) {
        composable<HomeRouteKey> {
            val viewModel: HomeViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        HomeViewModel(
                            repository = graph.sessions,
                            serverId = auth.server.id,
                            activeProfile = graph.auth.activeProfile,
                            showAllProfiles = graph.auth.showAllProfiles,
                            pickProfile = graph.auth::pickProfile,
                            sessionsChanged = graph.sessionsChanged,
                        )
                    }
                },
            )
            HomeRoute(
                viewModel = viewModel,
                initials = initials(auth.user.displayName.ifBlank { auth.server.username }),
                onOpenSession = { row -> nav.navigate(ChatRouteKey(row.key, row.profile, row.title, row.cwd)) },
                onNewChat = { nav.navigate(ChatRouteKey(null, profile, null)) },
                onDestination = { nav.navigate(PlaceholderRouteKey(it.title)) },
                onSettings = { nav.navigate(SettingsRouteKey) },
                onArchived = { nav.navigate(ArchivedRouteKey) },
            )
        }
        composable<ChatRouteKey> { entry ->
            val route = entry.toRoute<ChatRouteKey>()
            val viewModel: ChatViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        val handle = createSavedStateHandle()
                        handle["key"] = route.key
                        handle["profile"] = route.profile
                        handle["title"] = route.title
                        handle["cwd"] = route.cwd
                        ChatViewModel(handle, graph.chat, graph::notifySessionsChanged)
                    }
                },
            )
            ChatRoute(
                viewModel = viewModel,
                onBack = { nav.popBackStack() },
                onOpenFiles = { cwd -> nav.navigate(FilesRouteKey(cwd, cwd)) },
                onOpenFork = { fork -> nav.navigate(ChatRouteKey(fork.key, fork.profile, fork.title)) },
            )
        }
        composable<ArchivedRouteKey> {
            val viewModel: ArchivedViewModel = viewModel(
                factory = viewModelFactory {
                    initializer { ArchivedViewModel(graph.sessions, auth.server.id, profile, graph::notifySessionsChanged) }
                },
            )
            ArchivedRoute(
                viewModel = viewModel,
                onBack = { nav.popBackStack() },
                onOpen = { row -> nav.navigate(ChatRouteKey(row.key, row.profile, row.title)) },
            )
        }
        composable<FilesRouteKey> { entry ->
            val route = entry.toRoute<FilesRouteKey>()
            val viewModel: FolderViewModel = viewModel(
                factory = viewModelFactory { initializer { FolderViewModel(graph.files, route.path, route.root) } },
            )
            FolderRoute(
                viewModel = viewModel,
                path = route.path,
                root = route.root,
                onBack = { nav.popBackStack() },
                onRoot = { nav.popBackStack(FilesRouteKey(route.root, route.root), inclusive = false) },
                onOpenFolder = { path -> nav.navigate(FilesRouteKey(path, route.root)) },
                onOpenFile = { path -> nav.navigate(FileViewerRouteKey(path)) },
            )
        }
        composable<FileViewerRouteKey> { entry ->
            val route = entry.toRoute<FileViewerRouteKey>()
            val viewModel: FileViewerViewModel = viewModel(
                factory = viewModelFactory { initializer { FileViewerViewModel(graph.files, route.path) } },
            )
            FileViewerRoute(viewModel = viewModel, path = route.path, onBack = { nav.popBackStack() })
        }
        composable<SettingsRouteKey> {
            val appearance by graph.store.appearance.collectAsStateWithLifecycle(AppearancePrefs(ThemeMode.System, LogoColor.Yellow))
            SettingsScreen(
                auth = auth,
                appearance = appearance,
                onBack = { nav.popBackStack() },
                onTheme = { scope.launch { graph.store.setTheme(it) } },
                onLogo = { scope.launch { graph.store.setLogo(it) } },
                onSignOut = { graph.appScope.launch { graph.signOut() } },
            )
        }
        composable<PlaceholderRouteKey> { entry ->
            PlaceholderScreen(title = entry.toRoute<PlaceholderRouteKey>().title, onBack = { nav.popBackStack() })
        }
    }
}

private fun initials(name: String): String {
    val parts = name.trim().split(Regex("[\\s._-]+")).filter { it.isNotEmpty() }
    return when {
        parts.size >= 2 -> "${parts[0].first()}${parts[1].first()}".uppercase()
        parts.size == 1 -> parts[0].take(3).uppercase()
        else -> "?"
    }
}
