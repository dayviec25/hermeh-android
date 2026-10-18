package com.hermeh.android.core.designsystem

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hermeh.android.R

@Composable
fun HermehWordmark(color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .aspectRatio(643f / 185f)
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .semantics { contentDescription = "HERMEH" },
    ) {
        val fill = Modifier.matchParentSize()
        Image(
            painter = painterResource(R.drawable.hermes_fill_mask),
            contentDescription = null,
            colorFilter = ColorFilter.tint(color, BlendMode.SrcIn),
            modifier = fill,
        )
        Image(
            painter = painterResource(R.drawable.hermes_shading_overlay),
            contentDescription = null,
            modifier = fill.graphicsLayer { blendMode = BlendMode.Multiply },
        )
        Image(
            painter = painterResource(R.drawable.hermes_highlight),
            contentDescription = null,
            modifier = fill.graphicsLayer { blendMode = BlendMode.Screen },
        )
        Image(
            painter = painterResource(R.drawable.hermes_outline_shadow),
            contentDescription = null,
            modifier = fill,
        )
    }
}

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
