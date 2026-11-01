package com.ilya.rulivo.ui.components

import com.ilya.rulivo.R
import com.ilya.rulivo.i18n.text

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** A visual guide only; it never handles taps or supplies AR coordinates. */
@Composable
fun AimingGrid(exclusions: List<Rect>, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize().semantics { contentDescription = text(R.string.aiming_grid_two_vertical_and_two_horizontal) }) {
        val panels = Path().apply { exclusions.filter { it.width > 0f && it.height > 0f }.forEach { addRect(it.inflate(6.dp.toPx())) } }
        clipPath(panels, ClipOp.Difference) {
            for (fraction in listOf(1f / 3f, 2f / 3f)) {
                drawLine(Color.White.copy(alpha = 0.16f), Offset(size.width * fraction, 0f),
                    Offset(size.width * fraction, size.height), 1.dp.toPx())
                drawLine(Color.White.copy(alpha = 0.16f), Offset(0f, size.height * fraction),
                    Offset(size.width, size.height * fraction), 1.dp.toPx())
            }
        }
    }
}
