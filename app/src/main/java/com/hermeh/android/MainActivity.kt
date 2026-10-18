package com.hermeh.android

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hermeh.android.core.data.ThemeMode
import com.hermeh.android.core.designsystem.HermehTheme
import com.hermeh.android.core.designsystem.Onboarding
import com.hermeh.android.core.model.AuthState
import com.hermeh.android.core.model.DefaultCompanion
import com.hermeh.android.feature.onboarding.OnboardingRoute
import com.hermeh.android.navigation.HermehNavHost

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val graph = (application as HermehApplication).graph
        setContent {
            val theme by graph.store.theme.collectAsStateWithLifecycle(ThemeMode.System)
            val companion by graph.store.companion.collectAsStateWithLifecycle(DefaultCompanion)
            val auth by graph.auth.state.collectAsStateWithLifecycle()
            val onboarded by graph.store.onboarded.collectAsStateWithLifecycle<Boolean?>(null)
            val signedIn = auth is AuthState.SignedIn
            val dark = when {
                !signedIn -> true
                theme == ThemeMode.Dark -> true
                theme == ThemeMode.Light -> false
                else -> isSystemInDarkTheme()
            }
            LaunchedEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            HermehTheme(mode = if (signedIn) theme else ThemeMode.Dark, mascot = companion.color) {
                Root(auth = auth, onboarded = onboarded, graph = graph)
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun Root(auth: AuthState?, onboarded: Boolean?, graph: AppGraph) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .semantics { testTagsAsResourceId = true },
    ) {
        when {
            auth == null || onboarded == null -> Box(Modifier.fillMaxSize().background(Onboarding.background))
            else -> RootContent(auth, onboarded, graph)
        }
    }
}

@Composable
private fun RootContent(auth: AuthState, onboarded: Boolean, graph: AppGraph) {
    Box(Modifier.fillMaxSize()) {
        when (auth) {
            AuthState.Unconfigured -> OnboardingRoute(graph = graph, startAtConnect = onboarded, initialMessage = null)
            is AuthState.SignedOut -> OnboardingRoute(graph = graph, startAtConnect = true, initialMessage = auth.message, server = auth.server)
            is AuthState.SignedIn -> HermehNavHost(graph = graph, auth = auth)
        }
    }
}
