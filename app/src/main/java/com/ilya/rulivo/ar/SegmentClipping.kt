package com.ilya.rulivo.ar

internal data class ClipPoint(val x: Float, val y: Float, val z: Float, val w: Float) {
    fun screen(): FloatArray? = if (w > 0f && z >= -w && z <= w)
        floatArrayOf(x / w, y / w) else null

    fun mix(other: ClipPoint, t: Float) = ClipPoint(
        x + (other.x - x) * t, y + (other.y - y) * t,
        z + (other.z - z) * t, w + (other.w - w) * t,
    )
}

/** Clip before dividing by W so a point behind the camera cannot flip the entire line. */
internal object SegmentClipping {
    fun visiblePart(a: ClipPoint, b: ClipPoint): Pair<ClipPoint, ClipPoint>? {
        val from = floatArrayOf(a.w + a.x, a.w - a.x, a.w + a.y, a.w - a.y, a.w + a.z, a.w - a.z)
        val to = floatArrayOf(b.w + b.x, b.w - b.x, b.w + b.y, b.w - b.y, b.w + b.z, b.w - b.z)
        if (from.any { !it.isFinite() } || to.any { !it.isFinite() }) return null
        var enter = 0f
        var exit = 1f
        for (index in from.indices) {
            val start = from[index]
            val end = to[index]
            if (start < 0f && end < 0f) return null
            if (start < 0f) enter = maxOf(enter, start / (start - end))
            if (end < 0f) exit = minOf(exit, start / (start - end))
            if (enter > exit) return null
        }
        return a.mix(b, enter) to a.mix(b, exit)
    }
}
