package com.ilya.rulivo.ui.components

import com.ilya.rulivo.R
import com.ilya.rulivo.i18n.text

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ilya.rulivo.ui.theme.Lime

@Composable
fun CenterReticle(ready: Boolean, hasTarget: Boolean, modifier: Modifier = Modifier) {
    val tint = when { ready -> Lime; else -> Color(0xFFB0B8B8) }
    Canvas(modifier.size(52.dp).semantics { contentDescription = when {
        ready -> text(R.string.crosshair_ready_to_place_a_point)
        hasTarget -> text(R.string.crosshair_surface_found_point_placement_not_ready)
        else -> text(R.string.crosshair_searching_for_a_surface)
    } }) {
        val radius = size.minDimension / 3f
        drawCircle(Color.Black.copy(alpha = 0.6f), radius + 2.dp.toPx(), style = Stroke(4.dp.toPx()))
        drawCircle(tint, radius, style = Stroke(2.dp.toPx()))
        drawCircle(Color.Black, 4.dp.toPx())
        drawCircle(Color.White, 2.dp.toPx())
        val gap = radius + 4.dp.toPx()
        val tip = size.minDimension / 2f
        listOf(Offset(1f, 0f), Offset(-1f, 0f), Offset(0f, 1f), Offset(0f, -1f)).forEach { direction ->
            drawLine(tint, center + direction * gap, center + direction * tip, 2.dp.toPx())
        }
    }
}
