package com.ilya.rulivo.ar

import org.junit.Assert.assertEquals
import org.junit.Test

class DistanceCalculatorTest {
    @Test fun oneMeterAlongX() {
        assertEquals(1.0, DistanceCalculator.distanceMeters(Point3D(0.0, 0.0, 0.0), Point3D(1.0, 0.0, 0.0)), 1e-9)
    }

    @Test fun halfMeterAlongY() {
        assertEquals(0.5, DistanceCalculator.distanceMeters(Point3D(0.0, 0.0, 0.0), Point3D(0.0, 0.5, 0.0)), 1e-9)
    }

    @Test fun depthAlongZ() {
        assertEquals(0.8, DistanceCalculator.distanceMeters(Point3D(0.0, 0.0, -0.2), Point3D(0.0, 0.0, -1.0)), 1e-9)
    }

    @Test fun allThreeAxesAndNegativeCoordinates() {
        assertEquals(13.0, DistanceCalculator.distanceMeters(Point3D(-1.0, -2.0, -3.0), Point3D(2.0, 2.0, 9.0)), 1e-9)
    }

    @Test fun identicalPointsGiveZero() {
        val point = Point3D(2.0, -3.0, 4.0)
        assertEquals(0.0, DistanceCalculator.distanceMeters(point, point), 0.0)
    }

    @Test fun distanceIsSymmetric() {
        val a = Point3D(1.2, -3.0, 4.0)
        val b = Point3D(-3.0, 5.0, 1.0)
        assertEquals(DistanceCalculator.distanceMeters(a, b), DistanceCalculator.distanceMeters(b, a), 0.0)
    }

    @Test fun metersToCentimeters() {
        assertEquals(50.0, DistanceCalculator.metersToCentimeters(0.5), 1e-9)
        assertEquals(142.0, DistanceCalculator.metersToCentimeters(1.42), 1e-9)
    }
}
