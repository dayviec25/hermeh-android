package com.hermeh.android.core.designsystem

import android.animation.ValueAnimator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.hermeh.android.core.model.Mascot
import com.hermeh.android.core.model.MascotArt
import com.hermeh.android.core.model.MascotMood
import kotlinx.coroutines.delay
import kotlin.random.Random

private val Ink = Color(0xFF1F1B16)
private val Blush = Color(0xFFFF8A8A)

fun MascotMood.words(): String = when (this) {
    MascotMood.Idle -> "relaxing"
    MascotMood.Happy -> "happy"
    MascotMood.Working -> "working"
    MascotMood.WaitingForYou -> "waiting for you"
    MascotMood.Offline -> "offline"
}

@Composable
fun Mascot(
    mascot: Mascot,
    color: Color,
    mood: MascotMood,
    size: Dp,
    modifier: Modifier = Modifier,
    name: String = mascot.title,
    animated: Boolean = true,
) {
    val colors = HermehTheme.colors
    val stroke = colors.label.copy(alpha = 0.12f)
    val badge = colors.accent
    val sleepy = colors.label.copy(alpha = 0.55f)
    val live = animated && remember { ValueAnimator.areAnimatorsEnabled() }
    val still = remember { mutableFloatStateOf(0f) }
    val bob: State<Float>
    val flap: State<Float>
    val blink: State<Float>
    if (live) {
        val transition = rememberInfiniteTransition(label = "mascot")
        bob = transition.animateFloat(-1f, 1f, infiniteRepeatable(tween(1_300, easing = EaseInOut), RepeatMode.Reverse), label = "bob")
        flap = if (mood == MascotMood.Working) {
            transition.animateFloat(0f, 1f, infiniteRepeatable(tween(150, easing = LinearEasing), RepeatMode.Reverse), label = "flap")
        } else {
            still
        }
        val blinking = remember { Animatable(0f) }
        LaunchedEffect(blinking) {
            while (true) {
                delay(Random.nextLong(3_500, 5_500))
                blinking.animateTo(1f, tween(70))
                blinking.animateTo(0f, tween(90))
            }
        }
        blink = blinking.asState()
    } else {
        bob = still
        flap = still
        blink = still
    }
    Box(modifier.size(size)) {
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer { translationY = bob.value * 0.03f * this.size.height }
                .semantics { contentDescription = "$name, ${mood.words()}" }
                .testTag("mascot"),
        ) {
            when (mascot.art) {
                MascotArt.WingedBlob -> drawWingedBlob(color, mood, stroke, badge, sleepy, flap.value, blink.value)
            }
        }
    }
}

