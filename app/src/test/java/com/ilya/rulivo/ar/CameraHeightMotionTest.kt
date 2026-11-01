package com.ilya.rulivo.ar

import com.ilya.rulivo.measure.ValueSource
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.sqrt

class CameraHeightMotionTest {
    private val origin=Point3D(0.0,1.5,3.0)
    private val base=Point3D(0.0,0.0,0.0)
    private fun ray(y: Double): CameraRay {
        val dy=y-origin.y;val length=sqrt(9+dy*dy)
        return CameraRay(origin,Point3D(0.0,dy/length,-3/length))
    }
    private fun command(d: CaptureDraft<*>,action: CaptureAction,height: Double?=null)=CaptureCommand(d.request.id,d.revision,action,height=height)
    private fun draft(type: CaptureType): CaptureDraft<Point3D> {
        val d=CaptureDraft<Point3D>(CaptureRequest("camera",type,"figure")){}
        val points=when(type) {
            CaptureType.CYLINDER_VOLUME->listOf(base,Point3D(.5,0.0,0.0))
            CaptureType.BOX_VOLUME->listOf(base,Point3D(2.0,0.0,0.0),Point3D(1.0,0.0,2.0))
            else->listOf(base,Point3D(2.0,0.0,0.0),Point3D(2.0,0.0,2.0),Point3D(0.0,0.0,2.0))
        }
        points.forEach {p->assertTrue(d.place(command(d,CaptureAction.PLACE),true){p})}
        if(type==CaptureType.ROOM_VOLUME)d.close(command(d,CaptureAction.CLOSE),true)
        return d
    }
    @Test fun upwardButtonAlwaysRaisesSignedTopIncludingBelowBase() {
        for(type in listOf(CaptureType.CYLINDER_VOLUME,CaptureType.BOX_VOLUME)) {
            for(h in listOf(-1.7,-.05,0.0,.05,1.7)) {
                assertEquals(h+.05,HeightEditing.step(type,h,.05),1e-12)
                assertEquals(h-.05,HeightEditing.step(type,h,-.05),1e-12)
            }
        }
        assertEquals(0.0,HeightEditing.step(CaptureType.ROOM_VOLUME,0.0,-.05),0.0)
        assertEquals(-1.7,HeightEditing.parse("-1,7")!!,0.0)
        assertNull(HeightEditing.parse("NaN"));assertNull(HeightEditing.parse("-"))
    }
    @Test fun allFiguresFollowCameraUpAndDownWithoutSurfaceHitsAndFreeze() {
        for(type in listOf(CaptureType.CYLINDER_VOLUME,CaptureType.BOX_VOLUME,CaptureType.ROOM_VOLUME)) {
            val d=draft(type);val anchors=d.anchors.toList()
            assertTrue(d.cameraHeight)
            d.updateCameraHeight(ray(0.0),base)
            d.updateCameraHeight(ray(1.7),base)
            assertEquals(1.7,d.signedHeight!!,0.0)
            d.updateCameraHeight(ray(1.2),base)
            assertEquals(1.2,d.signedHeight!!,0.0)
            assertTrue(d.freezeHeight(command(d,CaptureAction.FREEZE_HEIGHT)))
            d.updateCameraHeight(ray(9.0),base)
            assertEquals(1.2,d.signedHeight!!,0.0)
            assertEquals(anchors,d.anchors)
            assertEquals(ValueSource.CAMERA_AIM,d.result(d.evaluate(anchors)).dimensionSources["height"])
            assertTrue(d.aimHeight(command(d,CaptureAction.AIM_HEIGHT)))
            d.updateCameraHeight(ray(9.0),base)
            assertEquals(1.2,d.signedHeight!!,0.0)
            d.updateCameraHeight(ray(9.3),base)
            assertEquals(1.5,d.signedHeight!!,0.0)
            d.setHeight(command(d,CaptureAction.SET_HEIGHT,2.735))
            d.updateCameraHeight(ray(7.0),base)
            assertEquals(2.735,d.signedHeight!!,0.0)
            assertEquals(ValueSource.MANUAL,d.result(d.evaluate(anchors)).dimensionSources["height"])
        }
    }
    @Test fun trackingLossAndUnstableAnglesKeepHeightAndRebaseWithoutJump() {
        val d=draft(CaptureType.CYLINDER_VOLUME)
        d.updateCameraHeight(ray(0.0),base);d.updateCameraHeight(ray(1.7),base)
        val invalid=listOf(null,CameraRay(origin,Point3D(0.0,1.0,0.0)),CameraRay(origin,Point3D(0.0,0.0,1.0)),CameraRay(Point3D(0.0,1.5,0.0),Point3D(0.0,-1.0,0.0)))
        for(r in invalid) {
            d.updateCameraHeight(r,base)
            assertEquals(1.7,d.signedHeight!!,0.0);assertFalse(d.cameraHeightReady)
            assertFalse(d.freezeHeight(command(d,CaptureAction.FREEZE_HEIGHT)))
            d.updateCameraHeight(ray(4.0),base)
            assertEquals(1.7,d.signedHeight!!,0.0)
        }
        d.updateCameraHeight(ray(4.1),base);assertEquals(1.8,d.signedHeight!!,0.0)
    }
    @Test fun roomTopStopsAtFloorAndImmediatelyRespondsWhenRaisingAgain() {
        val d=draft(CaptureType.ROOM_VOLUME)
        d.updateCameraHeight(ray(0.0),base);d.updateCameraHeight(ray(-1.0),base)
        assertNull(d.signedHeight)
        d.updateCameraHeight(ray(-1.0),base);d.updateCameraHeight(ray(-.5),base)
        assertEquals(.5,d.signedHeight!!,0.0)
    }
    @Test fun raisingPhoneAlsoRaisesTopAndStaleFreezeCannotStopNewCapture() {
        val d=draft(CaptureType.CYLINDER_VOLUME)
        val initial=ray(0.0)
        d.updateCameraHeight(initial,base)
        d.updateCameraHeight(initial.copy(origin=origin.copy(y=1.8)),base)
        assertEquals(.3,d.signedHeight!!,0.0)
        val stale=command(d,CaptureAction.FREEZE_HEIGHT)
        d.clear()
        assertFalse(d.freezeHeight(stale));assertFalse(d.aimHeight(command(d,CaptureAction.AIM_HEIGHT)))
    }
}
