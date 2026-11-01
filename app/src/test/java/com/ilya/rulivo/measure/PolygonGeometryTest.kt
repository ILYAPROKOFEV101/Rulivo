package com.ilya.rulivo.measure

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class PolygonGeometryTest : com.ilya.rulivo.i18n.LocalizedTest() {
    private val concave=listOf(Point2(0.0,0.0),Point2(4.0,0.0),Point2(4.0,2.0),Point2(2.0,2.0),Point2(2.0,4.0),Point2(0.0,4.0))
    @Test fun areaIsInvariantToOrientationTranslationAndRotation() {
        val a=.83
        listOf(concave,concave.reversed(),concave.map {Point2(it.x*cos(a)-it.y*sin(a)+1e6,it.x*sin(a)+it.y*cos(a)-1e6)}).forEach {
            assertNull(PolygonGeometry.validate(it))
            assertEquals(12.0,PolygonGeometry.calculate(it).area,1e-8)
            assertEquals(16.0,PolygonGeometry.calculate(it).perimeter,1e-8)
        }
    }
    @Test fun crossingTouchingDuplicateAndOverlappingEdgesAreRejected() {
        val polygons=listOf(
            listOf(Point2(0.0,0.0),Point2(2.0,2.0),Point2(0.0,2.0),Point2(2.0,0.0)),
            concave+concave.first(),
            listOf(Point2(0.0,0.0),Point2(3.0,0.0),Point2(1.0,0.0),Point2(1.0,2.0)),
            listOf(Point2(0.0,0.0),Point2(2.0,0.0),Point2(2.0,2.0),Point2(1.0,0.0),Point2(0.0,2.0)),
            listOf(Point2(0.0,0.0),Point2(1.0,0.0),Point2(2.0,0.0)),
            listOf(Point2(Double.NaN,0.0),Point2(1.0,0.0),Point2(2.0,2.0)))
        polygons.forEach {assertNotNull("Invalid polygon accepted: $it",PolygonGeometry.validate(it))}
    }
    @Test fun redundantStraightVertexDoesNotInvalidateSimpleContour() {
        val p=listOf(Point2(0.0,0.0),Point2(1.0,0.0),Point2(2.0,0.0),Point2(2.0,2.0),Point2(0.0,2.0))
        assertEquals(4.0,PolygonGeometry.calculate(p).area,0.0)
    }
}
