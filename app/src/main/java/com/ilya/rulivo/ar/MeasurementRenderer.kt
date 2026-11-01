package com.ilya.rulivo.ar

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLUtils
import android.opengl.Matrix
import com.google.ar.core.Camera
import com.google.ar.core.Coordinates2d
import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.TrackingState
import com.google.ar.core.Pose
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.sqrt
import kotlin.math.ceil

/** GL-thread only. Projects world anchors each frame; screen pixels never determine distance. */
class MeasurementRenderer(private val density: Float, textSizePx: Float) {
    var cameraTextureId: Int = 0
        private set
    private var cameraProgram = 0
    private var shapeProgram = 0
    private var labelProgram = 0
    private data class LabelTexture(val id: Int, var text: String? = null, var width: Int = 0, var height: Int = 0)
    private val labels = mutableMapOf<Long, LabelTexture>()
    private var fillProgram = 0
    private var planeProgram = 0
    private val planeModel = FloatArray(16)
    private val planeMvp = FloatArray(16)
    private val labelVertices = buffer(FloatArray(8))
    private val labelUv = buffer(floatArrayOf(0f, 1f, 1f, 1f, 0f, 0f, 1f, 0f))
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = textSizePx
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        color = 0xFFA6FF47.toInt()
    }
    private val quad = buffer(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
    private val textureCoordinates = buffer(FloatArray(8))
    private val shapeVertices = buffer(FloatArray(16))
    private val shapeData = FloatArray(16)
    private val projection = FloatArray(16)
    private val view = FloatArray(16)
    private val viewProjection = FloatArray(16)
    private val world = FloatArray(4)
    private val clip = FloatArray(4)
    private val accent = floatArrayOf(0.65f, 1f, 0.28f, 1f)
    private val previewColor = floatArrayOf(1f, 1f, 1f, 0.5f)
    private val shadow = floatArrayOf(0.03f, 0.06f, 0.05f, 0.9f)

    fun create() {
        // A recreated EGL context owns fresh IDs; old resources are released with the old context.
        cameraProgram = program(CAMERA_VERTEX, CAMERA_FRAGMENT)
        shapeProgram = program(SHAPE_VERTEX, SHAPE_FRAGMENT)
        labelProgram = program(CAMERA_VERTEX, LABEL_FRAGMENT)
        labels.clear()
        planeProgram = program(PLANE_VERTEX, PLANE_FRAGMENT)
        fillProgram = program(FILL_VERTEX, FILL_FRAGMENT)
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        cameraTextureId = textures[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, cameraTextureId)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glDisable(GLES20.GL_CULL_FACE)
    }

    fun drawCamera(frame: Frame) {
        if (frame.timestamp == 0L) return
        quad.position(0)
        textureCoordinates.position(0)
        frame.transformCoordinates2d(Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES,
            quad, Coordinates2d.TEXTURE_NORMALIZED, textureCoordinates)
        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glUseProgram(cameraProgram)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, cameraTextureId)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(cameraProgram, "uTexture"), 0)
        val position = GLES20.glGetAttribLocation(cameraProgram, "aPosition")
        val uv = GLES20.glGetAttribLocation(cameraProgram, "aUv")
        quad.position(0)
        textureCoordinates.position(0)
        GLES20.glVertexAttribPointer(position, 2, GLES20.GL_FLOAT, false, 0, quad)
        GLES20.glVertexAttribPointer(uv, 2, GLES20.GL_FLOAT, false, 0, textureCoordinates)
        GLES20.glEnableVertexAttribArray(position)
        GLES20.glEnableVertexAttribArray(uv)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(position)
        GLES20.glDisableVertexAttribArray(uv)
    }

    fun beginScene(camera: Camera) {
        camera.getProjectionMatrix(projection, 0, 0.02f, 100f)
        camera.getViewMatrix(view, 0)
        Matrix.multiplyMM(viewProjection, 0, projection, 0, view, 0)
    }

    internal fun setCameraMatrices(projectionValues: FloatArray, viewValues: FloatArray) {
        Matrix.multiplyMM(viewProjection, 0, projectionValues, 0, viewValues, 0)
    }

    internal fun centerRay(): CameraRay?=centerCameraRay(viewProjection)

    fun drawFigure(edges: List<FigureEdge>, width: Int, height: Int, preview: Boolean = false) {
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        fun projectPoint(p: Point3D) = project(Pose.makeTranslation(p.x.toFloat(), p.y.toFloat(), p.z.toFloat()))
        edges.forEach { edge ->
            val clipped = SegmentClipping.visiblePart(projectPoint(edge.start), projectPoint(edge.end))
            if (clipped != null && clipped.first.w > 0f && clipped.second.w > 0f) {
                val a = floatArrayOf(clipped.first.x / clipped.first.w, clipped.first.y / clipped.first.w)
                val b = floatArrayOf(clipped.second.x / clipped.second.w, clipped.second.y / clipped.second.w)
                drawLine(a, b, width, height, 5f * density, shadow)
                drawLine(a, b, width, height, 2.5f * density, if (preview) previewColor else accent)
            }
        }
        GLES20.glDisable(GLES20.GL_BLEND)
    }

    /** Triangles are in world space: GL clips the fill even when corners pass behind the camera. */
    fun drawContourFill(triangles: List<Point3D>) {
        if (triangles.isEmpty()) return
        val vertices = buffer(triangles.flatMap { listOf(it.x.toFloat(), it.y.toFloat(), it.z.toFloat()) }.toFloatArray())
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glUseProgram(fillProgram)
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(fillProgram, "uMvp"), 1, false, viewProjection, 0)
        val position = GLES20.glGetAttribLocation(fillProgram, "aPosition")
        GLES20.glEnableVertexAttribArray(position)
        GLES20.glVertexAttribPointer(position, 3, GLES20.GL_FLOAT, false, 0, vertices)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, triangles.size)
        GLES20.glDisableVertexAttribArray(position)
        GLES20.glDisable(GLES20.GL_BLEND)
    }

    fun worldLabelBounds(id: Long, text: String, anchor: Point3D, width: Int, height: Int): LabelBounds? {
        val point = project(Pose.makeTranslation(anchor.x.toFloat(), anchor.y.toFloat(), anchor.z.toFloat())).screen() ?: return null
        val texture = prepareLabel(id, text)
        return LabelBounds((point[0] + 1f) * width / 2f - texture.width / 2f,
            (1f - point[1]) * height / 2f - texture.height / 2f, texture.width.toFloat(), texture.height.toFloat())
    }

    fun drawWorldLabel(id: Long, text: String, anchor: Point3D, width: Int, height: Int) {
        val point = project(Pose.makeTranslation(anchor.x.toFloat(), anchor.y.toFloat(), anchor.z.toFloat())).screen() ?: return
        GLES20.glEnable(GLES20.GL_BLEND)
        drawLabel(id, text, point, point, point, width, height, 1f, centered = true)
        GLES20.glDisable(GLES20.GL_BLEND)
    }

    fun drawMeasurement(poses: List<Pose>, width: Int, height: Int,
        distanceLabel: String?, previewPose: Pose? = null, labelId: Long, previewIsLive: Boolean = true, avoidLabel: LabelBounds? = null) {
        if (poses.isEmpty()) return
        val clipPoints = poses.map { project(it) }
        val points = clipPoints.map { it.screen() }
        val segment = when {
            clipPoints.size == 2 -> SegmentClipping.visiblePart(clipPoints[0], clipPoints[1])
            clipPoints.size == 1 && previewPose != null -> SegmentClipping.visiblePart(clipPoints[0], project(previewPose))
            else -> null
        }
        // Clipped coordinates already lie in the frustum; avoid float rounding at the near plane.
        val lineStart = segment?.first?.let { if (it.w > 0f) floatArrayOf(it.x / it.w, it.y / it.w) else null }
        val lineEnd = segment?.second?.let { if (it.w > 0f) floatArrayOf(it.x / it.w, it.y / it.w) else null }
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        if (lineStart != null && lineEnd != null) {
            drawLine(lineStart, lineEnd, width, height, 6f * density, shadow)
            drawLine(lineStart, lineEnd, width, height, 3f * density,
                if (poses.size == 2) accent else previewColor)
        }
        points.filterNotNull().forEach { point ->
            val rx = 12f * density / width
            val ry = 12f * density / height
            setVertex(0, point[0] - rx, point[1] - ry, -1f, -1f)
            setVertex(1, point[0] + rx, point[1] - ry, 1f, -1f)
            setVertex(2, point[0] - rx, point[1] + ry, -1f, 1f)
            setVertex(3, point[0] + rx, point[1] + ry, 1f, 1f)
            drawShape(accent, circle = true)
        }
        val endPose = poses.getOrNull(1) ?: previewPose
        if (endPose != null && distanceLabel != null && lineStart != null && lineEnd != null) {
            val a = poses[0]
            val middle = MeasurementGeometry.midpoint(Point3D(a.tx().toDouble(), a.ty().toDouble(), a.tz().toDouble()),
                Point3D(endPose.tx().toDouble(), endPose.ty().toDouble(), endPose.tz().toDouble()))
            val projectedMiddle = project(Pose.makeTranslation(middle.x.toFloat(), middle.y.toFloat(), middle.z.toFloat())).screen()
            if (projectedMiddle != null) drawLabel(labelId, distanceLabel, projectedMiddle, lineStart, lineEnd, width, height,
                if (poses.size == 1 && !previewIsLive) 0.5f else 1f, avoidBounds = avoidLabel)
        }
        GLES20.glDisable(GLES20.GL_BLEND)
    }

    private fun prepareLabel(id: Long, text: String): LabelTexture {
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        val texture = labels.getOrPut(id) { createLabelTexture() }
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture.id)
        if (texture.text != text) updateLabelTexture(text, texture)
        return texture
    }

    private fun drawLabel(id: Long, text: String, midpoint: FloatArray, a: FloatArray, b: FloatArray,
        width: Int, height: Int, opacity: Float, centered: Boolean = false, avoidBounds: LabelBounds? = null) {
        val texture = prepareLabel(id, text)
        val bounds = if (centered) LabelBounds(
            (midpoint[0] + 1f) * width / 2f - texture.width / 2f,
            (1f - midpoint[1]) * height / 2f - texture.height / 2f,
            texture.width.toFloat(), texture.height.toFloat(),
        ) else MeasurementLabelPlacement.aboveSegment(
            (a[0] + 1f) * width / 2f, (1f - a[1]) * height / 2f,
            (b[0] + 1f) * width / 2f, (1f - b[1]) * height / 2f,
            width.toFloat(), height.toFloat(), texture.width.toFloat(), texture.height.toFloat(), 8f * density,
            middleX = (midpoint[0] + 1f) * width / 2f, middleY = (1f - midpoint[1]) * height / 2f, avoidBounds = avoidBounds,
        ) ?: return
        val left = bounds.left / width * 2f - 1f
        val right = (bounds.left + bounds.width) / width * 2f - 1f
        val top = 1f - bounds.top / height * 2f
        val bottom = 1f - (bounds.top + bounds.height) / height * 2f
        labelVertices.position(0)
        labelVertices.put(floatArrayOf(left, bottom, right, bottom, left, top, right, top)).position(0)
        labelUv.position(0)
        GLES20.glUseProgram(labelProgram)
        // Android's Canvas produces premultiplied alpha, including the rounded edges.
        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(labelProgram, "uTexture"), 0)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(labelProgram, "uOpacity"), opacity)
        val position = GLES20.glGetAttribLocation(labelProgram, "aPosition")
        val uv = GLES20.glGetAttribLocation(labelProgram, "aUv")
        GLES20.glEnableVertexAttribArray(position)
        GLES20.glEnableVertexAttribArray(uv)
        GLES20.glVertexAttribPointer(position, 2, GLES20.GL_FLOAT, false, 0, labelVertices)
        GLES20.glVertexAttribPointer(uv, 2, GLES20.GL_FLOAT, false, 0, labelUv)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(position)
        GLES20.glDisableVertexAttribArray(uv)
    }

    private fun updateLabelTexture(text: String, texture: LabelTexture) {
        val paddingX = 12f * density
        val paddingY = 7f * density
        val metrics = labelPaint.fontMetrics
        val lines = text.lines()
        val lineHeight = metrics.descent - metrics.ascent
        val lineGap = 4f * density
        val labelWidth = ceil(lines.maxOf { labelPaint.measureText(it) } + paddingX * 2f).toInt().coerceAtLeast(1)
        val labelHeight = ceil(lineHeight * lines.size + lineGap * (lines.size - 1) + paddingY * 2f).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(labelWidth, labelHeight, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            val background = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xED162020.toInt() }
            val inset = density / 2f
            val radius = 12f * density
            canvas.drawRoundRect(inset, inset, labelWidth - inset, labelHeight - inset, radius, radius, background)
            background.apply { color = 0x66A6FF47; style = Paint.Style.STROKE; strokeWidth = density }
            canvas.drawRoundRect(inset, inset, labelWidth - inset, labelHeight - inset, radius, radius, background)
            lines.forEachIndexed { index, line ->
                canvas.drawText(line, (labelWidth - labelPaint.measureText(line)) / 2f,
                    paddingY - metrics.ascent + index * (lineHeight + lineGap), labelPaint)
            }
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
            texture.text = text
            texture.width = labelWidth
            texture.height = labelHeight
        } finally {
            bitmap.recycle()
        }
    }

    /** Call even when tracking is paused so undo/clear promptly free caption textures. */
    fun retainLabels(ids: Set<Long>) {
        val removed = labels.keys.filter { it !in ids }
        for (id in removed) {
            val texture = labels.remove(id) ?: continue
            GLES20.glDeleteTextures(1, intArrayOf(texture.id), 0)
        }
    }

    private fun createLabelTexture(): LabelTexture {
        val names = IntArray(1)
        GLES20.glGenTextures(1, names, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, names[0])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        return LabelTexture(names[0])
    }

    /** ARCore polygons are convex in the plane's local X/Z frame; the shader clips the grid to them. */
    fun drawPlanes(planes: Collection<Plane>) {
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glUseProgram(planeProgram)
        val position = GLES20.glGetAttribLocation(planeProgram, "aPlanePosition")
        GLES20.glEnableVertexAttribArray(position)
        planes.asSequence().filter { it.trackingState == TrackingState.TRACKING && it.subsumedBy == null }
            .take(12).forEach { plane ->
                val polygon = plane.polygon
                polygon.position(0)
                if (polygon.remaining() < 6) return@forEach
                plane.centerPose.toMatrix(planeModel, 0)
                Matrix.multiplyMM(planeMvp, 0, viewProjection, 0, planeModel, 0)
                GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(planeProgram, "uMvp"), 1, false, planeMvp, 0)
                GLES20.glVertexAttribPointer(position, 2, GLES20.GL_FLOAT, false, 0, polygon)
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_FAN, 0, polygon.remaining() / 2)
            }
        GLES20.glDisableVertexAttribArray(position)
        GLES20.glDisable(GLES20.GL_BLEND)
    }

    private fun project(pose: Pose): ClipPoint {
        world[0] = pose.tx(); world[1] = pose.ty(); world[2] = pose.tz(); world[3] = 1f
        Matrix.multiplyMV(clip, 0, viewProjection, 0, world, 0)
        return ClipPoint(clip[0], clip[1], clip[2], clip[3])
    }

    private fun drawLine(a: FloatArray, b: FloatArray, width: Int, height: Int, thickness: Float, color: FloatArray) {
        val dx = (b[0] - a[0]) * width
        val dy = (b[1] - a[1]) * height
        val length = sqrt(dx * dx + dy * dy)
        if (length < 0.01f) return
        val nx = -dy / length * thickness / width
        val ny = dx / length * thickness / height
        setVertex(0, a[0] - nx, a[1] - ny)
        setVertex(1, a[0] + nx, a[1] + ny)
        setVertex(2, b[0] - nx, b[1] - ny)
        setVertex(3, b[0] + nx, b[1] + ny)
        drawShape(color, circle = false)
    }

    private fun setVertex(index: Int, x: Float, y: Float, u: Float = 0f, v: Float = 0f) {
        val offset = index * 4
        shapeData[offset] = x; shapeData[offset + 1] = y
        shapeData[offset + 2] = u; shapeData[offset + 3] = v
    }

    private fun drawShape(color: FloatArray, circle: Boolean) {
        GLES20.glUseProgram(shapeProgram)
        GLES20.glUniform4fv(GLES20.glGetUniformLocation(shapeProgram, "uColor"), 1, color, 0)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(shapeProgram, "uCircle"), if (circle) 1f else 0f)
        shapeVertices.position(0)
        shapeVertices.put(shapeData).position(0)
        val position = GLES20.glGetAttribLocation(shapeProgram, "aPosition")
        val local = GLES20.glGetAttribLocation(shapeProgram, "aLocal")
        GLES20.glEnableVertexAttribArray(position)
        GLES20.glEnableVertexAttribArray(local)
        GLES20.glVertexAttribPointer(position, 2, GLES20.GL_FLOAT, false, 16, shapeVertices)
        shapeVertices.position(2)
        GLES20.glVertexAttribPointer(local, 2, GLES20.GL_FLOAT, false, 16, shapeVertices)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(position)
        GLES20.glDisableVertexAttribArray(local)
    }

    private fun program(vertex: String, fragment: String): Int {
        val shaders = listOf(shader(GLES20.GL_VERTEX_SHADER, vertex), shader(GLES20.GL_FRAGMENT_SHADER, fragment))
        val id = GLES20.glCreateProgram()
        shaders.forEach { GLES20.glAttachShader(id, it) }
        GLES20.glLinkProgram(id)
        val status = IntArray(1)
        GLES20.glGetProgramiv(id, GLES20.GL_LINK_STATUS, status, 0)
        shaders.forEach { GLES20.glDeleteShader(it) }
        check(status[0] != 0) { "GL program: ${GLES20.glGetProgramInfoLog(id)}" }
        return id
    }

    private fun shader(type: Int, source: String): Int {
        val id = GLES20.glCreateShader(type)
        GLES20.glShaderSource(id, source)
        GLES20.glCompileShader(id)
        val status = IntArray(1)
        GLES20.glGetShaderiv(id, GLES20.GL_COMPILE_STATUS, status, 0)
        check(status[0] != 0) { "GL shader: ${GLES20.glGetShaderInfoLog(id)}" }
        return id
    }

    private fun buffer(values: FloatArray): FloatBuffer = ByteBuffer.allocateDirect(values.size * 4)
        .order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(values); position(0) }

    companion object {
        private const val CAMERA_VERTEX = """
            attribute vec2 aPosition;
            attribute vec2 aUv;
            varying vec2 vUv;
            void main() { gl_Position = vec4(aPosition, 0.0, 1.0); vUv = aUv; }
        """
        private const val CAMERA_FRAGMENT = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            uniform samplerExternalOES uTexture;
            varying vec2 vUv;
            void main() { gl_FragColor = texture2D(uTexture, vUv); }
        """
        private const val LABEL_FRAGMENT = """
            precision mediump float;
            uniform sampler2D uTexture;
            uniform float uOpacity;
            varying vec2 vUv;
            void main() { gl_FragColor = texture2D(uTexture, vUv) * uOpacity; }
        """
        private const val FILL_VERTEX = """
            attribute vec3 aPosition;
            uniform mat4 uMvp;
            void main() { gl_Position = uMvp * vec4(aPosition, 1.0); }
        """
        private const val FILL_FRAGMENT = """
            precision mediump float;
            void main() { gl_FragColor = vec4(0.65, 1.0, 0.28, 0.14); }
        """
        private const val PLANE_VERTEX = """
            attribute vec2 aPlanePosition;
            uniform mat4 uMvp;
            varying vec2 vGrid;
            void main() {
                vGrid = aPlanePosition;
                gl_Position = uMvp * vec4(aPlanePosition.x, 0.0, aPlanePosition.y, 1.0);
            }
        """
        private const val PLANE_FRAGMENT = """
            precision mediump float;
            varying vec2 vGrid;
            void main() {
                vec2 cell = abs(fract(vGrid * 10.0 + 0.5) - 0.5);
                float line = 1.0 - smoothstep(0.01, 0.035, min(cell.x, cell.y));
                gl_FragColor = vec4(0.85, 1.0, 0.85, line * 0.18);
            }
        """
        private const val SHAPE_VERTEX = """
            attribute vec2 aPosition;
            attribute vec2 aLocal;
            varying vec2 vLocal;
            void main() { gl_Position = vec4(aPosition, 0.0, 1.0); vLocal = aLocal; }
        """
        private const val SHAPE_FRAGMENT = """
            precision mediump float;
            varying vec2 vLocal;
            uniform vec4 uColor;
            uniform float uCircle;
            void main() {
                if (uCircle > 0.5) {
                    float radius = length(vLocal);
                    if (radius > 1.0) discard;
                    vec3 color = radius > 0.8 ? vec3(0.05) : (radius > 0.5 ? vec3(1.0) : uColor.rgb);
                    gl_FragColor = vec4(color, 1.0 - smoothstep(0.92, 1.0, radius));
                } else { gl_FragColor = uColor; }
            }
        """
    }
}
