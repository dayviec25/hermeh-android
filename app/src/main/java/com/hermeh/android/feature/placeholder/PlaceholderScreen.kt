package com.hermeh.android.feature.placeholder

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hermeh.android.core.designsystem.GlassCircleButton
import com.hermeh.android.core.designsystem.HermehTheme
import com.hermeh.android.core.designsystem.HermehType

@Composable
fun PlaceholderScreen(title: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val colors = HermehTheme.colors
    Column(modifier.fillMaxSize().background(colors.background).windowInsetsPadding(WindowInsets.statusBars)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            GlassCircleButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack, Modifier.testTag("placeholder.back"))
            Text(title, style = HermehType.screenTitle, color = colors.label, modifier = Modifier.padding(start = 14.dp))
        }
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Coming soon", color = colors.label, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "$title arrives in a later Hermeh for Android update.",
                color = colors.secondaryLabel,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp).testTag("placeholder.message"),
            )
        }
    }
}
