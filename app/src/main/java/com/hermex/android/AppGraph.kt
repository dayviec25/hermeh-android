package com.hermex.android

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.hermex.android.core.data.AppStore
import com.hermex.android.core.data.AuthRepository
import com.hermex.android.core.data.ChatRepository
import com.hermex.android.core.data.FilesRepository
import com.hermex.android.core.data.SecretCipher
import com.hermex.android.core.data.SessionRepository
import com.hermex.android.core.data.db.HermexDatabase
import com.hermex.android.core.model.AuthState
import com.hermex.android.core.network.GatewayTarget
import com.hermex.android.core.network.HermesGateway
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class AppGraph(context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val baseClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    val store = AppStore(context.applicationContext, SecretCipher())
    private val database by lazy { HermexDatabase.build(context.applicationContext) }

    val auth = AuthRepository(baseClient, store, appScope)

    val gateway = HermesGateway(auth.httpClient, appScope) {
        auth.authorized { rest -> GatewayTarget(rest.gatewayUrl(), rest.wsTicket(), rest.headers) }
    }

    val chat by lazy { ChatRepository(auth, gateway, database.transcripts()) }
    val sessions by lazy {
        SessionRepository(auth, database.sessions(), gateway, { chat.attachedRuntimes }, { chat.forgetRuntime(it) })
    }
    val files = FilesRepository(auth)

    private val _sessionsChanged = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    val sessionsChanged: SharedFlow<Unit> = _sessionsChanged.asSharedFlow()

    fun notifySessionsChanged() {
        _sessionsChanged.tryEmit(Unit)
    }

    suspend fun signOut() {
        gateway.stop()
        auth.signOut()
        sessions.clearAll()
        database.transcripts().clearAll()
    }

    fun start() {
        appScope.launch { auth.bootstrap() }
        appScope.launch(Dispatchers.Main) {
            ProcessLifecycleOwner.get().lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                try {
                    auth.state.collect { state ->
                        if (state is AuthState.SignedIn) gateway.start() else gateway.stop()
                    }
                } finally {
                    gateway.stop()
                }
            }
        }
    }
}
