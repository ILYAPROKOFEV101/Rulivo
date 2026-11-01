package com.ilya.rulivo.ar

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class FigureGeometryTest : com.ilya.rulivo.i18n.LocalizedTest() {
    private fun p(x:Double,y:Double,z:Double)=Point3D(x,y,z)
    private val box=listOf(p(0.0,0.0,0.0),p(2.0,0.0,0.0),p(1.0,0.0,3.0),p(1.0,4.0,1.0))
    private val room=listOf(p(0.0,0.0,0.0),p(4.0,0.0,0.0),p(4.0,0.0,2.0),p(2.0,0.0,2.0),p(2.0,0.0,4.0),p(0.0,0.0,4.0))
    @Test fun boxBuildsEdgeThenRectangleThenCompleteTwelveEdgeVolume() {
        val edge=FigureGeometry.evaluate(CaptureType.BOX_VOLUME,box.take(2))
        assertEquals(1,edge.edges.size);assertFalse(edge.complete)
        val base=FigureGeometry.evaluate(CaptureType.BOX_VOLUME,box.take(3))
        assertEquals(4,base.edges.size);assertTrue(base.baseValid);assertFalse(base.complete);assertNull(base.volume)
        val complete=FigureGeometry.evaluate(CaptureType.BOX_VOLUME,box)
        assertEquals(12,complete.edges.size);assertTrue(complete.complete)
        assertEquals(24.0,complete.volume!!,1e-9)
        assertEquals(mapOf("length" to 2.0,"width" to 3.0,"height" to 4.0),complete.dimensions)
        assertEquals(8,complete.edges.flatMap {listOf(it.start,it.end)}.toSet().size)
    }
    @Test fun boxRotatedWithinPlaneKeepsPerpendicularGeometryAndVolume() {
        val angle=.71
        val rotated=box.map {p(it.x*cos(angle)-it.z*sin(angle),it.y,it.x*sin(angle)+it.z*cos(angle))}
        val figure=FigureGeometry.evaluate(CaptureType.BOX_VOLUME,rotated)
        assertEquals(24.0,figure.volume!!,1e-9)
        val a=figure.edges[0];val b=figure.edges[1]
        assertEquals(0.0,(a.end.x-a.start.x)*(b.end.x-b.start.x)+(a.end.z-a.start.z)*(b.end.z-b.start.z),1e-9)
    }
    @Test fun boxCanStartOnTopFaceAndExtrudeDown() {
        val result=FigureGeometry.evaluate(CaptureType.BOX_VOLUME,box.dropLast(1)+p(1.0,-4.0,1.0))
        assertEquals(24.0,result.volume!!,0.0);assertEquals(-4.0,result.edges[4].start.y,0.0)
    }
    @Test fun wrongPlaneDegenerateBaseAndOffFootprintTopCannotComplete() {
        for(samples in listOf(listOf(box[0],p(2.0,.04,0.0),box[2],box[3]),
            listOf(box[0],box[0],box[2],box[3]),box.take(2)+p(1.0,0.0,0.0)+box[3],
            box.take(3)+p(4.0,4.0,1.0),box.take(3)+p(1.0,0.0,1.0))) {
            val result=FigureGeometry.evaluate(CaptureType.BOX_VOLUME,samples)
            assertFalse(result.complete);assertNotNull(result.error);assertNull(result.volume)
        }
    }
    @Test fun cylinderUsesRadiusAndPerpendicularHeightNotSlantDistance() {
        val samples=listOf(p(0.0,0.0,0.0),p(.5,0.0,0.0),p(.5,2.0,0.0))
        val base=FigureGeometry.evaluate(CaptureType.CYLINDER_VOLUME,samples.take(2))
        assertEquals(33,base.edges.size);assertFalse(base.complete)
        val result=FigureGeometry.evaluate(CaptureType.CYLINDER_VOLUME,samples)
        assertTrue(result.complete);assertEquals(68,result.edges.size)
        assertEquals(Math.PI*.5,result.volume!!,1e-9)
        assertEquals(1.0,result.dimensions.getValue("diameter"),0.0)
        assertEquals(2.0,result.dimensions.getValue("height"),0.0)
    }
    @Test fun cylinderRejectsBadCenterRimAndTop() {
        val a=p(0.0,0.0,0.0)
        for(samples in listOf(listOf(a,a),listOf(a,p(.5,.1,0.0)),listOf(a,p(.5,0.0,0.0),p(.7,2.0,0.0))))
            assertNotNull(FigureGeometry.evaluate(CaptureType.CYLINDER_VOLUME,samples).error)
    }
    @Test fun concaveRoomRequiresClosedFloorAndCeilingThenExtrudesEveryWall() {
        val open=FigureGeometry.evaluate(CaptureType.ROOM_VOLUME,room)
        assertEquals(12.0,open.area!!,0.0);assertTrue(open.baseValid);assertFalse(open.complete)
        val closed=FigureGeometry.evaluate(CaptureType.ROOM_VOLUME,room,6)
        assertFalse(closed.complete)
        val result=FigureGeometry.evaluate(CaptureType.ROOM_VOLUME,room+p(1.0,2.7,1.0),6)
        assertTrue(result.complete);assertEquals(18,result.edges.size)
        assertEquals(32.4,result.volume!!,1e-9);assertEquals(6,result.outline.size)
        assertEquals(16.0,result.dimensions.getValue("perimeter"),0.0)
    }
    @Test fun roomRejectsCeilingOutsideConcavityAndBelowFloor() {
        for(top in listOf(p(3.0,2.7,3.0),p(1.0,-2.7,1.0),p(1.0,0.0,1.0))) {
            val result=FigureGeometry.evaluate(CaptureType.ROOM_VOLUME,room+top,6)
            assertFalse(result.complete);assertNotNull(result.error)
        }
        assertTrue(FigureGeometry.evaluate(CaptureType.ROOM_VOLUME,room+p(2.0,2.7,3.0),6).complete)
    }
    @Test fun crossedFloorAndNonfinitePointsAreNotAccepted() {
        val crossed=listOf(p(0.0,0.0,0.0),p(2.0,0.0,2.0),p(0.0,0.0,2.0),p(2.0,0.0,0.0))
        assertFalse(FigureGeometry.evaluate(CaptureType.ROOM_VOLUME,crossed,4).baseValid)
        assertNotNull(FigureGeometry.evaluate(CaptureType.BOX_VOLUME,listOf(p(Double.NaN,0.0,0.0))).error)
    }
    @Test fun planeOutlineHasSameAreaAndEdgesAsRoomFloor() {
        val result=FigureGeometry.evaluate(CaptureType.AREA,room)
        assertTrue(result.complete);assertEquals(12.0,result.area!!,0.0);assertEquals(6,result.edges.size)
    }
}
