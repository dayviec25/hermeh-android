package com.hermex.android.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.hermex.android.core.data.BotsRepository
import com.hermex.android.core.data.KanbanRepository
import com.hermex.android.core.model.AuthState
import com.hermex.android.core.model.CardId
import com.hermex.android.feature.bots.BotsRoute
import com.hermex.android.feature.bots.BotsViewModel
import com.hermex.android.feature.chat.ChatRoute
import com.hermex.android.feature.chat.ChatViewModel
import com.hermex.android.feature.home.HomeRoute
import com.hermex.android.feature.home.HomeDestination
import com.hermex.android.feature.home.HomeViewModel
import com.hermex.android.feature.kanban.CardDetailViewModel
import com.hermex.android.feature.kanban.KanbanRoute
import com.hermex.android.feature.kanban.KanbanViewModel
import com.hermex.android.feature.placeholder.PlaceholderScreen
import com.hermex.android.feature.settings.SettingsScreen
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable data object HomeRouteKey

@Serializable data class ChatRouteKey(val key: String? = null, val profile: String, val title: String? = null)

@Serializable data object SettingsRouteKey

@Serializable data class PlaceholderRouteKey(val title: String)

@Serializable data object KanbanRouteKey

@Serializable data object BotsRouteKey

@Composable
fun HermexNavHost(graph: AppGraph, auth: AuthState.SignedIn) {
    val nav = rememberNavController()
    val scope = rememberCoroutineScope()
    val profile by graph.auth.activeProfile.collectAsStateWithLifecycle()
    NavHost(navController = nav, startDestination = HomeRouteKey) {
        composable<HomeRouteKey> {
            val viewModel: HomeViewModel = viewModel(
                factory = viewModelFactory {
                    initializer { HomeViewModel(graph.sessions, auth.server.id, graph.sessionsChanged) }
                },
            )
            HomeRoute(
                viewModel = viewModel,
                initials = initials(auth.user.displayName.ifBlank { auth.server.username }),
                onOpenSession = { row -> nav.navigate(ChatRouteKey(row.key, row.profile, row.title)) },
                onNewChat = { nav.navigate(ChatRouteKey(null, profile, null)) },
                onDestination = {
                    when (it) {
                        HomeDestination.Kanban -> nav.navigate(KanbanRouteKey)
                        HomeDestination.Bots -> nav.navigate(BotsRouteKey)
                        else -> nav.navigate(PlaceholderRouteKey(it.title))
                    }
                },
                onSearch = { nav.navigate(PlaceholderRouteKey("Search")) },
                onSettings = { nav.navigate(SettingsRouteKey) },
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
                        ChatViewModel(handle, graph.chat, graph::notifySessionsChanged)
                    }
                },
            )
            ChatRoute(viewModel = viewModel, onBack = { nav.popBackStack() })
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
        composable<KanbanRouteKey> {
            val kanban = remember { KanbanRepository(graph.auth) }
            val viewModel: KanbanViewModel = viewModel(
                factory = viewModelFactory { initializer { KanbanViewModel(createSavedStateHandle(), kanban) } },
            )
            KanbanRoute(
                viewModel = viewModel,
                detailViewModel = { board, cardId ->
                    viewModel(
                        key = "card-$board-$cardId",
                        factory = viewModelFactory {
                            initializer { CardDetailViewModel(createSavedStateHandle(), kanban, board, CardId(cardId)) }
                        },
                    )
                },
                onBack = { nav.popBackStack() },
            )
        }
        composable<BotsRouteKey> {
            val viewModel: BotsViewModel = viewModel(
                factory = viewModelFactory { initializer { BotsViewModel(createSavedStateHandle(), BotsRepository(graph.gateway)) } },
            )
            BotsRoute(
                viewModel = viewModel,
                onBack = { nav.popBackStack() },
                onOpenChat = { target -> nav.navigate(ChatRouteKey(target.key.value, target.profile.value, target.title)) },
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
