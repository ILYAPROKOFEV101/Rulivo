package com.ilya.rulivo.ui.components

import com.ilya.rulivo.R
import com.ilya.rulivo.i18n.text

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ilya.rulivo.ui.measurement.CameraMotionCue
import com.ilya.rulivo.ui.theme.CameraPanel
import com.ilya.rulivo.ui.theme.Lime
import com.ilya.rulivo.ui.theme.Muted
import kotlin.math.cos
import kotlin.math.sin

/** Vector tutorial, animated only while its camera screen is visible and resumed. */
@Composable
internal fun CameraMotionGuide(cue: CameraMotionCue, modifier: Modifier = Modifier,
    animationsEnabled: Boolean = true, showCaption: Boolean = true) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var resumed by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ -> resumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    // Compose animation respects the system animator duration scale, including disabled animations.
    val phase = if (animationsEnabled && resumed && cue != CameraMotionCue.READY) {
        key(cue) {
            rememberInfiniteTransition(label = "Camera gesture").animateFloat(
                initialValue = -1f, targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing),
                    if (cue == CameraMotionCue.RETURN || cue == CameraMotionCue.AIM) RepeatMode.Restart else RepeatMode.Reverse),
                label = "Slow camera movement")
        }
    } else rememberUpdatedState(0f)

    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(Modifier.fillMaxWidth().height(72.dp).testTag("camera-motion-${cue.name}")
            .semantics { contentDescription = text(R.string.movement_guide, cue.caption) }) {
            // Read animation state in the draw phase; AR state and text do not recompose every frame.
            val factor = minOf(size.width / 240f, size.height / 80f)
            translate((size.width - 240f * factor) / 2f, (size.height - 80f * factor) / 2f) {
                scale(factor, pivot = Offset.Zero) { drawGesture(cue, phase.value) }
            }
        }
        if (showCaption) Text(cue.caption, color = Muted, style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
    }
}

private fun DrawScope.drawGesture(cue: CameraMotionCue, phase: Float) {
    val amount = (phase + 1f) / 2f
    when (cue) {
        CameraMotionCue.SWEEP, CameraMotionCue.DETAILS -> {
            phone(Offset(77f, 40f), alpha = .16f)
            phone(Offset(163f, 40f), alpha = .16f)
            arrow(Offset(66f, 40f), Offset(28f, 40f), .5f + .5f * (1f - amount))
            arrow(Offset(174f, 40f), Offset(212f, 40f), .5f + .5f * amount)
            phone(Offset(120f + phase * 43f, 40f), details = true)
        }
        CameraMotionCue.STEADY -> {
            drawCircle(Lime.copy(alpha = .08f + amount * .1f), 33f + amount * 4f, Offset(120f, 40f))
            phone(Offset(120f, 40f))
            line(Offset(181f, 28f), Offset(181f, 52f), Lime, 5f)
            line(Offset(193f, 28f), Offset(193f, 52f), Lime, 5f)
        }
        CameraMotionCue.RETURN -> {
            drawRoundRect(Lime.copy(alpha = .6f), Offset(104f, 9f), Size(32f, 62f), CornerRadius(7f),
                style = Stroke(1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))))
            arrow(Offset(188f, 40f), Offset(153f, 40f), .8f)
            phone(Offset(120f + (1f - amount) * 42f, 40f), alpha = .6f + amount * .4f, details = true)
        }
        CameraMotionCue.LIGHT -> {
            phone(Offset(104f, 40f), details = true)
            val sun = Offset(171f, 29f)
            drawCircle(Lime.copy(alpha = .1f + amount * .14f), 22f, sun)
            drawCircle(Lime, 8f, sun, style = Stroke(2f))
            repeat(8) { index ->
                val angle = index * Math.PI / 4
                val direction = Offset(cos(angle).toFloat(), sin(angle).toFloat())
                line(sun + direction * 13f, sun + direction * 17f, Lime)
            }
        }
        CameraMotionCue.AIM -> {
            phone(Offset(120f, 40f))
            val target = Offset(120f, 39f)
            drawCircle(Lime.copy(alpha = .3f), 4f, target)
            val aim = target + Offset((1f - amount) * 9f, (1f - amount) * 14f)
            drawCircle(Lime, 7f, aim, style = Stroke(1.5f))
            line(aim - Offset(10f, 0f), aim + Offset(10f, 0f), Lime, 1f)
            line(aim - Offset(0f, 10f), aim + Offset(0f, 10f), Lime, 1f)
        }
        CameraMotionCue.HEIGHT -> {
            phone(Offset(120f, 40f + phase * 7f))
            arrow(Offset(79f, 49f), Offset(79f, 12f), 1f - amount * .5f)
            arrow(Offset(161f, 31f), Offset(161f, 68f), .5f + amount * .5f)
            line(Offset(110f, 40f + phase * 15f), Offset(130f, 40f + phase * 15f), Lime, 3f)
        }
        CameraMotionCue.READY -> {
            phone(Offset(105f, 40f), details = true)
            drawCircle(Lime.copy(alpha = .13f), 21f, Offset(169f, 40f))
            line(Offset(158f, 40f), Offset(166f, 48f), Lime, 3f)
            line(Offset(166f, 48f), Offset(182f, 31f), Lime, 3f)
        }
    }
}

private fun DrawScope.phone(at: Offset, alpha: Float = 1f, details: Boolean = false) {
    val origin = at - Offset(17f, 30f)
    drawRoundRect(CameraPanel.copy(alpha = alpha), origin, Size(34f, 60f), CornerRadius(7f))
    drawRoundRect(Color.White.copy(alpha = alpha), origin, Size(34f, 60f), CornerRadius(7f), style = Stroke(2f))
    line(at + Offset(-5f, -24f), at + Offset(5f, -24f), Color.White.copy(alpha = alpha), 2f)
    line(at + Offset(-5f, 24f), at + Offset(5f, 24f), Color.White.copy(alpha = alpha * .6f), 2f)
    if (details) {
        val plane = Path().apply {
            moveTo(at.x - 11f, at.y + 10f); lineTo(at.x + 11f, at.y + 10f)
            lineTo(at.x + 7f, at.y - 8f); lineTo(at.x - 7f, at.y - 8f); close()
        }
        drawPath(plane, Lime.copy(alpha = alpha * .12f))
        drawPath(plane, Lime.copy(alpha = alpha * .7f), style = Stroke(1f))
        for (offset in listOf(Offset(-7f, -8f), Offset(7f, -8f), Offset(-11f, 10f), Offset(11f, 10f)))
            drawCircle(Lime.copy(alpha = alpha), 1.7f, at + offset)
    }
}

private fun DrawScope.arrow(from: Offset, to: Offset, alpha: Float) {
    val color = Lime.copy(alpha = alpha)
    line(from, to, color)
    val delta = to - from
    val length = delta.getDistance()
    val direction = delta / length
    val wing = Offset(-direction.y, direction.x) * 5f
    line(to, to - direction * 7f + wing, color)
    line(to, to - direction * 7f - wing, color)
}

private fun DrawScope.line(from: Offset, to: Offset, color: Color, width: Float = 2f) =
    drawLine(color, from, to, width, cap = StrokeCap.Round)
