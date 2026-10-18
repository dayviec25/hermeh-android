package com.hermeh.android.feature.tasks

import androidx.compose.runtime.Composable
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.hermeh.android.core.data.TasksRepository
import kotlinx.serialization.Serializable

@Serializable internal data object TasksListKey

@Serializable internal data class TaskDetailKey(val id: String)

@Serializable internal data class TaskEditorKey(val id: String? = null)

@Serializable internal data class TaskRunKey(val jobId: String, val runId: String, val title: String)

@Composable
fun TasksRoute(repository: TasksRepository, profile: String, onBack: () -> Unit) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = TasksListKey) {
        composable<TasksListKey> {
            val viewModel: TasksViewModel = viewModel(factory = viewModelFactory { initializer { TasksViewModel(repository) } })
            TasksListRoute(
                viewModel = viewModel,
                onBack = onBack,
                onOpen = { nav.navigate(TaskDetailKey(it.id)) },
                onNew = { nav.navigate(TaskEditorKey()) },
            )
        }
        composable<TaskDetailKey> {
            val viewModel: TaskDetailViewModel = viewModel(
                factory = viewModelFactory { initializer { TaskDetailViewModel(createSavedStateHandle(), repository) } },
            )
            TaskDetailRoute(
                viewModel = viewModel,
                onBack = { nav.popBackStack() },
                onEdit = { nav.navigate(TaskEditorKey(it.id)) },
                onOpenRun = { job, run, title -> nav.navigate(TaskRunKey(job.id, run.id, title)) },
            )
        }
        composable<TaskEditorKey> {
            val viewModel: TaskEditorViewModel = viewModel(
                factory = viewModelFactory { initializer { TaskEditorViewModel(createSavedStateHandle(), repository, profile) } },
            )
            TaskEditorRoute(viewModel = viewModel, onClose = { nav.popBackStack() })
        }
        composable<TaskRunKey> { entry ->
            val key = entry.toRoute<TaskRunKey>()
            val viewModel: RunOutputViewModel = viewModel(
                factory = viewModelFactory { initializer { RunOutputViewModel(createSavedStateHandle(), repository) } },
            )
            RunOutputRoute(viewModel = viewModel, title = key.title, onBack = { nav.popBackStack() })
        }
    }
}
