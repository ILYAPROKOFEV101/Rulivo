package com.ilya.rulivo.ar

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.round

internal data class CameraRay(val origin: Point3D,val direction: Point3D)

/** A vertical drag plane fixed for one gesture. No hit on the top surface is needed. */
internal class CameraHeightMotion {
    private data class Start(val reference: Point3D,val nx: Double,val nz: Double,val y: Double,val height: Double)
    private var start: Start?=null
    fun reset(){start=null}
    fun height(ray: CameraRay?,reference: Point3D,current: Double): Double? {
        if(ray==null){reset();return null}
        val dx=ray.origin.x-reference.x;val dz=ray.origin.z-reference.z
        val distance=hypot(dx,dz)
        if(distance<.15){reset();return null}
        val previous=start
        val nx=previous?.nx ?: dx/distance;val nz=previous?.nz ?: dz/distance
        val p=previous?.reference ?: reference
        val denominator=ray.direction.x*nx+ray.direction.z*nz
        if(!denominator.isFinite()||abs(denominator)<.08){reset();return null}
        val t=((p.x-ray.origin.x)*nx+(p.z-ray.origin.z)*nz)/denominator
        val y=ray.origin.y+t*ray.direction.y
        if(t<=0||!y.isFinite()){reset();return null}
        if(previous==null){start=Start(reference,nx,nz,y,current);return current}
        val result=previous.height+y-previous.y
        if(!result.isFinite()||abs(result)>FigureGeometry.MAX_HEIGHT_METERS){reset();return null}
        return round(result*1000)/1000
    }
}

internal object HeightEditing {
    fun parse(text: String): Double?=com.ilya.rulivo.i18n.LocalizedNumbers.normalize(text).toDoubleOrNull()?.takeIf {it.isFinite()}
    fun step(type: CaptureType,height: Double,delta: Double): Double =
        (round((height+delta)*1000)/1000).coerceIn(
            if(type==CaptureType.ROOM_VOLUME)0.0 else -FigureGeometry.MAX_HEIGHT_METERS,FigureGeometry.MAX_HEIGHT_METERS)
}
