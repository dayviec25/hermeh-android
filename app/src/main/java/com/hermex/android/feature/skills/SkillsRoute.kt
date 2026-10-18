package com.hermex.android.feature.skills

import androidx.compose.runtime.Composable
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.hermex.android.core.data.SkillsRepository
import kotlinx.serialization.Serializable

@Serializable internal data object SkillsListKey

@Serializable internal data class SkillDetailKey(val name: String)

@Serializable internal data class SkillFileKey(val name: String, val file: String)

@Composable
fun SkillsRoute(repository: SkillsRepository, profile: String, onBack: () -> Unit) {
    val nav = rememberNavController()
    val skillsViewModel: SkillsViewModel = viewModel(factory = viewModelFactory { initializer { SkillsViewModel(repository, profile) } })
    NavHost(navController = nav, startDestination = SkillsListKey) {
        composable<SkillsListKey> {
            SkillsListRoute(viewModel = skillsViewModel, onBack = onBack, onOpen = { nav.navigate(SkillDetailKey(it.name)) })
        }
        composable<SkillDetailKey> { entry ->
            val name = entry.toRoute<SkillDetailKey>().name
            val viewModel: SkillDetailViewModel = viewModel(
                factory = viewModelFactory { initializer { SkillDetailViewModel(createSavedStateHandle(), repository, profile) } },
            )
            SkillDetailRoute(
                viewModel = viewModel,
                listViewModel = skillsViewModel,
                name = name,
                onBack = { nav.popBackStack() },
                onOpenFile = { nav.navigate(SkillFileKey(name, it)) },
            )
        }
        composable<SkillFileKey> { entry ->
            val key = entry.toRoute<SkillFileKey>()
            val viewModel: SkillFileViewModel = viewModel(
                factory = viewModelFactory { initializer { SkillFileViewModel(createSavedStateHandle(), repository, profile) } },
            )
            SkillFileRoute(viewModel = viewModel, title = key.file, onBack = { nav.popBackStack() })
        }
    }
}
