package com.ilya.rulivo.ar

import com.ilya.rulivo.R
import com.ilya.rulivo.i18n.text

import android.app.Activity
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.util.TypedValue
import com.google.ar.core.Anchor
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.DepthPoint
import com.google.ar.core.Frame
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.ResourceExhaustedException
import com.google.ar.core.exceptions.NotTrackingException
import com.google.ar.core.exceptions.UnavailableApkTooOldException
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException
import com.google.ar.core.exceptions.UnavailableSdkTooOldException
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException
import com.ilya.rulivo.ui.measurement.MeasurementStatus
import com.ilya.rulivo.ui.measurement.ContourFormatter
import com.ilya.rulivo.ui.measurement.DistanceFormatter
import com.ilya.rulivo.ui.measurement.MeasurementUnit
import java.util.concurrent.atomic.AtomicReference
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/** Owns the session with the view lifecycle. Anchors and frame access stay on the GL thread. */
class ArMeasurementController(
    private val activity: Activity,
    private val surface: GLSurfaceView,
    private val onStatus: (MeasurementStatus) -> Unit,
    private val onFrame: (ArFrameState) -> Unit,
) : GLSurfaceView.Renderer {
    private sealed interface Command {
        data class Place(val revision: Long) : Command
        data class Undo(val revision: Long) : Command
        data class CancelCurrent(val revision: Long) : Command
        data class Continue(val revision: Long) : Command
        data class CloseContour(val revision: Long) : Command
        data object Reset : Command
        data object BeginMeasurement : Command
    }
    private val command = AtomicReference<Command?>(null)
    private val handler = Handler(Looper.getMainLooper())
    private val renderer = MeasurementRenderer(
        activity.resources.displayMetrics.density,
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 18f, activity.resources.displayMetrics),
    )
    @Volatile private var measurementUnit = MeasurementUnit.AUTO
    private val scene = MeasurementScene<Anchor> { it.detach() }
    private val capture = ArCaptureController()
    private var previewRelativePose: Pose? = null
    private val spaceScan = SpaceScanTracker()
    private var diagnostics: AccuracyDiagnostics? = null
    private var notice: String? = null
    private var noticeUntil = 0L
    @Volatile private var session: Session? = null
    @Volatile private var running = false
    @Volatile private var destroyed = false
    @Volatile private var failed = false
    private var foreground = false
    private var installRequested = false
    private var generation = 0
    private var width = 1
    private var height = 1
    private var textureBound = false
    private var lastUiUpdate = 0L
    private var lastFrameState: ArFrameState? = null

    fun resume() {
        if (destroyed || failed || foreground) return
        foreground = true
        val attempt = ++generation
        if (session != null) {
            resumeSession()
            return
        }
        onStatus(MeasurementStatus.CheckingAvailability)
        ArCoreApk.getInstance().checkAvailabilityAsync(activity) { availability ->
            handler.post {
                if (destroyed || failed || !foreground || generation != attempt) return@post
                when {
                    availability.isSupported -> installAndStart()
                    availability == ArCoreApk.Availability.UNSUPPORTED_DEVICE_NOT_CAPABLE ->
                        fail(MeasurementStatus.ArNotSupported)
                    else -> fail(MeasurementStatus.Error(text(R.string.failed_to_check_ar_support_check_your)))
                }
            }
        }
    }

    private fun installAndStart() {
        try {
            when (ArCoreApk.getInstance().requestInstall(activity, !installRequested)) {
                ArCoreApk.InstallStatus.INSTALL_REQUESTED -> {
                    installRequested = true
                    // The install activity pauses us; the next resume repeats the check.
                    return
                }
                ArCoreApk.InstallStatus.INSTALLED -> Unit
            }
            val created = Session(activity)
            session = created
            val depthSupported = created.isDepthModeSupported(Config.DepthMode.AUTOMATIC)
            created.configure(Config(created).apply {
                planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
                if (depthSupported) {
                    depthMode = Config.DepthMode.AUTOMATIC
                }
                focusMode = Config.FocusMode.AUTO
                lightEstimationMode = Config.LightEstimationMode.DISABLED
                updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
            })
            diagnostics = AccuracyDiagnostics.create(activity, depthSupported)
            resumeSession()
        } catch (exception: Exception) {
            handleException(exception)
        }
    }

    private fun resumeSession() {
        try {
            session?.resume()
            diagnostics?.event("resume")
            running = true
            textureBound = false
            lastFrameState = null
            // A resumed session still owns its anchors. Do not reset the UI's last result.
            onFrame(ArFrameState(if (scene.currentStart == null) 0 else 1, false, false,
                measurements = scene.measurements.map { MeasurementReading(it.id, null, false) },
                trackingHint = if (spaceScan.phase.isPreparing) scanHint()
                    else text(R.string.return_to_the_marked_points_to_restore),
                sceneRevision = scene.revision, scanPhase = spaceScan.phase, scanProgress = spaceScan.progress,
                contours = scene.contours.map { ContourReading(it.id, scene.measurements.last { edge -> edge.pathId == it.id }.id, null, null, false) },
                isContinuing = scene.isContinuing, cornerCount = scene.activeCorners.size,
                segmentLimitReached = !scene.canAddSegment))
            surface.onResume()
        } catch (exception: Exception) {
            handleException(exception)
        }
    }

    fun pause() {
        foreground = false
        generation++
        running = false
        command.set(null)
        capture.interrupt()
        // Wait for the render thread BEFORE pausing/closing ARCore.
        surface.onPause()
        capture.releaseUnused()
        spaceScan.interrupt()
        diagnostics?.interrupt("pause")
        try {
            session?.pause()
        } catch (exception: Exception) {
            Log.w(TAG, "Could not pause AR session", exception)
        }
    }

    fun destroy() {
        if (destroyed) return
        destroyed = true
        pause()
        handler.removeCallbacksAndMessages(null)
        // The renderer is paused, so no frame can access these anchors now.
        scene.clear()
        capture.destroy()
        diagnostics?.close()
        diagnostics = null
        val oldSession = session
        session = null
        // close() can take seconds; it must not block Compose or the Activity.
        if (oldSession != null) Thread({ oldSession.close() }, "Rulivo-AR-close").start()
    }

    fun beginMeasurement() {
        if (running && !failed) command.compareAndSet(null, Command.BeginMeasurement)
    }

    fun placePoint(revision: Long) {
        if (running && !failed) command.compareAndSet(null, Command.Place(revision))
    }

    fun reset() {
        if (running && !failed) command.set(Command.Reset)
    }

    fun undo(revision: Long) {
        if (running && !failed) command.compareAndSet(null, Command.Undo(revision))
    }

    fun cancelCurrent(revision: Long) {
        if (running && !failed) command.compareAndSet(null, Command.CancelCurrent(revision))
    }

    fun continueLine(revision: Long) {
        if (running && !failed) command.compareAndSet(null, Command.Continue(revision))
    }

    fun closeContour(revision: Long) {
        if (running && !failed) command.compareAndSet(null, Command.CloseContour(revision))
    }

    fun setMeasurementUnit(unit: MeasurementUnit) {
        measurementUnit = unit
    }

    fun setCaptureRequest(request: CaptureRequest?) { capture.request = request }
    fun captureCommand(command: CaptureCommand) { if (running && !failed) capture.submit(command) }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        try {
            renderer.create()
            textureBound = false
        } catch (exception: Exception) {
            reportRenderError(exception)
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        this.width = width.coerceAtLeast(1)
        this.height = height.coerceAtLeast(1)
        GLES20.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        val renderStartedNanos = System.nanoTime()
        GLES20.glClearColor(0.035f, 0.05f, 0.05f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        val activeSession = session ?: return
        if (!running || destroyed || failed) return
        try {
            if (!textureBound) {
                activeSession.setCameraTextureName(renderer.cameraTextureId)
                textureBound = true
            }
            activeSession.setDisplayGeometry(surface.display?.rotation ?: 0, width, height)
            val frame = activeSession.update()
            renderer.drawCamera(frame)
            val captureRequest = capture.request
            val action = command.getAndSet(null)?.takeIf { captureRequest == null || it == Command.BeginMeasurement }
            val revisionBefore = scene.revision
            var commandOutcome: String? = null
            val edited = when (action) {
                Command.Reset -> { scene.clear(); true }
                is Command.Undo -> scene.undoLast(action.revision)
                is Command.CancelCurrent -> scene.cancelCurrent(action.revision)
                is Command.Continue -> scene.continueLast(action.revision)
                else -> false
            }
            if (edited) {
                previewRelativePose = null
                notice = null
            }
            val removed = scene.removeStopped { it.trackingState == TrackingState.STOPPED }
            if (removed) {
                previewRelativePose = null
                showNotice(text(R.string.the_lost_measurement_was_removed_other_lines))
            }
            val cameraTracking = frame.camera.trackingState == TrackingState.TRACKING
            val currentTracking = cameraTracking && (captureRequest != null || scene.currentStart?.trackingState != TrackingState.PAUSED)
            val candidates = if (currentTracking && (!scene.atLimit || captureRequest != null)) centerHits(frame) else emptyList()
            val hit = SurfaceHitSelection.nearestSurface(candidates)
            // Preparation concerns the session, even when the reticle is between known surfaces.
            // Actual point placement below still requires a valid centre hit on this frame.
            val scanPlanes = if (cameraTracking && spaceScan.phase.isPreparing)
                activeSession.getAllTrackables(Plane::class.java).filter {
                    it.trackingState == TrackingState.TRACKING && it.subsumedBy == null && it.polygon.remaining() >= 6
                } else emptyList()
            spaceScan.update(frame.timestamp, System.nanoTime(), cameraTracking, frame.camera.pose.point(),
                hit != null || scanPlanes.isNotEmpty())
            if (action == Command.BeginMeasurement) {
                if (spaceScan.beginMeasurement()) {
                    notice = null
                    commandOutcome = "STARTED"
                } else {
                    showNotice(scanHint())
                    commandOutcome = "SCAN_NOT_READY"
                }
            }
            if (cameraTracking) renderer.beginScene(frame.camera)
            val captureFrame = capture.update(hit, cameraTracking && spaceScan.canPlace(true),
                if (cameraTracking) renderer.centerRay() else null)
            var canPlace = spaceScan.canPlace(hit != null) && currentTracking && scene.canAddSegment
            if (action is Command.CloseContour && action.revision == scene.revision && scene.canClose) {
                val corners = scene.activeCorners
                if (cameraTracking && corners.all { it.trackingState == TrackingState.TRACKING } &&
                    spaceScan.phase == SpaceScanPhase.MEASURING) {
                    val evaluation = ContourGeometry.evaluate(corners.map { it.pose.point() })
                    if (evaluation.error == null && scene.closeContour(action.revision)) {
                        previewRelativePose = null
                        notice = null
                        commandOutcome = "CLOSED"
                    } else {
                        evaluation.error?.let(::showNotice)
                        commandOutcome = "INVALID_CONTOUR"
                    }
                }
            }
            if (action is Command.Place) {
                try {
                    val placement = scene.place(action.revision, canPlace) { hit!!.createAnchor() }
                    commandOutcome = placement.name
                    when (placement) {
                        MeasurementScene.Placement.FIRST_POINT, MeasurementScene.Placement.COMPLETED -> {
                            previewRelativePose = null
                            notice = null
                        }
                        MeasurementScene.Placement.LIMIT_REACHED -> showNotice(LIMIT_MESSAGE)
                        MeasurementScene.Placement.REJECTED -> if (action.revision == scene.revision) {
                            showNotice(if (spaceScan.phase != SpaceScanPhase.MEASURING) scanHint() else NO_SURFACE_MESSAGE)
                        }
                    }
                } catch (_: NotTrackingException) {
                    commandOutcome = "NOT_TRACKING"
                    spaceScan.interrupt()
                    canPlace = false
                    showNotice(NO_SURFACE_MESSAGE)
                } catch (_: ResourceExhaustedException) {
                    commandOutcome = "RESOURCE_EXHAUSTED"
                    showNotice(text(R.string.not_enough_ar_resources_remove_the_last))
                }
            }
            val start = scene.currentStart
            val startTracking = cameraTracking && (start == null || start.trackingState == TrackingState.TRACKING)
            if (start != null && startTracking && hit != null) {
                // Preview is relative to the real first anchor and creates no extra Anchor.
                previewRelativePose = start.pose.inverse().compose(hit.hitPose)
            }
            val previewPose = if (start != null && startTracking) previewRelativePose?.let { start.pose.compose(it) } else null
            val previewDistance = if (previewPose != null && start != null)
                DistanceCalculator.distanceMeters(start.pose.point(), previewPose.point()) else null
            val readings = scene.measurements.map { measurement ->
                val tracking = cameraTracking && measurement.startAnchor.trackingState == TrackingState.TRACKING &&
                    measurement.endAnchor.trackingState == TrackingState.TRACKING
                val distance = if (tracking) DistanceCalculator.distanceMeters(
                    measurement.startAnchor.pose.point(), measurement.endAnchor.pose.point()) else null
                MeasurementReading(measurement.id, distance, tracking)
            }
            val activeCorners = scene.activeCorners
            val cornersTracking = cameraTracking && activeCorners.all { it.trackingState == TrackingState.TRACKING }
            val activeEvaluation = if (activeCorners.size >= 3 && cornersTracking)
                ContourGeometry.evaluate(activeCorners.map { it.pose.point() }) else null
            val contours = scene.contours
            val evaluations = contours.associate { contour ->
                contour.id to if (cameraTracking && contour.anchors.all { it.trackingState == TrackingState.TRACKING })
                    ContourGeometry.evaluate(contour.anchors.map { it.pose.point() }) else null
            }
            val contourReadings = contours.map { contour ->
                val evaluation = evaluations[contour.id]
                ContourReading(contour.id, scene.measurements.last { it.pathId == contour.id }.id,
                    evaluation?.area, evaluation?.perimeter, evaluation?.area != null)
            }
            renderer.retainLabels(scene.measurements.map { it.id }.toSet() +
                contours.map { contourLabelId(it.id) } +
                (if (start != null) setOf(PREVIEW_LABEL_ID) else emptySet()) +
                (if (captureRequest?.isVolume == true) setOf(-3L) else emptySet()))
            if (cameraTracking) {
                if (spaceScan.phase.isPreparing) renderer.drawPlanes(scanPlanes)
                evaluations.values.filterNotNull().forEach { renderer.drawContourFill(it.triangles) }
                val areaLabelBounds = evaluations.mapValues { (id, evaluation) ->
                    if (evaluation?.labelPosition != null && evaluation.area != null && evaluation.perimeter != null)
                        renderer.worldLabelBounds(contourLabelId(id),
                            ContourFormatter.label(evaluation.area, evaluation.perimeter, measurementUnit),
                            evaluation.labelPosition, width, height) else null
                }
                scene.measurements.zip(readings).forEach { (measurement, reading) ->
                    val visible = listOf(measurement.startAnchor, measurement.endAnchor)
                        .filter { it.trackingState == TrackingState.TRACKING }.map { it.pose }
                    renderer.drawMeasurement(visible, width, height,
                        reading.distanceMeters?.let { DistanceFormatter.format(it, measurementUnit) }, labelId = measurement.id,
                        avoidLabel = areaLabelBounds[measurement.pathId])
                }
                if (start != null && startTracking) renderer.drawMeasurement(listOf(start.pose), width, height,
                    previewDistance?.let { text(R.string.approximate_value, DistanceFormatter.format(it, measurementUnit)) },
                    previewPose = previewPose, labelId = PREVIEW_LABEL_ID, previewIsLive = hit != null)
                contours.forEach { contour ->
                    val evaluation = evaluations[contour.id]
                    if (evaluation?.labelPosition != null && evaluation.area != null && evaluation.perimeter != null) {
                        renderer.drawWorldLabel(contourLabelId(contour.id),
                            ContourFormatter.label(evaluation.area, evaluation.perimeter, measurementUnit),
                            evaluation.labelPosition, width, height)
                    }
                }
                capture.draw(renderer, width, height, hit)
            }
            val hint = when {
                !cameraTracking -> trackingHint(frame.camera.trackingFailureReason)
                spaceScan.phase == SpaceScanPhase.RECOVERING -> scanHint()
                notice != null && SystemClock.uptimeMillis() < noticeUntil -> notice
                scene.atLimit && captureRequest == null -> LIMIT_MESSAGE
                spaceScan.phase != SpaceScanPhase.MEASURING -> scanHint()
                !startTracking -> text(R.string.return_to_the_first_point_to_restore)
                hit == null -> NO_SURFACE_MESSAGE
                else -> null
            }
            diagnostics?.frame(frame, candidates, hit, scene, readings, spaceScan,
                canPlace && startTracking && scene.canAddSegment,
                action?.let { mapOf(
                    "type" to when (it) {
                        is Command.Place -> "place"
                        is Command.Undo -> "undo"
                        is Command.CancelCurrent -> "cancel_current"
                        is Command.Continue -> "continue_line"
                        is Command.CloseContour -> "close_contour"
                        Command.Reset -> "reset"
                        Command.BeginMeasurement -> "begin_measurement"
                    },
                    "expected_revision" to when (it) {
                        is Command.Place -> it.revision
                        is Command.Undo -> it.revision
                        is Command.CancelCurrent -> it.revision
                        is Command.Continue -> it.revision
                        is Command.CloseContour -> it.revision
                        else -> null
                    },
                    "revision_before" to revisionBefore,
                    "outcome" to (commandOutcome ?: if (edited) "APPLIED" else "REJECTED"),
                    "scan_phase" to spaceScan.phase.name,
                    "camera_tracking" to cameraTracking, "start_tracking" to startTracking,
                    "has_hit" to (hit != null), "at_limit" to scene.atLimit,
                    "completed_measurement_id" to if (commandOutcome == "COMPLETED") scene.measurements.last().id else null,
                ) }, renderStartedNanos, width, height, surface.display?.rotation ?: 0)
            publish(ArFrameState(if (start == null) 0 else 1, canPlace && startTracking && scene.canAddSegment,
                startTracking, readings, previewDistance, hit != null && previewPose != null, hint,
                hasTarget = hit != null, sceneRevision = scene.revision,
                scanPhase = spaceScan.phase, scanProgress = spaceScan.progress,
                canBeginMeasurement = spaceScan.canBeginMeasurement, capture = captureFrame,
                trackingIssue = if (!cameraTracking) trackingIssue(frame.camera.trackingFailureReason) else null,
                scanHasSurface = cameraTracking && (hit != null || scanPlanes.isNotEmpty()),
                contours = contourReadings,
                isContinuing = scene.isContinuing,
                cornerCount = activeCorners.size,
                canContinue = scene.canContinue && cameraTracking,
                canCloseContour = scene.canClose && cornersTracking && activeEvaluation?.area != null &&
                    spaceScan.phase == SpaceScanPhase.MEASURING,
                contourError = activeEvaluation?.error,
                segmentLimitReached = !scene.canAddSegment,
                notice = notice?.takeIf { SystemClock.uptimeMillis() < noticeUntil }), action != null || removed ||
                    captureFrame?.revision != lastFrameState?.capture?.revision || captureFrame?.confirmed != lastFrameState?.capture?.confirmed ||
                    captureFrame?.manualHeight==true&&captureFrame.signedHeight != lastFrameState?.capture?.signedHeight)

        } catch (exception: Exception) {
            reportRenderError(exception)
        }
    }

    private fun centerHits(frame: Frame): List<SurfaceHit<HitResult>> {
        val cameraPose = frame.camera.pose
        return frame.hitTest(width / 2f, height / 2f).mapNotNull { hit ->
            val trackable = hit.trackable
            if (trackable.trackingState != TrackingState.TRACKING) return@mapNotNull null
            val kind = when (trackable) {
                is Plane -> {
                    if (!trackable.isPoseInPolygon(hit.hitPose)) return@mapNotNull null
                    val normal = hit.hitPose.getTransformedAxis(1, 1f)
                    val facing = (cameraPose.tx() - hit.hitPose.tx()) * normal[0] +
                        (cameraPose.ty() - hit.hitPose.ty()) * normal[1] +
                        (cameraPose.tz() - hit.hitPose.tz()) * normal[2]
                    if (facing <= 0f) return@mapNotNull null
                    SurfaceKind.PLANE
                }
                is DepthPoint -> SurfaceKind.DEPTH
                else -> return@mapNotNull null
            }
            SurfaceHit(hit, kind, hit.distance.toDouble())
        }
    }

    private fun scanHint(): String = when (spaceScan.phase) {
        SpaceScanPhase.SCANNING -> text(R.string.inspect_the_floor_walls_and_object_slowly)
        SpaceScanPhase.READY -> if (spaceScan.canBeginMeasurement)
            text(R.string.surfaces_found_start_measuring_and_continue_scanning)
            else text(R.string.scan_saved_restoring_tracking)
        SpaceScanPhase.RECOVERING -> text(R.string.return_to_the_object_to_restore_tracking)
        SpaceScanPhase.MEASURING -> NO_SURFACE_MESSAGE
    }

    private fun showNotice(message: String) {
        notice = message
        noticeUntil = SystemClock.uptimeMillis() + 4_000L
    }

    private fun contourLabelId(id: Long) = -1000L - id

    private fun Pose.point() = Point3D(tx().toDouble(), ty().toDouble(), tz().toDouble())

    private fun publish(state: ArFrameState, force: Boolean = false) {
        val now = SystemClock.uptimeMillis()
        if (!force && (now - lastUiUpdate < 100 || state == lastFrameState)) return
        lastUiUpdate = now
        lastFrameState = state
        handler.post { if (!destroyed && !failed && running) onFrame(state) }
    }

    private fun reportRenderError(exception: Exception) {
        running = false
        handler.post { if (!destroyed) handleException(exception) }
    }

    private fun handleException(exception: Exception) {
        diagnostics?.event("session_error", mapOf("exception" to exception.javaClass.simpleName))
        Log.e(TAG, "AR session failed", exception)
        val status = when (exception) {
            is UnavailableDeviceNotCompatibleException -> MeasurementStatus.ArNotSupported
            is UnavailableUserDeclinedInstallationException -> MeasurementStatus.Error(text(R.string.install_google_play_services_for_ar_to))
            is UnavailableArcoreNotInstalledException, is UnavailableApkTooOldException -> MeasurementStatus.Error(text(R.string.you_need_to_install_or_update_google))
            is UnavailableSdkTooOldException -> MeasurementStatus.Error(text(R.string.the_ar_version_in_the_application_is))
            is CameraNotAvailableException -> MeasurementStatus.Error(text(R.string.camera_unavailable_close_other_camera_apps_and))
            is SecurityException -> MeasurementStatus.CameraPermissionRequired
            else -> MeasurementStatus.Error(text(R.string.failed_to_start_ar_measurement_please_try))
        }
        fail(status)
    }

    private fun fail(status: MeasurementStatus) {
        failed = true
        pause()
        onStatus(status)
    }

    private fun trackingHint(reason: TrackingFailureReason): String = when (reason) {
        TrackingFailureReason.INSUFFICIENT_LIGHT -> text(R.string.too_dark_add_light_and_move_your)
        TrackingFailureReason.EXCESSIVE_MOTION -> text(R.string.move_the_phone_more_slowly)
        TrackingFailureReason.INSUFFICIENT_FEATURES -> text(R.string.point_the_camera_at_a_surface_with)
        TrackingFailureReason.BAD_STATE -> text(R.string.return_to_the_surface_to_restore_tracking)
        TrackingFailureReason.CAMERA_UNAVAILABLE -> text(R.string.camera_is_temporarily_unavailable)
        else -> text(R.string.move_the_phone_slowly_to_find_the)
    }

    private fun trackingIssue(reason: TrackingFailureReason): TrackingIssue? = when (reason) {
        TrackingFailureReason.INSUFFICIENT_LIGHT -> TrackingIssue.LOW_LIGHT
        TrackingFailureReason.EXCESSIVE_MOTION -> TrackingIssue.TOO_MUCH_MOTION
        TrackingFailureReason.INSUFFICIENT_FEATURES -> TrackingIssue.FEW_FEATURES
        TrackingFailureReason.CAMERA_UNAVAILABLE -> TrackingIssue.CAMERA_UNAVAILABLE
        TrackingFailureReason.BAD_STATE -> TrackingIssue.RELOCALIZING
        else -> null
    }

    companion object {
        private const val TAG = "RulivoAR"
        private const val PREVIEW_LABEL_ID = 0L
        private val NO_SURFACE_MESSAGE get() = text(R.string.aim_at_the_surface_of_the_object)
        private val LIMIT_MESSAGE get() = text(R.string.the_scene_already_has_30_measurements_undo)
    }
}
