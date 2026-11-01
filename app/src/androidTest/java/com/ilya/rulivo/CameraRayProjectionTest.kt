package com.ilya.rulivo

import android.opengl.Matrix
import com.google.ar.core.Pose
import com.ilya.rulivo.ar.*
import org.junit.Assert.*
import org.junit.Test

class CameraRayProjectionTest {
    @Test fun reticleRayProjectsToScreenCenterAcrossRotationsAndAsymmetricFrusta() {
        for(rotation in listOf(0f,90f,180f,270f)) {
            val view=FloatArray(16);val projection=FloatArray(16);val vp=FloatArray(16)
            Matrix.setLookAtM(view,0,1f,1.6f,3f,0f,.8f,0f,0f,1f,0f)
            Matrix.rotateM(view,0,rotation,0f,0f,1f)
            Matrix.frustumM(projection,0,-.015f,.025f,-.03f,.04f,.02f,100f)
            Matrix.multiplyMM(vp,0,projection,0,view,0)
            val ray=centerCameraRay(vp)!!
            for(distance in listOf(.1,1.0,5.0)) {
                val world=floatArrayOf((ray.origin.x+ray.direction.x*distance).toFloat(),
                    (ray.origin.y+ray.direction.y*distance).toFloat(),(ray.origin.z+ray.direction.z*distance).toFloat(),1f)
                val clip=FloatArray(4);Matrix.multiplyMV(clip,0,vp,0,world,0)
                assertTrue(clip[3]>0)
                assertEquals(0f,clip[0]/clip[3],.0001f);assertEquals(0f,clip[1]/clip[3],.0001f)
            }
        }
        assertNull(centerCameraRay(FloatArray(16)))
    }
    @Test fun positiveHeightGoesUpEvenWhenBasePlaneFacesDown() {
        for(pose in listOf(Pose.IDENTITY,Pose(floatArrayOf(1f,2f,3f),floatArrayOf(1f,0f,0f,0f)))) {
            val basis=captureBasis(pose)
            val below=basis.compose(Pose.makeTranslation(0f,-1.7f,0f))
            val raised=basis.compose(Pose.makeTranslation(0f,-1.65f,0f))
            assertEquals(.05f,raised.ty()-below.ty(),.00001f)
            assertEquals(pose.tx(),basis.tx(),0f);assertEquals(pose.ty(),basis.ty(),0f)
        }
    }
}
