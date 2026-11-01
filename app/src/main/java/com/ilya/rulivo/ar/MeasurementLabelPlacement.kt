package com.ilya.rulivo.ar

import kotlin.math.abs
import kotlin.math.hypot

data class LabelBounds(val left: Float, val top: Float, val width: Float, val height: Float)

/** Pixel coordinates are used only to position the caption, never to measure distance. */
object MeasurementLabelPlacement {
    fun aboveSegment(
        startX: Float, startY: Float, endX: Float, endY: Float,
        viewportWidth: Float, viewportHeight: Float,
        labelWidth: Float, labelHeight: Float, gap: Float,
        middleX: Float = (startX + endX) / 2f, middleY: Float = (startY + endY) / 2f,
        avoidBounds: LabelBounds? = null,
    ): LabelBounds? {
        if (!middleX.isFinite() || !middleY.isFinite() ||
            middleX !in 0f..viewportWidth || middleY !in 0f..viewportHeight ||
            labelWidth + gap * 2f > viewportWidth || labelHeight + gap * 2f > viewportHeight) return null

        val dx = endX - startX
        val dy = endY - startY
        val length = hypot(dx, dy)
        var nx = if (length > 0.01f) dy / length else 0f
        var ny = if (length > 0.01f) -dx / length else -1f
        // Prefer the upper side regardless of which endpoint was placed first.
        // For a vertical segment, place the upright caption to its right.
        if (ny > 0f || (ny == 0f && nx < 0f)) { nx = -nx; ny = -ny }
        val clearance = abs(nx) * labelWidth / 2f + abs(ny) * labelHeight / 2f + gap
        fun candidate(side: Float, along: Float = 0f): LabelBounds {
            val left = (middleX + side * nx * clearance - ny * along - labelWidth / 2f)
                .coerceIn(gap, viewportWidth - labelWidth - gap)
            val top = (middleY + side * ny * clearance + nx * along - labelHeight / 2f)
                .coerceIn(gap, viewportHeight - labelHeight - gap)
            return LabelBounds(left, top, labelWidth, labelHeight)
        }
        // Clamping near a screen edge must not push the caption back across its own line.
        val preferredSide = if (avoidBounds != null &&
            (avoidBounds.left + avoidBounds.width / 2f - middleX) * nx +
            (avoidBounds.top + avoidBounds.height / 2f - middleY) * ny > 0) -1f else 1f
        val candidates = mutableListOf(candidate(preferredSide), candidate(-preferredSide))
        if (avoidBounds != null) {
            // At a viewport edge, move along the side to leave the area caption readable.
            val along = abs(ny) * (avoidBounds.width + labelWidth) / 2f +
                abs(nx) * (avoidBounds.height + labelHeight) / 2f + gap
            for (offset in listOf(-along, along)) {
                candidates.add(candidate(preferredSide, offset))
                candidates.add(candidate(-preferredSide, offset))
            }
        }
        return candidates.firstOrNull { bounds ->
            !intersects(startX, startY, endX, endY, bounds, gap / 2f) &&
                (avoidBounds == null || bounds.left + bounds.width + gap <= avoidBounds.left ||
                    bounds.left >= avoidBounds.left + avoidBounds.width + gap ||
                    bounds.top + bounds.height + gap <= avoidBounds.top ||
                    bounds.top >= avoidBounds.top + avoidBounds.height + gap)
        }
    }

    private fun intersects(ax: Float, ay: Float, bx: Float, by: Float, bounds: LabelBounds, margin: Float): Boolean {
        var enter = 0f
        var exit = 1f
        fun slab(start: Float, delta: Float, low: Float, high: Float): Boolean {
            if (abs(delta) < 0.00001f) return start in low..high
            val first = (low - start) / delta
            val second = (high - start) / delta
            enter = maxOf(enter, minOf(first, second))
            exit = minOf(exit, maxOf(first, second))
            return enter <= exit
        }
        return slab(ax, bx - ax, bounds.left - margin, bounds.left + bounds.width + margin) &&
            slab(ay, by - ay, bounds.top - margin, bounds.top + bounds.height + margin)
    }
}
