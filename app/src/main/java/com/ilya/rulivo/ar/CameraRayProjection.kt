package com.ilya.rulivo.ar

import android.opengl.Matrix
import com.google.ar.core.Pose
import kotlin.math.sqrt

/** Unprojects the visible reticle, including display rotation and asymmetric projection. */
internal fun centerCameraRay(viewProjection: FloatArray): CameraRay? {
    val inverse=FloatArray(16)
    if(!Matrix.invertM(inverse,0,viewProjection,0))return null
    fun point(z: Float): Point3D? {
        val result=FloatArray(4)
        Matrix.multiplyMV(result,0,inverse,0,floatArrayOf(0f,0f,z,1f),0)
        if(result.any {!it.isFinite()}||kotlin.math.abs(result[3])<1e-8)return null
        return Point3D((result[0]/result[3]).toDouble(),(result[1]/result[3]).toDouble(),(result[2]/result[3]).toDouble())
    }
    val near=point(-1f) ?: return null;val far=point(1f) ?: return null
    val x=far.x-near.x;val y=far.y-near.y;val z=far.z-near.z
    val length=sqrt(x*x+y*y+z*z)
    if(length<1e-8||!length.isFinite())return null
    return CameraRay(near,Point3D(x/length,y/length,z/length))
}

/** Horizontal bases always grow toward world up, including a downward-facing AR plane. */
internal fun captureBasis(pose: Pose): Pose = if(pose.getYAxis()[1]<-.5f)
    pose.compose(Pose(floatArrayOf(0f,0f,0f),floatArrayOf(1f,0f,0f,0f))) else pose
