package com.hermeh.android.core.data

import com.hermeh.android.core.model.AuthState
import com.hermeh.android.core.model.HermesUser
import com.hermeh.android.core.model.ServerConfig
import com.hermeh.android.core.network.HermesCompatibility
import com.hermeh.android.core.network.HermesFailure
import com.hermeh.android.core.network.HermesRest
import com.hermeh.android.core.network.HostStatus
import com.hermeh.android.core.network.PersistentCookieJar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import java.util.UUID

sealed interface ProbeResult {
    data class Found(val status: HostStatus, val address: String) : ProbeResult
}

class AuthRepository(
    private val baseClient: OkHttpClient,
    private val store: AppStore,
    private val scope: CoroutineScope,
) {
    val cookieJar = PersistentCookieJar { serialized -> scope.launch { store.saveCookies(serialized) } }
    val httpClient: OkHttpClient = baseClient.newBuilder().cookieJar(cookieJar).build()

    private val _state = MutableStateFlow<AuthState?>(null)
    val state: StateFlow<AuthState?> = _state.asStateFlow()

    private val _profile = MutableStateFlow("default")
    val activeProfile: StateFlow<String> = _profile.asStateFlow()

    private val _showAll = MutableStateFlow(false)
    val showAllProfiles: StateFlow<Boolean> = _showAll.asStateFlow()

    private var rest: HermesRest? = null
    private val signInMutex = Mutex()

    suspend fun bootstrap() {
        val saved = store.currentServer()
        if (saved == null) {
            _state.value = AuthState.Unconfigured
            return
        }
        cookieJar.restore(store.cookies())
        val client = restFor(saved.config) ?: run {
            _state.value = AuthState.SignedOut(saved.config, null)
            return
        }
        rest = client
        _state.value = try {
            val user = try {
                client.me()
            } catch (failure: HermesFailure.Rejected) {
                if (failure.code != 401) throw failure
                reSignIn(client, saved.config)
                client.me()
            }
            refreshProfile(client)
            val version = runCatching { client.status().version }.getOrNull().orEmpty()
            AuthState.SignedIn(saved.config, user, version)
        } catch (failure: HermesFailure.Transport) {
            store.profilePick()?.let { _profile.value = it }
            _showAll.value = store.showAllProfiles()
            AuthState.SignedIn(saved.config, HermesUser(saved.config.username, saved.config.username, "basic"), "")
        } catch (failure: HermesFailure) {
            AuthState.SignedOut(saved.config, failure.message)
        }
    }

    suspend fun probe(address: String, headers: List<Pair<String, String>>): ProbeResult {
        val url = HermesRest.normalize(address) ?: throw HermesFailure.InvalidAddress()
        val client = HermesRest(httpClient, url, headers)
        val status = client.status()
        checkStatus(status)
        return ProbeResult.Found(status, HermesRest.display(url))
    }

    suspend fun connect(address: String, username: String, password: String, headers: List<Pair<String, String>>) {
        signInMutex.withLock {
            val url = HermesRest.normalize(address) ?: throw HermesFailure.InvalidAddress()
            val client = HermesRest(httpClient, url, headers)
            val status = client.status()
            checkStatus(status)
            val saved = store.currentServer()
            if (saved != null && saved.config.baseUrl == HermesRest.display(url) &&
                saved.installId != null && status.installId != null && saved.installId != status.installId
            ) {
                throw HermesFailure.Rpc(0, "The Hermes host at this address reports a different identity than the one you connected to. Check the address.")
            }
            cookieJar.clear()
            client.passwordLogin(username.trim(), password)
            val user = client.me()
            if (user.provider.isNotEmpty() && user.provider != "basic") throw HermesFailure.Unsupported()
            val config = ServerConfig(
                id = saved?.config?.id ?: UUID.randomUUID().toString(),
                name = url.host,
                baseUrl = HermesRest.display(url),
                username = username.trim(),
                customHeaders = headers,
            )
            store.saveServer(SavedServer(config, status.installId), password)
            store.markOnboarded()
            rest = client
            refreshProfile(client)
            _state.value = AuthState.SignedIn(config, user, status.version.orEmpty())
        }
    }

    suspend fun <T> authorized(block: suspend (HermesRest) -> T): T {
        val signedIn = _state.value as? AuthState.SignedIn ?: throw HermesFailure.SignInExpired()
        val client = rest ?: restFor(signedIn.server) ?: throw HermesFailure.InvalidAddress()
        return try {
            block(client)
        } catch (failure: HermesFailure.Rejected) {
            if (failure.code != 401) throw failure
            try {
                signInMutex.withLock { reSignIn(client, signedIn.server) }
            } catch (expired: HermesFailure) {
                _state.value = AuthState.SignedOut(signedIn.server, HermesFailure.SignInExpired().message)
                throw HermesFailure.SignInExpired()
            }
            block(client)
        }
    }

    suspend fun signOut() {
        store.clearCredentials()
        cookieJar.clear()
        rest = null
        _state.value = AuthState.Unconfigured
    }

    fun currentRest(): HermesRest? = rest

    private suspend fun reSignIn(client: HermesRest, server: ServerConfig) {
        val password = store.password() ?: throw HermesFailure.SignInExpired()
        client.passwordLogin(server.username, password)
    }

    suspend fun pickProfile(name: String, showAll: Boolean) {
        _profile.value = name
        _showAll.value = showAll
        store.setProfilePick(name, showAll)
    }

    private suspend fun refreshProfile(client: HermesRest) {
        _showAll.value = store.showAllProfiles()
        val picked = store.profilePick()
        if (picked != null) {
            _profile.value = picked
            return
        }
        runCatching { client.activeProfile() }.onSuccess { _profile.value = it }
    }

    private fun restFor(config: ServerConfig): HermesRest? {
        val url = HermesRest.normalize(config.baseUrl) ?: config.baseUrl.toHttpUrlOrNull() ?: return null
        return HermesRest(httpClient, url, config.customHeaders)
    }

    private fun checkStatus(status: HostStatus) {
        if (!HermesCompatibility.isSupported(status.version)) throw HermesFailure.Outdated(status.version.orEmpty())
        if (!status.authRequired) throw HermesFailure.Unsupported()
        if (!status.authProviders.contains("basic")) {
            if (status.authFlows.contains("native_pkce")) throw HermesFailure.BrowserSignIn()
            throw HermesFailure.Unsupported()
        }
    }
}
