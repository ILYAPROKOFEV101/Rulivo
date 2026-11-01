package com.ilya.rulivo.ar

import android.app.Activity
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.google.ar.core.Anchor
import com.google.ar.core.Frame
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.core.Pose
import java.io.File

/** Optional baseline instrumentation. ARCore values are copied on the GL thread before enqueueing. */
internal class AccuracyDiagnostics private constructor(private val journal: DiagnosticJournal) {
    private val sampler = DiagnosticFrameSampler()

    fun event(type: String, fields: Map<String, Any?> = emptyMap()) = safely {
        journal.record(type, fields + ("observed_elapsed_ns" to SystemClock.elapsedRealtimeNanos()))
    }

    fun interrupt(reason: String) {
        sampler.interrupt()
        event(reason)
    }

    fun frame(
        frame: Frame,
        candidates: List<SurfaceHit<HitResult>>,
        selected: HitResult?,
        scene: MeasurementScene<Anchor>,
        readings: List<MeasurementReading>,
        scan: SpaceScanTracker,
        canPlace: Boolean,
        command: Map<String, Any?>?,
        renderStartedNanos: Long,
        width: Int,
        height: Int,
        rotation: Int,
    ) = safely {
        val observed = SystemClock.elapsedRealtimeNanos()
        val sample = sampler.observe(frame.timestamp, observed, force = command != null) ?: return@safely
        val snapshotStarted = System.nanoTime()
        val cameraTracking = frame.camera.trackingState.name
        val fields = sample + mapOf(
            "camera_tracking" to cameraTracking,
            "tracking_failure" to frame.camera.trackingFailureReason.name,
            "camera_pose" to frame.camera.pose.snapshot(),
            "viewport" to listOf(width, height), "display_rotation" to rotation,
            "reticle_pixels" to listOf(width / 2f, height / 2f),
            "scan_phase" to scan.phase.name, "scan_progress" to scan.progress,
            "can_place" to canPlace, "scene_revision" to scene.revision,
            "candidates" to candidates.map { it.value.snapshot(it.kind.name) },
            "selected_hit" to selected?.snapshot(if (selected.trackable is Plane) "PLANE" else "DEPTH"),
            "current_start" to scene.currentStart?.snapshot(),
            "measurements" to scene.measurements.zip(readings).map { (measurement, reading) ->
                mapOf("id" to measurement.id, "distance_m" to reading.distanceMeters,
                    "tracking" to reading.isTracking, "start" to measurement.startAnchor.snapshot(),
                    "end" to measurement.endAnchor.snapshot(),
                    "end_in_start" to measurement.startAnchor.pose.inverse().compose(measurement.endAnchor.pose).snapshot())
            },
            "command" to command,
            // CPU time through rendering; not GPU time or sensor-to-display latency.
            "frame_work_ns" to (snapshotStarted - renderStartedNanos),
        )
        journal.record("frame", fields + ("diagnostic_snapshot_ns" to (System.nanoTime() - snapshotStarted)))
    }

    fun close() = journal.close()

    private inline fun safely(block: () -> Unit) {
        try { block() } catch (exception: Exception) {
            // Diagnostics must not change readiness, placement, or AR session failure behavior.
            Log.w(TAG, "Diagnostic snapshot unavailable", exception)
        }
    }

    private fun Pose.snapshot(): Map<String, Any?> = mapOf(
        "translation_m" to translation.toList(), "rotation_xyzw" to rotationQuaternion.toList())

    private fun Anchor.snapshot(): Map<String, Any?> = mapOf(
        "tracking" to trackingState.name, "pose" to pose.snapshot())

    private fun HitResult.snapshot(kind: String): Map<String, Any?> {
        val plane = trackable as? Plane
        return mapOf("source" to kind, "distance_to_camera_m" to distance,
            "pose" to hitPose.snapshot(), "surface_id" to trackable.hashCode().toString(16),
            "plane_type" to plane?.type?.name, "plane_pose" to plane?.centerPose?.snapshot(),
            "pose_in_plane" to plane?.centerPose?.inverse()?.compose(hitPose)?.snapshot())
    }

    companion object {
        private const val TAG = "RulivoDiagnostics"
        const val INTENT_EXTRA = "accuracy_diagnostics"

        fun create(activity: Activity, depthSupported: Boolean): AccuracyDiagnostics? {
            if (activity.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0 ||
                !activity.intent.getBooleanExtra(INTENT_EXTRA, false)) return null
            return try {
                @Suppress("DEPRECATION")
                val arcore = activity.packageManager.getPackageInfo("com.google.ar.core", 0)
                @Suppress("DEPRECATION")
                val app = activity.packageManager.getPackageInfo(activity.packageName, 0)
                AccuracyDiagnostics(DiagnosticJournal(File(activity.noBackupFilesDir, "accuracy"), mapOf(
                    "algorithm" to "baseline_hit_test_v1", "instrumentation" to "accuracy_journal_v1",
                    "manufacturer" to Build.MANUFACTURER, "model" to Build.MODEL,
                    "device" to Build.DEVICE, "android_api" to Build.VERSION.SDK_INT,
                    "app_version" to app.versionName, "arcore_version" to arcore.versionName,
                    "depth_supported" to depthSupported,
                    "depth_mode" to if (depthSupported) "AUTOMATIC" else "DISABLED",
                    "observed_elapsed_ns" to SystemClock.elapsedRealtimeNanos(),
                    "sample_interval_ms" to 100, "images_recorded" to false,
                ), onFailure = { Log.w(TAG, "Diagnostic journal unavailable", it) }))
            } catch (exception: Exception) {
                Log.w(TAG, "Diagnostic journal could not start", exception)
                null
            }
        }
    }
}
