package com.hermeh.android.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hermeh.android.core.data.AuthRepository
import com.hermeh.android.core.data.ProbeResult
import com.hermeh.android.core.model.ServerConfig
import com.hermeh.android.core.network.HermesFailure
import com.hermeh.android.core.network.HermesRest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ConnectStatus {
    data object Idle : ConnectStatus
    data object Checking : ConnectStatus
    data class Found(val text: String) : ConnectStatus
    data class Error(val text: String) : ConnectStatus
}

data class HeaderRow(val name: String, val value: String)

data class ConnectUiState(
    val url: String,
    val username: String,
    val password: String,
    val headers: List<HeaderRow>,
    val status: ConnectStatus,
) {
    val isWorking: Boolean get() = status == ConnectStatus.Checking
    val canSubmit: Boolean get() = url.isNotBlank() && username.isNotBlank() && password.isNotEmpty()
    val addressPreview: String? get() = HermesRest.normalize(url)?.let(HermesRest::display)
}

class ConnectViewModel(
    private val auth: AuthRepository,
    server: ServerConfig?,
    initialMessage: String?,
) : ViewModel() {

    private val _state = MutableStateFlow(
        ConnectUiState(
            url = server?.baseUrl.orEmpty(),
            username = server?.username.orEmpty(),
            password = "",
            headers = server?.customHeaders?.map { HeaderRow(it.first, it.second) }.orEmpty(),
            status = initialMessage?.let { ConnectStatus.Error(it) } ?: ConnectStatus.Idle,
        ),
    )
    val state: StateFlow<ConnectUiState> = _state.asStateFlow()

    fun onUrl(value: String) = _state.update { it.copy(url = value) }
    fun onUsername(value: String) = _state.update { it.copy(username = value) }
    fun onPassword(value: String) = _state.update { it.copy(password = value) }
    fun addHeader() = _state.update { it.copy(headers = it.headers + HeaderRow("", "")) }
    fun updateHeader(index: Int, row: HeaderRow) = _state.update {
        it.copy(headers = it.headers.mapIndexed { i, existing -> if (i == index) row else existing })
    }
    fun removeHeader(index: Int) = _state.update { it.copy(headers = it.headers.filterIndexed { i, _ -> i != index }) }

    fun testConnection() {
        val current = _state.value
        if (current.isWorking || current.url.isBlank()) return
        _state.update { it.copy(status = ConnectStatus.Checking) }
        viewModelScope.launch {
            val status = try {
                when (val result = auth.probe(current.url, validHeaders(current))) {
                    is ProbeResult.Found -> ConnectStatus.Found(
                        "Hermes dashboard found${result.status.version?.let { " ($it)" }.orEmpty()}. Sign in with your dashboard username and password.",
                    )
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Throwable) {
                ConnectStatus.Error(message(error, current))
            }
            _state.update { it.copy(status = status) }
        }
    }

    fun connect() {
        val current = _state.value
        if (current.isWorking || !current.canSubmit) {
            if (!current.isWorking && current.password.isEmpty() && current.url.isNotBlank()) {
                _state.update { it.copy(status = ConnectStatus.Error("Enter the server password.")) }
            }
            return
        }
        _state.update { it.copy(status = ConnectStatus.Checking) }
        viewModelScope.launch {
            try {
                auth.connect(current.url, current.username, current.password, validHeaders(current))
                _state.update { it.copy(status = ConnectStatus.Idle, password = "") }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Throwable) {
                _state.update { it.copy(status = ConnectStatus.Error(message(error, current))) }
            }
        }
    }

    private fun validHeaders(state: ConnectUiState): List<Pair<String, String>> =
        state.headers.filter { it.name.isNotBlank() && it.name.lowercase() !in RESERVED }.map { it.name.trim() to it.value.trim() }

    private fun message(error: Throwable, state: ConnectUiState): String = when (error) {
        is HermesFailure -> error.message.orEmpty()
        else -> HermesFailure.transportMessage(HermesRest.normalize(state.url)?.host ?: state.url, error)
    }

    private companion object {
        val RESERVED = setOf(
            "host", "connection", "upgrade", "content-length", "transfer-encoding", "te", "trailer", "keep-alive",
            "proxy-connection", "proxy-authorization", "cookie", "origin", "x-forwarded-prefix", "x-hermes-session-token",
        )
    }
}
