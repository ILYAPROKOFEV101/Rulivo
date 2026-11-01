package com.ilya.rulivo.ar

internal enum class SurfaceKind { PLANE, DEPTH }

internal data class SurfaceHit<T>(val value: T, val kind: SurfaceKind, val distanceMeters: Double)

internal object SurfaceHitSelection {
    /** Prefer a fitted plane only at the same depth, never the wall/floor behind an object. */
    fun <T> nearestSurface(hits: List<SurfaceHit<T>>): T? {
        val valid = hits.filter { it.distanceMeters.isFinite() && it.distanceMeters > 0.0 }
        val nearest = valid.minByOrNull { it.distanceMeters } ?: return null
        return (valid.filter {
            it.kind == SurfaceKind.PLANE && it.distanceMeters <= nearest.distanceMeters + 0.02
        }.minByOrNull { it.distanceMeters } ?: nearest).value
    }
}
