package com.ilya.rulivo.ar

import kotlin.math.sqrt

data class Point3D(val x: Double, val y: Double, val z: Double)

object DistanceCalculator {
    fun distanceMeters(first: Point3D, second: Point3D): Double {
        val dx = second.x - first.x
        val dy = second.y - first.y
        val dz = second.z - first.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    fun metersToCentimeters(meters: Double): Double = meters * 100.0
}
