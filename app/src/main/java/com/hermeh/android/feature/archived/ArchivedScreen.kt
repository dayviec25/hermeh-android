package com.hermeh.android.feature.archived

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hermeh.android.core.data.SessionRepository
import com.hermeh.android.core.designsystem.GlassCircleButton
import com.hermeh.android.core.designsystem.AutomationAccessibilityRefresh
import com.hermeh.android.core.designsystem.HermehTheme
import com.hermeh.android.core.designsystem.HermehType
import com.hermeh.android.core.model.SessionSummary
import com.hermeh.android.core.model.StoredKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

data class ArchivedRowUi(val key: String, val profile: String, val title: String, val subtitle: String)

sealed interface ArchivedUiState {
    data object Loading : ArchivedUiState
    data object Empty : ArchivedUiState
    data class Error(val message: String) : ArchivedUiState
    data class Success(val rows: List<ArchivedRowUi>, val restoring: Set<String>, val message: String?) : ArchivedUiState
}

private data class ArchivedStatus(val loaded: Boolean = false, val error: String? = null, val restoring: Set<String> = emptySet(), val message: String? = null)

class ArchivedViewModel(
    private val repository: SessionRepository,
    private val serverId: String,
    private val profile: String,
    private val onChanged: () -> Unit,
) : ViewModel() {
    private val status = MutableStateFlow(ArchivedStatus())

    val state: StateFlow<ArchivedUiState> = combine(repository.sessions(serverId, profile, archived = true), status) { rows, load ->
        when {
            rows.isNotEmpty() -> ArchivedUiState.Success(rows.map { it.toRow() }, load.restoring, load.message)
            !load.loaded -> ArchivedUiState.Loading
            load.error != null -> ArchivedUiState.Error(load.error)
            else -> ArchivedUiState.Empty
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ArchivedUiState.Loading)

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val error = try {
                repository.refresh(serverId, profile, upTo = 200, archived = true)
                null
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Throwable) {
                error.message ?: "Couldn't load archived sessions."
            }
            status.update { it.copy(loaded = true, error = error) }
        }
    }

    fun unarchive(key: String) {
        status.update { it.copy(restoring = it.restoring + key, message = null) }
        viewModelScope.launch {
            try {
                repository.setArchived(serverId, StoredKey(key), false)
                onChanged()
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Throwable) {
                status.update { it.copy(message = error.message ?: "Couldn't unarchive the session.") }
            }
            status.update { it.copy(restoring = it.restoring - key) }
        }
    }

    private fun SessionSummary.toRow(): ArchivedRowUi {
        val age = Duration.between(lastActive, Instant.now()).toDays()
        val time = if (age < 1) "today" else "${age}d ago"
        return ArchivedRowUi(key.value, profile.value, title, "$messageCount ${if (messageCount == 1) "message" else "messages"} • $time")
    }
}

@Composable
fun ArchivedRoute(viewModel: ArchivedViewModel, onBack: () -> Unit, onOpen: (ArchivedRowUi) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AutomationAccessibilityRefresh()
    ArchivedScreen(state, onBack, onOpen, viewModel::unarchive, viewModel::refresh)
}

@Composable
fun ArchivedScreen(
    state: ArchivedUiState,
    onBack: () -> Unit,
    onOpen: (ArchivedRowUi) -> Unit,
    onUnarchive: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HermehTheme.colors
    Column(modifier.fillMaxSize().background(colors.background).windowInsetsPadding(WindowInsets.statusBars)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            GlassCircleButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack, Modifier.testTag("archived.back"))
            Text("Archived Sessions", style = HermehType.screenTitle, color = colors.label, modifier = Modifier.padding(start = 14.dp))
        }
        when (state) {
            ArchivedUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.secondaryLabel, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
            }
            ArchivedUiState.Empty -> Centered("No Archived Sessions", "Sessions you archive will appear here.", Modifier.testTag("archived.empty"))
            is ArchivedUiState.Error -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(state.message, color = colors.secondaryLabel, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(24.dp))
                TextButton(onClick = onRetry) { Text("Try Again", color = colors.accent) }
            }
            is ArchivedUiState.Success -> LazyColumn(Modifier.fillMaxSize().testTag("archived.list"), contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp)) {
                state.message?.let { message ->
                    item(key = "message", contentType = "message") {
                        Text(message, color = colors.danger, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
                    }
                }
                items(state.rows, key = { it.key }, contentType = { "row" }) { row ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onOpen(row) }
                            .heightIn(min = 56.dp)
                            .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp)
                            .testTag("archived.row.${row.key}"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(row.title, color = colors.label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(row.subtitle, color = colors.secondaryLabel, fontSize = 12.sp)
                        }
                        Spacer(Modifier.width(8.dp))
                        val restoring = row.key in state.restoring
                        Row(
                            Modifier
                                .clip(CircleShape)
                                .background(colors.secondaryBackground)
                                .clickable(enabled = !restoring) { onUnarchive(row.key) }
                                .heightIn(min = 40.dp)
                                .padding(horizontal = 12.dp)
                                .testTag("archived.unarchive.${row.key}"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            if (restoring) {
                                CircularProgressIndicator(color = colors.secondaryLabel, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
                            } else {
                                Icon(Icons.Outlined.Unarchive, contentDescription = null, tint = colors.label, modifier = Modifier.size(16.dp))
                            }
                            Text("Unarchive", color = colors.label, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Centered(title: String, message: String, modifier: Modifier = Modifier) {
    val colors = HermehTheme.colors
    Column(modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, color = colors.label, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Text(message, color = colors.secondaryLabel, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
    }
}
