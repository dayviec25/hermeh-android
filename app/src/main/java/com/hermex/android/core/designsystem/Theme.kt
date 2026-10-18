package com.hermex.android.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.hermex.android.core.data.LogoColor
import com.hermex.android.core.data.ThemeMode

@Immutable
data class HermexColors(
    val isDark: Boolean,
    val background: Color,
    val secondaryBackground: Color,
    val tertiaryBackground: Color,
    val label: Color,
    val secondaryLabel: Color,
    val tertiaryLabel: Color,
    val separator: Color,
    val accent: Color,
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
    val logo: Color,
    val onLogo: Color,
)

object Onboarding {
    val background = Color(0xFF000000)
    val amber = Color(0xFFFFBD1A)
    val success = Color(0xFF73EB8F)
    val error = Color(0xFFFF7857)
    val glow = Color(0xFFFF9E14)
    val glowInner = Color(0xFFFFC72E)
}

private fun colors(dark: Boolean, logo: Color): HermexColors {
    val label = if (dark) Color.White else Color.Black
    return HermexColors(
        isDark = dark,
        background = if (dark) Color(0xFF000000) else Color(0xFFFFFFFF),
        secondaryBackground = if (dark) Color(0xFF1C1C1E) else Color(0xFFF2F2F7),
        tertiaryBackground = if (dark) Color(0xFF2C2C2E) else Color(0xFFFFFFFF),
        label = label,
        secondaryLabel = label.copy(alpha = 0.6f),
        tertiaryLabel = label.copy(alpha = 0.3f),
        separator = if (dark) Color(0xFF38383A) else Color(0xFFC6C6C8),
        accent = if (dark) Color(0xFF0A84FF) else Color(0xFF007AFF),
        userBubble = if (dark) Color(0xFF48484A) else Color(0xFFF2F2F7),
        userBubbleStroke = if (dark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.04f),
        codeBackground = if (dark) Color(0xFF0A0D12) else Color(0xFFF2F2F7),
        inlineCode = if (dark) Color(0xFF14171F) else Color(0xFFEFEFF4),
        tableBackground = if (dark) Color(0xFF18191D) else Color(0xFFFFFFFF),
        tableAlternate = if (dark) Color(0xFF25262A) else Color(0xFFF7F7F9),
        tableBorder = if (dark) Color(0xFF42444E) else Color(0xFFE4E4E8),
        glass = (if (dark) Color(0xFF1C1C1E) else Color(0xFFF2F2F7)).copy(alpha = 0.85f),
        glassStroke = if (dark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f),
        shadow = Color.Black.copy(alpha = if (dark) 0.28f else 0.12f),
        approval = if (dark) Color(0xFFFCD34D) else Color(0xFFB45309),
        input = if (dark) Color(0xFFA5B4FC) else Color(0xFF4F46E5),
        working = if (dark) Color(0xFF38BDF8) else Color(0xFF0284C7),
        danger = Color(0xFFFF3B30),
        success = Color(0xFF34C759),
        logo = logo,
        onLogo = if (logo.luminanceApprox() > 0.62f) Color.Black else Color.White,
    )
}

private fun Color.luminanceApprox(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue

val LocalHermexColors = staticCompositionLocalOf { colors(dark = false, logo = Color(0xFFFFD700)) }

object HermexTheme {
    val colors: HermexColors
        @Composable get() = LocalHermexColors.current
}

val MonoFamily = FontFamily.Monospace

private val HermexTypography = Typography(
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
fun HermexTheme(mode: ThemeMode, logo: LogoColor, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val palette = colors(dark, Color(logo.argb))
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
    CompositionLocalProvider(LocalHermexColors provides palette) {
        MaterialTheme(colorScheme = scheme, typography = HermexTypography, content = content)
    }
}
