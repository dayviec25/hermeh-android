package com.hermex.android

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
import com.hermex.android.core.data.AppearancePrefs
import com.hermex.android.core.data.LogoColor
import com.hermex.android.core.data.ThemeMode
import com.hermex.android.core.designsystem.HermexTheme
import com.hermex.android.core.designsystem.Onboarding
import com.hermex.android.core.model.AuthState
import com.hermex.android.feature.onboarding.OnboardingRoute
import com.hermex.android.navigation.HermexNavHost

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val graph = (application as HermexApplication).graph
        setContent {
            val appearance by graph.store.appearance.collectAsStateWithLifecycle(AppearancePrefs(ThemeMode.System, LogoColor.Yellow))
            val auth by graph.auth.state.collectAsStateWithLifecycle()
            val onboarded by graph.store.onboarded.collectAsStateWithLifecycle(false)
            val signedIn = auth is AuthState.SignedIn
            val dark = when {
                !signedIn -> true
                appearance.theme == ThemeMode.Dark -> true
                appearance.theme == ThemeMode.Light -> false
                else -> isSystemInDarkTheme()
            }
            LaunchedEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            HermexTheme(mode = if (signedIn) appearance.theme else ThemeMode.Dark, logo = appearance.logo) {
                Root(auth = auth, onboarded = onboarded, graph = graph)
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun Root(auth: AuthState?, onboarded: Boolean, graph: AppGraph) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .semantics { testTagsAsResourceId = true },
    ) {
        when (auth) {
            null -> Box(Modifier.fillMaxSize().background(Onboarding.background))
            AuthState.Unconfigured -> OnboardingRoute(graph = graph, startAtConnect = onboarded, initialMessage = null)
            is AuthState.SignedOut -> OnboardingRoute(graph = graph, startAtConnect = true, initialMessage = auth.message, server = auth.server)
            is AuthState.SignedIn -> HermexNavHost(graph = graph, auth = auth)
        }
    }
}
