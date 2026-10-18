package com.hermeh.android.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hermeh.android.core.data.ThemeMode
import com.hermeh.android.core.model.MascotColor

@Immutable
data class HermehColors(
    val isDark: Boolean,
    val background: Color,
    val secondaryBackground: Color,
    val tertiaryBackground: Color,
    val label: Color,
    val secondaryLabel: Color,
    val tertiaryLabel: Color,
    val separator: Color,
    val accent: Color,
    val ink: Color,
    val onInk: Color,
    val userBubble: Color,
    val userBubbleStroke: Color,
    val codeBackground: Color,
    val inlineCode: Color,
    val tableBackground: Color,
    val tableAlternate: Color,
    val tableBorder: Color,
    val glass: Color,
    val glassStroke: Color,
    val shadow: Color,
    val approval: Color,
    val input: Color,
    val working: Color,
    val danger: Color,
    val success: Color,
    val mascot: Color,
    val onMascot: Color,
)

object Onboarding {
    val background = Color(0xFF161412)
    val sunny = Color(0xFFFFC94D)
    val onSunny = Color(0xFF1F1B16)
    val success = Color(0xFF73EB8F)
    val error = Color(0xFFFF7857)
    val glow = Color(0xFFFFB547)
    val glowInner = Color(0xFFFFD877)
}

val OnMascot = Color(0xFF1F1B16)

private fun colors(dark: Boolean, mascot: Color): HermehColors {
    val label = if (dark) Color(0xFFF4EFE8) else Color(0xFF1F1B16)
    val background = if (dark) Color(0xFF161412) else Color(0xFFFBF8F3)
    val secondaryBackground = if (dark) Color(0xFF211E1B) else Color(0xFFF3EEE6)
    return HermehColors(
        isDark = dark,
        background = background,
        secondaryBackground = secondaryBackground,
        tertiaryBackground = if (dark) Color(0xFF2B2724) else Color(0xFFFFFFFF),
        label = label,
        secondaryLabel = label.copy(alpha = 0.62f),
        tertiaryLabel = label.copy(alpha = 0.34f),
        separator = if (dark) Color(0xFF3A3531) else Color(0xFFE6DED2),
        accent = if (dark) Color(0xFFE8946A) else Color(0xFFC8643B),
        ink = label,
        onInk = background,
        userBubble = mascot.copy(alpha = if (dark) 0.18f else 0.22f).compositeOver(secondaryBackground),
        userBubbleStroke = if (dark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.04f),
        codeBackground = if (dark) Color(0xFF0F0E0C) else Color(0xFFF1EBE1),
        inlineCode = if (dark) Color(0xFF24201C) else Color(0xFFEEE7DC),
        tableBackground = if (dark) Color(0xFF1C1A17) else Color(0xFFFFFFFF),
        tableAlternate = if (dark) Color(0xFF262320) else Color(0xFFF8F4EE),
        tableBorder = if (dark) Color(0xFF3A3531) else Color(0xFFE6DED2),
        glass = secondaryBackground.copy(alpha = 0.88f),
        glassStroke = if (dark) Color.White.copy(alpha = 0.07f) else Color.Black.copy(alpha = 0.06f),
        shadow = Color.Black.copy(alpha = if (dark) 0.28f else 0.12f),
        approval = if (dark) Color(0xFFFCD34D) else Color(0xFFB45309),
        input = if (dark) Color(0xFFA5B4FC) else Color(0xFF4F46E5),
        working = if (dark) Color(0xFF38BDF8) else Color(0xFF0284C7),
        danger = Color(0xFFFF3B30),
        success = Color(0xFF34C759),
        mascot = mascot,
        onMascot = OnMascot,
    )
}

val LocalHermehColors = staticCompositionLocalOf { colors(dark = false, mascot = Color(MascotColor.Sunny.argb)) }

object HermehTheme {
    val colors: HermehColors
        @Composable get() = LocalHermehColors.current
}

val MonoFamily = FontFamily.Monospace

val DisplayFamily = FontFamily.Serif

object HermehType {
    val thought = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight.Medium, fontSize = 17.sp, lineHeight = 22.sp)
    val screenTitle = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight.Medium, fontSize = 26.sp, lineHeight = 32.sp)
    val headline = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight.Medium, fontSize = 28.sp, lineHeight = 34.sp)
    val hero = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight.Medium, fontSize = 32.sp, lineHeight = 38.sp)
    val wordmark = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight.Medium, fontSize = 22.sp)
    val nameTag = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
}

object HermehShapes {
    val card = RoundedCornerShape(24.dp)
    val composer = RoundedCornerShape(28.dp)
    val sheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val tile = RoundedCornerShape(20.dp)
    val bubble = RoundedCornerShape(22.dp)
    val userBubble = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomEnd = 6.dp, bottomStart = 22.dp)
    val capsule = RoundedCornerShape(50)
}

private val HermehTypography = Typography(
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 14.sp, lineHeight = 19.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
)

@Composable
fun HermehTheme(mode: ThemeMode, mascot: MascotColor, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val palette = colors(dark, Color(mascot.argb))
    val scheme = if (dark) {
        darkColorScheme(
            primary = palette.label,
            onPrimary = palette.background,
            background = palette.background,
            onBackground = palette.label,
            surface = palette.background,
            onSurface = palette.label,
            surfaceVariant = palette.secondaryBackground,
            onSurfaceVariant = palette.secondaryLabel,
            surfaceContainer = palette.secondaryBackground,
            surfaceContainerHigh = palette.tertiaryBackground,
            outline = palette.separator,
            error = palette.danger,
        )
    } else {
        lightColorScheme(
            primary = palette.label,
            onPrimary = palette.background,
            background = palette.background,
            onBackground = palette.label,
            surface = palette.background,
            onSurface = palette.label,
            surfaceVariant = palette.secondaryBackground,
            onSurfaceVariant = palette.secondaryLabel,
            surfaceContainer = palette.secondaryBackground,
            surfaceContainerHigh = palette.tertiaryBackground,
            outline = palette.separator,
            error = palette.danger,
        )
    }
    CompositionLocalProvider(LocalHermehColors provides palette) {
        MaterialTheme(colorScheme = scheme, typography = HermehTypography, content = content)
    }
}
