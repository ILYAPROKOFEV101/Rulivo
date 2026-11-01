package com.ilya.rulivo.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import com.ilya.rulivo.measure.FloorShape
import com.ilya.rulivo.measure.MeasureKind

/** Shared, resolution-independent silhouettes for measurement choices. */
internal object MeasurementIcons {
    val Line = figure("Line") {
        moveTo(7f, 25f); lineTo(25f, 7f)
        moveTo(4f, 22f); lineTo(10f, 28f)
        moveTo(22f, 4f); lineTo(28f, 10f)
        moveTo(13f, 14f); lineTo(16f, 17f)
        moveTo(18f, 9f); lineTo(21f, 12f)
    }

    val Box = figure("Box") {
        moveTo(16f, 3f); lineTo(28f, 9f); lineTo(28f, 23f)
        lineTo(16f, 29f); lineTo(4f, 23f); lineTo(4f, 9f); close()
        moveTo(4f, 9f); lineTo(16f, 15f); lineTo(28f, 9f)
        moveTo(16f, 15f); lineTo(16f, 29f)
    }

    val Cylinder = figure("Cylinder") {
        moveTo(27f, 8f)
        curveTo(27f, 10.8f, 22f, 13f, 16f, 13f)
        curveTo(10f, 13f, 5f, 10.8f, 5f, 8f)
        curveTo(5f, 5.2f, 10f, 3f, 16f, 3f)
        curveTo(22f, 3f, 27f, 5.2f, 27f, 8f); close()
        moveTo(5f, 8f); lineTo(5f, 24f)
        curveTo(5f, 26.8f, 10f, 29f, 16f, 29f)
        curveTo(22f, 29f, 27f, 26.8f, 27f, 24f); lineTo(27f, 8f)
    }

    val Area = figure("Area") {
        moveTo(8f, 5f); lineTo(27f, 5f); lineTo(27f, 19f)
        lineTo(19f, 19f); lineTo(19f, 27f); lineTo(8f, 27f); close()
        moveTo(13f, 11f); lineTo(16f, 8f)
        moveTo(13f, 18f); lineTo(23f, 8f)
        moveTo(13f, 24f); lineTo(16f, 21f)
    }

    val Room = figure("Room") {
        moveTo(4f, 9f); lineTo(16f, 3f); lineTo(28f, 9f)
        lineTo(28f, 23f); lineTo(16f, 29f); lineTo(4f, 23f); close()
        moveTo(16f, 3f); lineTo(16f, 17f)
        moveTo(4f, 23f); lineTo(16f, 17f); lineTo(28f, 23f)
        moveTo(8f, 25f); lineTo(20f, 19f)
        moveTo(12f, 27f); lineTo(24f, 21f)
    }

    val Fit = figure("Fit") {
        moveTo(4f, 12f); lineTo(4f, 5f); lineTo(11f, 5f)
        moveTo(21f, 5f); lineTo(28f, 5f); lineTo(28f, 12f)
        moveTo(28f, 21f); lineTo(28f, 28f); lineTo(21f, 28f)
        moveTo(11f, 28f); lineTo(4f, 28f); lineTo(4f, 21f)
        moveTo(16f, 10f); lineTo(23f, 13.5f); lineTo(23f, 21f)
        lineTo(16f, 24.5f); lineTo(9f, 21f); lineTo(9f, 13.5f); close()
        moveTo(9f, 13.5f); lineTo(16f, 17f); lineTo(23f, 13.5f)
        moveTo(16f, 17f); lineTo(16f, 24.5f)
    }

    val Materials = figure("Materials") {
        moveTo(16f, 4f); lineTo(29f, 11f); lineTo(16f, 18f)
        lineTo(3f, 11f); close()
        moveTo(9.5f, 7.5f); lineTo(22.5f, 14.5f)
        moveTo(22.5f, 7.5f); lineTo(9.5f, 14.5f)
        moveTo(3f, 17f); lineTo(16f, 24f); lineTo(29f, 17f)
        moveTo(3f, 23f); lineTo(16f, 30f); lineTo(29f, 23f)
    }

    private val Rectangle = figure("Rectangle") {
        moveTo(4f, 7f); lineTo(28f, 7f); lineTo(28f, 25f)
        lineTo(4f, 25f); close()
    }

    private val LShape = figure("LShape") {
        moveTo(5f, 4f); lineTo(15f, 4f); lineTo(15f, 17f)
        lineTo(27f, 17f); lineTo(27f, 28f); lineTo(5f, 28f); close()
    }

    private val Contour = figure("Contour") {
        moveTo(4f, 12f); lineTo(15f, 4f); lineTo(28f, 9f)
        lineTo(23f, 27f); lineTo(8f, 25f); close()
    }

    fun forKind(kind: MeasureKind): ImageVector = when (kind) {
        MeasureKind.BOX -> Box
        MeasureKind.CYLINDER -> Cylinder
        MeasureKind.AREA -> Area
        MeasureKind.ROOM -> Room
        MeasureKind.FIT -> Fit
        MeasureKind.MATERIALS -> Materials
    }

    fun forShape(shape: FloorShape): ImageVector = when (shape) {
        FloorShape.RECTANGLE -> Rectangle
        FloorShape.L_SHAPE -> LShape
        FloorShape.CONTOUR -> Contour
    }

    private fun figure(name: String, drawing: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 32.dp, 32.dp, 32f, 32f).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathBuilder = drawing,
            )
        }.build()
}
