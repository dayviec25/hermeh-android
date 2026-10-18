package com.hermeh.android.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun Modifier.glassSurface(shape: Shape, elevated: Boolean = false): Modifier {
    val colors = HermehTheme.colors
    val base = if (elevated) this.shadow(14.dp, shape, ambientColor = colors.shadow, spotColor = colors.shadow) else this
    return base
        .clip(shape)
        .background(colors.glass, shape)
        .border(1.dp, colors.glassStroke, shape)
}

@Composable
fun GlassCircleButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
) = GlassCircleButton(rememberVectorPainter(icon), contentDescription, onClick, modifier, size)

@Composable
fun GlassCircleButton(
    icon: Painter,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .glassSurface(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = HermehTheme.colors.label, modifier = Modifier.size(22.dp))
    }
}

val Radius8 = RoundedCornerShape(8.dp)
