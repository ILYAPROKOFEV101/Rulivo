package com.ilya.rulivo

import android.graphics.Bitmap
import android.opengl.EGL14
import android.opengl.GLES20
import android.opengl.Matrix
import androidx.test.platform.app.InstrumentationRegistry
import com.google.ar.core.Pose
import com.ilya.rulivo.ar.MeasurementRenderer
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Runs the real GL renderer with deterministic world points; does not claim AR tracking accuracy. */
class MeasurementRendererTest {
    @Test fun fiveBillboardLabelsRenderAndSurviveCameraMovement() {
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
            val lengths = listOf(0.426f, 0.75f, 1.37f, 0.25f, 0.9f)
            val captions = listOf("42.6 cm", "75 cm", "1.37 m", "25 cm", "90 cm")
            fun render(count: Int, name: String): Bitmap {
                GLES20.glClearColor(0.30f, 0.36f, 0.33f, 1f)
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
                renderer.setCameraMatrices(projection, view)
                renderer.retainLabels((1L..count.toLong()).toSet())
                repeat(count) { index ->
                    val y = -0.8f + index * 0.4f
                    renderer.drawMeasurement(listOf(Pose.makeTranslation(-0.55f, y, -2f),
                        Pose.makeTranslation(-0.55f + lengths[index], y, -2f)), WIDTH, HEIGHT, captions[index], labelId = index + 1L)
                }
                assertEquals(GLES20.GL_NO_ERROR, GLES20.glGetError())
                val bytes = ByteBuffer.allocateDirect(WIDTH * HEIGHT * 4).order(ByteOrder.nativeOrder())
                GLES20.glReadPixels(0, 0, WIDTH, HEIGHT, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, bytes)
                val pixels = IntArray(WIDTH * HEIGHT)
                for (y in 0 until HEIGHT) for (x in 0 until WIDTH) {
                    val offset = (y * WIDTH + x) * 4
                    val r = bytes.get(offset).toInt() and 255
                    val g = bytes.get(offset + 1).toInt() and 255
                    val b = bytes.get(offset + 2).toInt() and 255
                    pixels[(HEIGHT - 1 - y) * WIDTH + x] = android.graphics.Color.rgb(r, g, b)
                }
                val bitmap = Bitmap.createBitmap(pixels, WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
                val folder = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "qa").apply { mkdirs() }
                File(folder, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                return bitmap
            }
            val initial = render(5, "five-ar-lines.png")
            // Every distinct line occupies a different horizontal strip of the real GL framebuffer.
            for (index in 0..4) {
                val centerY = (HEIGHT / 2f - (-0.8f + index * 0.4f) * HEIGHT / (4f * kotlin.math.tan(Math.PI / 6))).toInt()
                var accentPixels = 0
                for (y in (centerY - 75).coerceAtLeast(0)..(centerY + 10).coerceAtMost(HEIGHT - 1)) {
                    for (x in 0 until WIDTH) {
                        val color = initial.getPixel(x, y)
                        if (android.graphics.Color.green(color) > 210 && android.graphics.Color.red(color) > 120 && android.graphics.Color.blue(color) < 140) accentPixels++
                    }
                }
                assertTrue("No visible measurement in strip $index", accentPixels > 100)
            }
            Matrix.setLookAtM(view, 0, 0.45f, 0.15f, 0f, 0f, 0f, -2f, 0f, 1f, 0f)
            render(5, "five-ar-lines-camera-moved.png").recycle()
            render(4, "four-ar-lines-after-undo.png").recycle()
            render(0, "empty-ar-scene.png").recycle()
            initial.recycle()
        } finally {
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(display, surface)
            EGL14.eglDestroyContext(display, context)
            EGL14.eglTerminate(display)
        }
    }
    companion object { const val WIDTH = 720; const val HEIGHT = 1280 }
}
