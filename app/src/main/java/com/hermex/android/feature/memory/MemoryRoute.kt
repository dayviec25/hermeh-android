package com.hermex.android.feature.memory

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.hermex.android.core.data.MemoryRepository
import com.hermex.android.core.model.MemorySection
import kotlinx.serialization.Serializable

@Serializable internal data object MemoryListKey

@Serializable internal data class MemoryEditKey(val section: String)

@Composable
fun MemoryRoute(repository: MemoryRepository, profile: String, onBack: () -> Unit) {
    val nav = rememberNavController()
    val viewModel: MemoryViewModel = viewModel(factory = viewModelFactory { initializer { MemoryViewModel(repository, profile) } })
    NavHost(navController = nav, startDestination = MemoryListKey) {
        composable<MemoryListKey> {
            MemoryListRoute(viewModel = viewModel, onBack = onBack, onEdit = {
                viewModel.beginEdit(it)
                nav.navigate(MemoryEditKey(it.slug))
            })
        }
        composable<MemoryEditKey> { entry ->
            val section = MemorySection.entries.first { it.slug == entry.toRoute<MemoryEditKey>().section }
            MemoryEditRoute(viewModel = viewModel, section = section, onClose = {
                viewModel.endEdit()
                nav.popBackStack()
            })
        }
    }
}
