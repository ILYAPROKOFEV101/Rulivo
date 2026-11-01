package com.ilya.rulivo.ar

import com.ilya.rulivo.i18n.LocalizedTest
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class ContourGeometryTest : LocalizedTest() {
    private val door = listOf(Point3D(0.0, 0.0, -2.0), Point3D(.81, 0.0, -2.0),
        Point3D(.81, 2.0, -2.0), Point3D(0.0, 2.0, -2.0))

    @Test fun referenceDoorHasAreaPerimeterAndWorldCenter() {
        val r = ContourGeometry.evaluate(door)
        assertNull(r.error); assertEquals(1.62, r.area!!, 1e-9); assertEquals(5.62, r.perimeter!!, 1e-9)
        assertEquals(.405, r.labelPosition!!.x, 1e-9); assertEquals(1.0, r.labelPosition.y, 1e-9)
        assertEquals(-2.0, r.labelPosition.z, 1e-9); assertEquals(6, r.triangles.size)
    }

    @Test fun areaIsInvariantUnderRotationTranslationAndReversedWinding() {
        val angle = .73
        val tilted = door.map { p -> Point3D(p.x + 1000, p.y * cos(angle) - p.z * sin(angle) - 2000,
            p.y * sin(angle) + p.z * cos(angle) + 3000) }
        for (points in listOf(door.reversed(), tilted, tilted.reversed(), door.map { Point3D(it.x, it.z, it.y) })) {
            val r = ContourGeometry.evaluate(points)
            assertNull(r.error); assertEquals(1.62, r.area!!, 1e-9); assertEquals(5.62, r.perimeter!!, 1e-9)
            assertEquals(r.area!!, triangleArea(r.triangles), 1e-9)
        }
    }

    @Test fun concaveOutlineFillsOnlyItsInteriorAndKeepsLabelInside() {
        // The area centroid of this U lies in its empty notch.
        val u = listOf(0.0 to 0.0,4.0 to 0.0,4.0 to 4.0,3.0 to 4.0,
            3.0 to 1.0,1.0 to 1.0,1.0 to 4.0,0.0 to 4.0).map { Point3D(it.first, it.second, -3.0) }
        for (points in listOf(u, u.reversed())) {
            val r = ContourGeometry.evaluate(points)
            assertNull(r.error); assertEquals(10.0, r.area!!, 1e-9)
            assertEquals(10.0, triangleArea(r.triangles), 1e-9)
            val center = r.labelPosition!!
            assertTrue(center.y <= 1 || center.x <= 1 || center.x >= 3)
            for (triangle in r.triangles.chunked(3)) {
                val x = triangle.sumOf { it.x } / 3; val y = triangle.sumOf { it.y } / 3
                assertTrue(y <= 1 + 1e-9 || x <= 1 + 1e-9 || x >= 3 - 1e-9)
            }
        }
    }

    @Test fun collinearEarlyEdgesStillFindTheSurfacePlane() {
        val r = ContourGeometry.evaluate(listOf(door[0],Point3D(.4,0.0,-2.0))+door.drop(1))
        assertNull(r.error); assertEquals(1.62,r.area!!,1e-9)
        assertEquals(1.62,triangleArea(r.triangles),1e-9)
    }

    @Test fun crossedRepeatedFlatAndNonPlanarContoursHaveNoAreaOrFill() {
        val invalid = listOf(door.take(2),listOf(door[0],door[2],door[1],door[3]),
            door+door[0], listOf(door[0],door[0],door[1]),
            listOf(Point3D(0.0,0.0,0.0),Point3D(1.0,0.0,0.0),Point3D(2.0,0.0,0.0)),
            door.dropLast(1)+door.last().copy(z=-1.5),door.dropLast(1)+door.last().copy(x=Double.NaN))
        invalid.forEach { points ->
            val r=ContourGeometry.evaluate(points)
            assertNotNull(r.error); assertNull(r.area); assertNull(r.labelPosition); assertTrue(r.triangles.isEmpty())
        }
    }

    @Test fun smallSurfaceNoiseKeepsPlanarAreaAndMeasuredEdgePerimeter() {
        val points=door.dropLast(1)+door.last().copy(z=-1.995)
        val r=ContourGeometry.evaluate(points)
        assertNull(r.error); assertEquals(1.62,r.area!!,.001)
        assertEquals(points.indices.sumOf { DistanceCalculator.distanceMeters(points[it],points[(it+1)%points.size]) },r.perimeter!!,1e-9)
    }

    private fun triangleArea(triangles: List<Point3D>)=triangles.chunked(3).sumOf { t ->
        val ax=t[1].x-t[0].x;val ay=t[1].y-t[0].y;val az=t[1].z-t[0].z
        val bx=t[2].x-t[0].x;val by=t[2].y-t[0].y;val bz=t[2].z-t[0].z
        sqrt((ay*bz-az*by).pow(2)+(az*bx-ax*bz).pow(2)+(ax*by-ay*bx).pow(2))/2
    }
}
