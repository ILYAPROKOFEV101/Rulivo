package com.ilya.rulivo.ar

import org.junit.Assert.*
import org.junit.Test

class CaptureHeightTest : com.ilya.rulivo.i18n.LocalizedTest() {
    private fun p(x: Double,y: Double,z: Double)=Point3D(x,y,z)
    private fun base(type: CaptureType)=when(type) {
        CaptureType.CYLINDER_VOLUME -> listOf(p(0.0,0.0,0.0),p(.5,0.0,0.0))
        CaptureType.BOX_VOLUME -> listOf(p(0.0,0.0,0.0),p(2.0,0.0,0.0),p(1.0,0.0,3.0))
        else -> listOf(p(0.0,0.0,0.0),p(4.0,0.0,0.0),p(4.0,0.0,2.0),p(2.0,0.0,2.0),p(2.0,0.0,4.0),p(0.0,0.0,4.0))
    }
    private fun draft(type: CaptureType,release: (Point3D)->Unit={}): CaptureDraft<Point3D> {
        val d=CaptureDraft(CaptureRequest("test",type,"figure"),release)
        base(type).forEach {point->assertTrue(d.place(c(d,CaptureAction.PLACE),true){point})}
        if(type==CaptureType.ROOM_VOLUME)assertTrue(d.close(c(d,CaptureAction.CLOSE),true))
        return d
    }
    private fun c(d: CaptureDraft<*>,action: CaptureAction,height: Double?=null,index: Int?=null)=
        CaptureCommand(d.request.id,d.revision,action,index,height)

    @Test fun allSolidsExposeHeightAfterBaseAndExtrudeWithoutAnyTopAnchor() {
        for(type in listOf(CaptureType.CYLINDER_VOLUME,CaptureType.BOX_VOLUME,CaptureType.ROOM_VOLUME)) {
            val d=draft(type)
            val anchors=d.anchors.toList()
            assertTrue(d.heightAvailable);assertFalse(d.acceptsPoint)
            assertFalse(d.evaluate(anchors).complete)
            for(height in listOf(.05,.1,2.735,.001,1000.0)) {
                assertTrue(d.setHeight(c(d,CaptureAction.SET_HEIGHT,height)))
                val figure=d.evaluate(d.anchors)
                assertTrue(figure.complete);assertNull(figure.error)
                assertEquals(height,figure.dimensions.getValue("height"),1e-12)
                assertEquals(figure.area!!*height,figure.volume!!,1e-10)
                assertEquals(height,figure.edges.maxOf {maxOf(it.start.y,it.end.y)},1e-12)
                assertEquals(0.0,figure.edges.minOf {minOf(it.start.y,it.end.y)},0.0)
                assertEquals(anchors,d.anchors)
            }
            assertTrue(d.setHeight(c(d,CaptureAction.SET_HEIGHT,null)))
            assertFalse(d.evaluate(d.anchors).complete)
        }
    }

    @Test fun cameraToManualReleasesOnlyHeightAnchorAndUndoKeepsBase() {
        for(type in listOf(CaptureType.CYLINDER_VOLUME,CaptureType.BOX_VOLUME,CaptureType.ROOM_VOLUME)) {
            val released=mutableListOf<Point3D>();val d=draft(type){released+=it}
            val anchors=d.anchors.toList()
            assertTrue(d.measureHeight(c(d,CaptureAction.CAMERA_HEIGHT)))
            val top=p(.2,1.5,.2)
            assertTrue(d.place(c(d,CaptureAction.PLACE),true){top})
            assertTrue(d.evaluate(d.anchors).complete)
            assertTrue(d.setHeight(c(d,CaptureAction.SET_HEIGHT,2.0)))
            assertEquals(listOf(top),released);assertEquals(anchors,d.anchors)
            assertTrue(d.undo(c(d,CaptureAction.UNDO)))
            assertNull(d.signedHeight);assertEquals(anchors,d.anchors)
            assertFalse(d.evaluate(d.anchors).complete)
            d.clear();assertEquals((anchors+top).toSet(),released.toSet());assertEquals(anchors.size+1,released.size)
        }
    }

    @Test fun invalidStaleAndEarlyHeightCommandsAreRejected() {
        val d=draft(CaptureType.CYLINDER_VOLUME)
        assertTrue(d.setHeight(c(d,CaptureAction.SET_HEIGHT,.4)))
        for(height in listOf(0.0,.0001,1000.1,Double.NaN,Double.POSITIVE_INFINITY))
            assertFalse(d.setHeight(c(d,CaptureAction.SET_HEIGHT,height)))
        assertEquals(.4,d.signedHeight!!,0.0)
        val stale=c(d,CaptureAction.SET_HEIGHT,5.0)
        d.clear();assertFalse(d.setHeight(stale));assertFalse(d.setHeight(c(d,CaptureAction.SET_HEIGHT,.2)))
        val room=draft(CaptureType.ROOM_VOLUME)
        assertFalse(room.setHeight(c(room,CaptureAction.SET_HEIGHT,-2.7)))
    }

    @Test fun extrusionCanReverseAndSurvivesBaseCorrection() {
        val d=draft(CaptureType.CYLINDER_VOLUME)
        d.setHeight(c(d,CaptureAction.SET_HEIGHT,-.8))
        assertEquals(-.8,d.evaluate(d.anchors).edges.minOf {minOf(it.start.y,it.end.y)},0.0)
        d.select(c(d,CaptureAction.REPLACE,index=1))
        d.place(c(d,CaptureAction.PLACE),true){p(.7,0.0,0.0)}
        assertEquals(-.8,d.signedHeight!!,0.0)
        assertEquals(1.4,d.evaluate(d.anchors).dimensions.getValue("diameter"),0.0)
    }

    @Test fun rapidTypingAndImmediateConfirmationKeepLatestHeightAndCommandOrder() {
        val d=draft(CaptureType.CYLINDER_VOLUME);val queue=CaptureCommands()
        for(h in listOf(2.0,2.7,2.735))queue.submit(c(d,CaptureAction.SET_HEIGHT,h))
        queue.submit(c(d,CaptureAction.CONFIRM))
        assertTrue(d.setHeight(queue.poll()!!))
        val confirm=queue.poll()!!
        assertEquals(CaptureAction.CONFIRM,confirm.action);assertTrue(d.matches(confirm))
        assertEquals(2.735,d.evaluate(d.anchors).dimensions.getValue("height"),0.0);assertNull(queue.poll())
        queue.submit(c(d,CaptureAction.SET_HEIGHT,3.0));queue.clear();assertNull(queue.poll())
    }
}
