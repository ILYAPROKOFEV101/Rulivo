package com.ilya.rulivo.ar

import kotlin.math.min

/** Readiness of the current AR session, not a claim that the whole room has been mapped. */
enum class SpaceScanPhase {
    SCANNING, READY, MEASURING, RECOVERING;
    val isPreparing: Boolean get() = this == SCANNING || this == READY
}

/** Frame-based scan/recovery gate. After preparation there is no dwell at individual targets. */
internal class SpaceScanTracker {
    var phase = SpaceScanPhase.SCANNING
        private set
    var progress = 0f
        private set
    val canBeginMeasurement: Boolean
        get() = phase == SpaceScanPhase.READY && fresh && recoveryFrames >= RECOVERY_FRAMES
    private var surfaceFrames = 0
    private var surfaceObservationNanos = 0L
    private var recoveryFrames = 0
    private var lastTimestamp = 0L
    private var lastObservedAt = 0L
    private var fresh = false
    private var measurementStarted = false

    // hasSurface describes the session's tracked surfaces, not just the centre hit-test.
    fun update(timestampNanos: Long, observedAtNanos: Long, tracking: Boolean,
        cameraPosition: Point3D?, hasSurface: Boolean) {
        if (!tracking || timestampNanos <= 0L || cameraPosition == null ||
            !cameraPosition.x.isFinite() || !cameraPosition.y.isFinite() || !cameraPosition.z.isFinite()) {
            interrupt()
            return
        }
        if (timestampNanos == lastTimestamp) {
            if (observedAtNanos - lastObservedAt > MAX_FRAME_GAP_NANOS) interrupt()
            return
        }
        if (timestampNanos < lastTimestamp) {
            interrupt()
            return
        }
        if (lastTimestamp != 0L && (timestampNanos - lastTimestamp > MAX_FRAME_GAP_NANOS ||
                observedAtNanos - lastObservedAt > MAX_FRAME_GAP_NANOS ||
                observedAtNanos < lastObservedAt)) interrupt()
        // Never count time spent paused, without tracking, or waiting for a new camera frame.
        val observationNanos = if (fresh) timestampNanos - lastTimestamp else 0L
        lastTimestamp = timestampNanos
        lastObservedAt = observedAtNanos
        fresh = true
        recoveryFrames = (recoveryFrames + 1).coerceAtMost(RECOVERY_FRAMES)
        if (measurementStarted) {
            if (recoveryFrames >= RECOVERY_FRAMES) phase = SpaceScanPhase.MEASURING
            return
        }
        // Scan evidence belongs to this session. Looking away must not erase partial progress.
        if (phase == SpaceScanPhase.READY) return
        if (hasSurface) {
            surfaceFrames = (surfaceFrames + 1).coerceAtMost(MIN_SURFACE_FRAMES)
            surfaceObservationNanos = (surfaceObservationNanos + observationNanos).coerceAtMost(MIN_OBSERVATION_NANOS)
        }
        progress = min(surfaceObservationNanos.toFloat() / MIN_OBSERVATION_NANOS,
            surfaceFrames.toFloat() / MIN_SURFACE_FRAMES)
        if (progress >= 1f) phase = SpaceScanPhase.READY
    }

    fun beginMeasurement(): Boolean {
        if (!canBeginMeasurement) return false
        measurementStarted = true
        phase = SpaceScanPhase.MEASURING
        return true
    }

    fun canPlace(hasSurface: Boolean): Boolean = phase == SpaceScanPhase.MEASURING && fresh && hasSurface

    /** Session.pause / lost tracking invalidate frame freshness, but keep the scene and scan history. */
    fun interrupt() {
        fresh = false
        // Keep the last timestamp so the same stalled frame cannot become "new" after a pause.
        recoveryFrames = 0
        if (measurementStarted) {
            phase = SpaceScanPhase.RECOVERING
        }
    }

    private companion object {
        const val MAX_FRAME_GAP_NANOS = 250_000_000L
        const val MIN_OBSERVATION_NANOS = 800_000_000L
        const val MIN_SURFACE_FRAMES = 12
        const val RECOVERY_FRAMES = 3
    }
}
