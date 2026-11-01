package com.ilya.rulivo.ar

import org.junit.Assert.*
import org.junit.Test

class CaptureDraftTest : com.ilya.rulivo.i18n.LocalizedTest() {
    private val req=CaptureRequest("room",CaptureType.ROOM_FLOOR,"contour")
    @Test fun heightProjectsVerticallyAndDoesNotMeasureDiagonal() {
        val a=Point3D(1.0,2.0,3.0);val b=Point3D(5.0,5.0,3.0)
        assertEquals(3.0,captureDistance(CaptureType.HEIGHT,a,b),0.0)
        assertEquals(5.0,captureDistance(CaptureType.DISTANCE,a,b),0.0)
        assertEquals(-3.0,captureDistance(CaptureType.HEIGHT,b,a),0.0)
    }
    @Test fun staleWrongDraftAndMissingSurfaceNeverAllocateAnchors() {
        val draft=CaptureDraft<Int>(req){}
        var allocations=0
        assertFalse(draft.place(CaptureCommand("other",0,CaptureAction.PLACE),true){++allocations})
        assertFalse(draft.place(CaptureCommand(req.id,0,CaptureAction.PLACE),false){++allocations})
        val first=CaptureCommand(req.id,0,CaptureAction.PLACE)
        assertTrue(draft.place(first,true){++allocations})
        assertFalse(draft.place(first,true){++allocations})
        assertEquals(1,allocations)
    }
    @Test fun closingReplacingReopeningUndoAndClearReleaseExactlyOnce() {
        val released=mutableListOf<Int>();val d=CaptureDraft<Int>(req){released.add(it)}
        fun command(action: CaptureAction,index: Int?=null)=CaptureCommand(req.id,d.revision,action,index)
        for(i in 1..4)assertTrue(d.place(command(CaptureAction.PLACE),true){i})
        assertFalse(d.close(command(CaptureAction.CLOSE),false))
        assertTrue(d.close(command(CaptureAction.CLOSE),true))
        assertFalse(d.place(command(CaptureAction.PLACE),true){error("Closed contour allocated")})
        assertTrue(d.select(command(CaptureAction.REPLACE,2)))
        assertTrue(d.place(command(CaptureAction.PLACE),true){5})
        assertEquals(listOf(3),released)
        d.undo(command(CaptureAction.UNDO));assertFalse(d.closed);assertEquals(4,d.anchors.size)
        d.undo(command(CaptureAction.UNDO));assertEquals(listOf(3,4),released)
        d.clear();d.clear()
        assertEquals(listOf(1,2,3,4,5),released.sorted())
    }
    @Test fun maximumVerticesAndScalarPointBudgetAreEnforced() {
        val contour=CaptureDraft<Int>(req){}
        repeat(32){i->assertTrue(contour.place(CaptureCommand(req.id,contour.revision,CaptureAction.PLACE),true){i})}
        assertFalse(contour.place(CaptureCommand(req.id,contour.revision,CaptureAction.PLACE),true){error("Budget exceeded")})
        val line=CaptureDraft<Int>(req.copy(type=CaptureType.DISTANCE)){}
        repeat(2){i->assertTrue(line.place(CaptureCommand(req.id,line.revision,CaptureAction.PLACE),true){i})}
        assertFalse(line.place(CaptureCommand(req.id,line.revision,CaptureAction.PLACE),true){error("Budget exceeded")})
    }
    @Test fun cancelReplacementRetainsAllExistingVertices() {
        val d=CaptureDraft<Int>(req){error("Unexpected release")}
        repeat(3){i->d.place(CaptureCommand(req.id,d.revision,CaptureAction.PLACE),true){i}}
        d.select(CaptureCommand(req.id,d.revision,CaptureAction.REPLACE,1))
        d.undo(CaptureCommand(req.id,d.revision,CaptureAction.UNDO))
        assertNull(d.replacement);assertEquals(listOf(0,1,2),d.anchors)
    }
    @Test fun roomClosesFloorThenCapturesOneHeightAndUndoPreservesFloor() {
        val released=mutableListOf<Int>()
        val d=CaptureDraft<Int>(req.copy(type=CaptureType.ROOM_VOLUME)){released.add(it)}
        fun c(action:CaptureAction,index:Int?=null)=CaptureCommand(req.id,d.revision,action,index)
        repeat(32){i->assertTrue(d.place(c(CaptureAction.PLACE),true){i})}
        assertFalse(d.acceptsPoint)
        assertTrue(d.close(c(CaptureAction.CLOSE),true));assertEquals(32,d.baseCount);assertFalse(d.acceptsPoint)
        assertTrue(d.measureHeight(c(CaptureAction.CAMERA_HEIGHT)));assertTrue(d.acceptsPoint)
        assertFalse(d.close(c(CaptureAction.CLOSE),true))
        assertTrue(d.place(c(CaptureAction.PLACE),true){32});assertFalse(d.acceptsPoint)
        assertFalse(d.place(c(CaptureAction.PLACE),true){error("Extra height anchor")})
        d.undo(c(CaptureAction.UNDO));assertTrue(d.closed);assertEquals(32,d.anchors.size);assertEquals(listOf(32),released)
        d.undo(c(CaptureAction.UNDO));assertFalse(d.closed);assertNull(d.baseCount)
        d.clear();assertEquals((0..32).toList(),released.sorted())
    }
    @Test fun solidPointBudgetsAndReplacementKeepExactlyRequiredSamples() {
        for(type in listOf(CaptureType.BOX_VOLUME,CaptureType.CYLINDER_VOLUME)) {
            val request=req.copy(type=type)
            val d=CaptureDraft<Int>(request){}
            repeat(request.pointLimit){i->
                if(d.heightAvailable)d.measureHeight(CaptureCommand(req.id,d.revision,CaptureAction.CAMERA_HEIGHT))
                assertTrue(d.place(CaptureCommand(req.id,d.revision,CaptureAction.PLACE),true){i})
            }
            assertFalse(d.acceptsPoint)
            d.select(CaptureCommand(req.id,d.revision,CaptureAction.REPLACE,0));assertTrue(d.acceptsPoint)
            d.place(CaptureCommand(req.id,d.revision,CaptureAction.PLACE),true){99}
            assertEquals(request.pointLimit,d.anchors.size);assertEquals(99,d.anchors[0]);assertFalse(d.acceptsPoint)
        }
    }

}
