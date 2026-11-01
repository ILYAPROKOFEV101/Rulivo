package com.ilya.rulivo

import android.graphics.Bitmap
import android.opengl.EGL14
import android.opengl.GLES20
import android.opengl.Matrix
import androidx.test.platform.app.InstrumentationRegistry
import com.google.ar.core.Pose
import com.ilya.rulivo.ar.*
import com.ilya.rulivo.ui.measurement.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Runs the real GL renderer with deterministic world points; does not claim AR tracking accuracy. */
class ContourRendererTest {
    @Test fun closedContourHasFillSideLengthsAndWorldAnchoredArea() {
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        assertTrue(EGL14.eglInitialize(display, IntArray(2), 0, IntArray(2), 1))
        val configs = arrayOfNulls<android.opengl.EGLConfig>(1)
        val attributes = intArrayOf(EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT, EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8, EGL14.EGL_NONE)
        assertTrue(EGL14.eglChooseConfig(display, attributes, 0, configs, 0, 1, IntArray(1), 0))
        val context = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0)
        val surface = EGL14.eglCreatePbufferSurface(display, configs[0],
            intArrayOf(EGL14.EGL_WIDTH, WIDTH, EGL14.EGL_HEIGHT, HEIGHT, EGL14.EGL_NONE), 0)
        try {
            assertTrue(EGL14.eglMakeCurrent(display, surface, surface, context))
            GLES20.glViewport(0, 0, WIDTH, HEIGHT)
            val renderer = MeasurementRenderer(1.5f, 27f)
            renderer.create() // Compiles camera, plane grid, line/marker and caption shaders.
            val projection = FloatArray(16)
            val view = FloatArray(16)
            Matrix.perspectiveM(projection, 0, 60f, WIDTH.toFloat() / HEIGHT, 0.02f, 100f)
            Matrix.setIdentityM(view, 0)
            val points = listOf(Point3D(-.405,-1.0,-2.5),Point3D(.405,-1.0,-2.5),
                Point3D(.405,1.0,-2.5),Point3D(-.405,1.0,-2.5))
            val evaluation=ContourGeometry.evaluate(points)
            fun pose(p: Point3D)=Pose.makeTranslation(p.x.toFloat(),p.y.toFloat(),p.z.toFloat())
            fun render(name: String, showContour: Boolean = true, unit: MeasurementUnit = MeasurementUnit.METERS): Bitmap {
                GLES20.glClearColor(0.30f, 0.36f, 0.33f, 1f)
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
                renderer.setCameraMatrices(projection, view)
                renderer.retainLabels(if(showContour) setOf(1,2,3,4,-1001L) else emptySet())
                if(showContour) {
                    renderer.drawContourFill(evaluation.triangles)
                    val labelBounds=renderer.worldLabelBounds(-1001L,
                        ContourFormatter.label(evaluation.area!!,evaluation.perimeter!!,unit),evaluation.labelPosition!!,WIDTH,HEIGHT)
                    points.indices.forEach { i ->
                        val a=points[i];val b=points[(i+1)%points.size]
                        renderer.drawMeasurement(listOf(pose(a),pose(b)),WIDTH,HEIGHT,
                            DistanceFormatter.format(DistanceCalculator.distanceMeters(a,b),unit),labelId=i+1L,avoidLabel=labelBounds)
                    }
                    renderer.drawWorldLabel(-1001L,ContourFormatter.label(evaluation.area!!,evaluation.perimeter!!,unit),
                        evaluation.labelPosition!!,WIDTH,HEIGHT)
                }
                assertEquals(GLES20.GL_NO_ERROR,GLES20.glGetError())
                val bytes=ByteBuffer.allocateDirect(WIDTH*HEIGHT*4).order(ByteOrder.nativeOrder())
                GLES20.glReadPixels(0,0,WIDTH,HEIGHT,GLES20.GL_RGBA,GLES20.GL_UNSIGNED_BYTE,bytes)
                val pixels=IntArray(WIDTH*HEIGHT)
                for(y in 0 until HEIGHT) for(x in 0 until WIDTH) {
                    val offset=(y*WIDTH+x)*4
                    pixels[(HEIGHT-1-y)*WIDTH+x]=android.graphics.Color.rgb(bytes.get(offset).toInt() and 255,
                        bytes.get(offset+1).toInt() and 255,bytes.get(offset+2).toInt() and 255)
                }
                val bitmap=Bitmap.createBitmap(pixels,WIDTH,HEIGHT,Bitmap.Config.ARGB_8888)
                val folder=File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),"qa").apply { mkdirs() }
                File(folder,name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
                return bitmap
            }
            fun brightPixels(bitmap: Bitmap, cx: Int, cy: Int): Int {
                var count=0
                for(y in cy-42..cy+42) for(x in cx-130..cx+130) {
                    val color=bitmap.getPixel(x.coerceIn(0,WIDTH-1),y.coerceIn(0,HEIGHT-1))
                    if(android.graphics.Color.green(color)>210&&android.graphics.Color.red(color)>120&&android.graphics.Color.blue(color)<140) count++
                }
                return count
            }
            val initial=render("contour-world.png")
            assertTrue("Area and perimeter must be visible at the world center",brightPixels(initial,WIDTH/2,HEIGHT/2)>400)
            assertTrue("Interior must have a translucent fill",android.graphics.Color.green(initial.getPixel(WIDTH/2,HEIGHT/2+150))>105)
            assertTrue("Outside must retain the camera background",android.graphics.Color.green(initial.getPixel(50,HEIGHT/2+150))<100)
            render("contour-world-centimeters.png",unit=MeasurementUnit.CENTIMETERS).recycle()
            Matrix.translateM(view,0,.3f,0f,0f)
            val moved=render("contour-world-camera-moved.png")
            val shiftedX=WIDTH/2+(HEIGHT*.3/(5*kotlin.math.tan(Math.PI/6))).toInt()
            assertTrue("Caption must follow its reprojected world anchor",brightPixels(moved,shiftedX,HEIGHT/2)>400)
            // A point behind the camera must not leave a caption fixed on the screen.
            Matrix.setIdentityM(view,0);Matrix.translateM(view,0,0f,0f,3f)
            val behind=render("contour-behind-camera.png")
            assertEquals(0,brightPixels(behind,WIDTH/2,HEIGHT/2))
            Matrix.setIdentityM(view,0)
            val empty=render("contour-cleared.png",showContour=false)
            assertEquals(0,brightPixels(empty,WIDTH/2,HEIGHT/2))
            initial.recycle();moved.recycle();behind.recycle();empty.recycle()
        } finally {
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(display, surface)
            EGL14.eglDestroyContext(display, context)
            EGL14.eglTerminate(display)
        }
    }
    companion object { const val WIDTH = 720; const val HEIGHT = 1280 }
}