private fun DrawScope.drawWingedBlob(
    color: Color,
    mood: MascotMood,
    wingStroke: Color,
    badge: Color,
    sleepy: Color,
    flap: Float,
    blink: Float,
) {
    val s = size.minDimension
    val ox = (size.width - s) / 2f
    val oy = (size.height - s) / 2f
    val bw = 0.74f * s
    val bh = bw / 1.08f
    val cx = ox + s / 2f
    val left = cx - bw / 2f
    val top = oy + 0.56f * s - bh / 2f
    val hairline = (s * 0.012f).coerceAtLeast(1f)

    val anchor = Offset(left + 0.08f * bw, top + 0.28f * bh)
    val flapDegrees = flap * 16f
    drawWing(anchor, s, flapDegrees, wingStroke, hairline)
    scale(-1f, 1f, pivot = Offset(cx, anchor.y)) {
        drawWing(anchor, s, flapDegrees, wingStroke, hairline)
    }

    val shade = Color(color.red * 0.88f, color.green * 0.88f, color.blue * 0.88f, color.alpha)
    drawRoundRect(
        brush = Brush.verticalGradient(0f to color, 0.55f to color, 1f to shade, startY = top, endY = top + bh),
        topLeft = Offset(left, top),
        size = Size(bw, bh),
        cornerRadius = CornerRadius(bw * 0.44f, bh * 0.47f),
    )
    rotate(-24f, pivot = Offset(left + 0.27f * bw, top + 0.17f * bh)) {
        drawOval(
            Color.White.copy(alpha = 0.5f),
            topLeft = Offset(left + 0.17f * bw, top + 0.12f * bh),
            size = Size(0.2f * bw, 0.1f * bh),
        )
    }

    val eyeY = top + 0.47f * bh
    val eyeDx = 0.19f * bw
    val line = 0.026f * s
    for (side in listOf(-1f, 1f)) {
        drawOval(
            Blush.copy(alpha = 0.35f),
            topLeft = Offset(cx + side * 0.30f * bw - 0.055f * s, eyeY + 0.065f * s),
            size = Size(0.11f * s, 0.06f * s),
        )
    }
    for (side in listOf(-1f, 1f)) {
        val ex = cx + side * eyeDx
        when (mood) {
            MascotMood.Happy -> drawArc(
                Ink,
                startAngle = 200f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = Offset(ex - 0.05f * s, eyeY - 0.03f * s),
                size = Size(0.1f * s, 0.08f * s),
                style = Stroke(width = line, cap = StrokeCap.Round),
            )
            MascotMood.Offline -> drawLine(
                Ink,
                start = Offset(ex - 0.04f * s, eyeY + 0.005f * s),
                end = Offset(ex + 0.04f * s, eyeY + 0.005f * s),
                strokeWidth = line,
                cap = StrokeCap.Round,
            )
            MascotMood.Idle -> drawEye(Offset(ex, eyeY), s, 0.11f, blink)
            MascotMood.Working -> drawEye(Offset(ex + 0.028f * s, eyeY - 0.03f * s), s, 0.105f, blink)
            MascotMood.WaitingForYou -> drawEye(Offset(ex, eyeY - 0.018f * s), s, 0.128f, blink)
        }
    }

    val mouthY = eyeY + 0.12f * s
    when (mood) {
        MascotMood.Idle -> drawArc(
            Ink,
            startAngle = 20f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(cx - 0.055f * s, mouthY - 0.045f * s),
            size = Size(0.11f * s, 0.06f * s),
            style = Stroke(width = 0.024f * s, cap = StrokeCap.Round),
        )
        MascotMood.Happy -> {
            val mouth = Path().apply {
                moveTo(cx - 0.085f * s, mouthY - 0.03f * s)
                quadraticTo(cx, mouthY - 0.012f * s, cx + 0.085f * s, mouthY - 0.03f * s)
                quadraticTo(cx + 0.075f * s, mouthY + 0.07f * s, cx, mouthY + 0.07f * s)
                quadraticTo(cx - 0.075f * s, mouthY + 0.07f * s, cx - 0.085f * s, mouthY - 0.03f * s)
                close()
            }
            drawPath(mouth, Ink)
            drawOval(Blush, topLeft = Offset(cx - 0.035f * s, mouthY + 0.025f * s), size = Size(0.07f * s, 0.038f * s))
        }
        MascotMood.Working -> drawCircle(Ink, radius = 0.026f * s, center = Offset(cx + 0.01f * s, mouthY), style = Stroke(width = 0.02f * s))
        MascotMood.WaitingForYou -> drawArc(
            Ink,
            startAngle = 30f,
            sweepAngle = 120f,
            useCenter = false,
            topLeft = Offset(cx - 0.04f * s, mouthY - 0.035f * s),
            size = Size(0.08f * s, 0.045f * s),
            style = Stroke(width = 0.022f * s, cap = StrokeCap.Round),
        )
        MascotMood.Offline -> drawLine(
            Ink,
            start = Offset(cx - 0.03f * s, mouthY),
            end = Offset(cx + 0.03f * s, mouthY),
            strokeWidth = 0.022f * s,
            cap = StrokeCap.Round,
        )
    }

    when (mood) {
        MascotMood.WaitingForYou -> {
            val center = Offset(ox + 0.86f * s, top + 0.02f * s)
            val radius = 0.095f * s
            drawCircle(badge, radius = radius, center = center)
            drawLine(
                Color.White,
                start = Offset(center.x, center.y - 0.048f * s),
                end = Offset(center.x, center.y + 0.01f * s),
                strokeWidth = 0.03f * s,
                cap = StrokeCap.Round,
            )
            drawCircle(Color.White, radius = 0.017f * s, center = Offset(center.x, center.y + 0.045f * s))
        }
        MascotMood.Offline -> {
            drawZ(Offset(ox + 0.78f * s, top - 0.07f * s), 0.09f * s, sleepy, 0.022f * s)
            drawZ(Offset(ox + 0.9f * s, top - 0.15f * s), 0.06f * s, sleepy.copy(alpha = sleepy.alpha * 0.7f), 0.018f * s)
        }
        MascotMood.Idle, MascotMood.Happy, MascotMood.Working -> Unit
    }
}

private fun DrawScope.drawWing(anchor: Offset, s: Float, flapDegrees: Float, stroke: Color, hairline: Float) {
    val feathers = listOf(
        Triple(0.23f, 38f, 0.095f),
        Triple(0.18f, 14f, 0.085f),
        Triple(0.13f, -10f, 0.075f),
    )
    for ((length, angle, thickness) in feathers.asReversed()) {
        val l = length * s
        val t = thickness * s
        rotate(angle + flapDegrees, pivot = anchor) {
            val topLeft = Offset(anchor.x - l, anchor.y - t / 2f)
            val oval = Size(l + t * 0.4f, t)
            drawOval(Color.White, topLeft = topLeft, size = oval)
            drawOval(stroke, topLeft = topLeft, size = oval, style = Stroke(width = hairline))
        }
    }
}

private fun DrawScope.drawEye(center: Offset, s: Float, height: Float, blink: Float) {
    val w = 0.085f * s
    val h = height * s * (1f - 0.88f * blink)
    drawOval(Ink, topLeft = Offset(center.x - w / 2f, center.y - h / 2f), size = Size(w, h))
    if (blink < 0.5f) {
        drawCircle(Color.White, radius = 0.019f * s, center = Offset(center.x + 0.014f * s, center.y - h * 0.22f))
    }
}

private fun DrawScope.drawZ(origin: Offset, size: Float, color: Color, width: Float) {
    val path = Path().apply {
        moveTo(origin.x, origin.y)
        lineTo(origin.x + size, origin.y)
        lineTo(origin.x, origin.y + size)
        lineTo(origin.x + size, origin.y + size)
    }
    drawPath(path, color, style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round))
}
