package com.ilya.rulivo

import android.graphics.Bitmap
import android.graphics.Color
import android.opengl.EGL14
import android.opengl.GLES20
import android.opengl.Matrix
import androidx.test.platform.app.InstrumentationRegistry
import com.ilya.rulivo.ar.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Real GL output from the same geometry used by capture; synthetic poses, not camera accuracy. */
class FigureRendererTest {
    @Test fun boxCylinderAndConcaveRoomRenderFromTwoCameraPositions() {
        val display=EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        assertTrue(EGL14.eglInitialize(display,IntArray(2),0,IntArray(2),1))
        val configs=arrayOfNulls<android.opengl.EGLConfig>(1)
        assertTrue(EGL14.eglChooseConfig(display,intArrayOf(EGL14.EGL_RENDERABLE_TYPE,EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE,EGL14.EGL_PBUFFER_BIT,EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,
            EGL14.EGL_BLUE_SIZE,8,EGL14.EGL_ALPHA_SIZE,8,EGL14.EGL_NONE),0,configs,0,1,IntArray(1),0))
        val context=EGL14.eglCreateContext(display,configs[0],EGL14.EGL_NO_CONTEXT,intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION,2,EGL14.EGL_NONE),0)
        val width=800;val height=1000
        val surface=EGL14.eglCreatePbufferSurface(display,configs[0],intArrayOf(EGL14.EGL_WIDTH,width,EGL14.EGL_HEIGHT,height,EGL14.EGL_NONE),0)
        try {
            assertTrue(EGL14.eglMakeCurrent(display,surface,surface,context))
            GLES20.glViewport(0,0,width,height)
            val renderer=MeasurementRenderer(1.5f,27f).apply {create()}
            val projection=FloatArray(16);val view=FloatArray(16)
            Matrix.perspectiveM(projection,0,55f,width.toFloat()/height,.02f,100f)
            fun p(x:Double,y:Double,z:Double)=Point3D(x,y,z)
            val box=FigureGeometry.evaluate(CaptureType.BOX_VOLUME,listOf(p(0.0,0.0,0.0),p(2.0,0.0,0.0),p(1.0,0.0,3.0),p(1.0,2.0,1.0)))
            val cylinder=FigureGeometry.evaluate(CaptureType.CYLINDER_VOLUME,listOf(p(1.0,0.0,1.0),p(2.0,0.0,1.0),p(1.0,2.0,1.0)))
            val room=FigureGeometry.evaluate(CaptureType.ROOM_VOLUME,listOf(p(0.0,0.0,0.0),p(4.0,0.0,0.0),p(4.0,0.0,2.0),p(2.0,0.0,2.0),p(2.0,0.0,4.0),p(0.0,0.0,4.0),p(1.0,2.7,1.0)),6)
            val folder=File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),"modes-qa").apply {mkdirs()}
            fun render(edges:List<FigureEdge>,name:String):IntArray {
                GLES20.glClearColor(.055f,.08f,.08f,1f);GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
                renderer.setCameraMatrices(projection,view);renderer.drawFigure(edges,width,height)
                assertEquals(GLES20.GL_NO_ERROR,GLES20.glGetError())
                val bytes=ByteBuffer.allocateDirect(width*height*4).order(ByteOrder.nativeOrder())
                GLES20.glReadPixels(0,0,width,height,GLES20.GL_RGBA,GLES20.GL_UNSIGNED_BYTE,bytes)
                val pixels=IntArray(width*height)
                for(y in 0 until height)for(x in 0 until width) {
                    val i=(y*width+x)*4
                    pixels[(height-1-y)*width+x]=Color.rgb(bytes.get(i).toInt()and 255,bytes.get(i+1).toInt()and 255,bytes.get(i+2).toInt()and 255)
                }
                val bitmap=Bitmap.createBitmap(pixels,width,height,Bitmap.Config.ARGB_8888)
                File(folder,name).outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
                return pixels
            }
            for((name,figure) in listOf("box" to box,"cylinder" to cylinder,"room" to room)) {
                assertTrue(figure.complete)
                Matrix.setLookAtM(view,0,7f,6f,8f,1.5f,1f,1.5f,0f,1f,0f)
                val initial=render(figure.edges,"wireframe-$name.png")
                assertTrue("No visible $name",initial.count {Color.green(it)>200&&Color.blue(it)<140}>1000)
                Matrix.setLookAtM(view,0,-6f,4f,7f,1.5f,1f,1.5f,0f,1f,0f)
                val moved=render(figure.edges,"wireframe-$name-moved.png")
                assertTrue(initial.indices.count {initial[it]!=moved[it]}>1000)
            }
            // A numeric height edit must change actual GL pixels while the base stays fixed.
            Matrix.setLookAtM(view,0,7f,6f,8f,1.5f,1f,1.5f,0f,1f,0f)
            for((name,type,base) in listOf(
                Triple("cylinder",CaptureType.CYLINDER_VOLUME,listOf(p(1.0,0.0,1.0),p(2.0,0.0,1.0))),
                Triple("box",CaptureType.BOX_VOLUME,listOf(p(0.0,0.0,0.0),p(2.0,0.0,0.0),p(1.0,0.0,3.0))),
                Triple("room",CaptureType.ROOM_VOLUME,listOf(p(0.0,0.0,0.0),p(4.0,0.0,0.0),p(4.0,0.0,2.0),p(2.0,0.0,2.0),p(2.0,0.0,4.0),p(0.0,0.0,4.0)))
            )) {
                val count=if(type==CaptureType.ROOM_VOLUME)base.size else null
                val low=FigureGeometry.withHeight(type,base,count,.5)
                val high=FigureGeometry.withHeight(type,base,count,2.735)
                assertTrue(low.complete&&high.complete)
                val lowPixels=render(low.edges,"height-$name-low.png")
                val highPixels=render(high.edges,"height-$name-high.png")
                assertTrue("Height did not change rendered $name",lowPixels.indices.count {lowPixels[it]!=highPixels[it]}>1000)
                val restored=render(low.edges,"height-$name-restored.png")
                assertArrayEquals("Lowering must restore the original scene",lowPixels,restored)
            }
            // Crossing the near plane must retain the visible part and produce no GL errors.
            Matrix.setIdentityM(view,0)
            assertTrue(render(listOf(FigureEdge(p(0.0,0.0,1.0),p(.5,.3,-2.0))),"wireframe-clipped.png").count {Color.green(it)>200}>100)
        } finally {
            EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(display,surface);EGL14.eglDestroyContext(display,context);EGL14.eglTerminate(display)
        }
    }
}
