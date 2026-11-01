package com.ilya.rulivo.ar

/** Samples fresh frames at 10 Hz, while counting repeats separately for performance analysis. */
internal class DiagnosticFrameSampler(private val intervalNanos: Long = 100_000_000L) {
    private var lastTimestamp = 0L
    private var lastSampleAt: Long? = null
    private var freshFrames = 0
    private var repeatedFrames = 0
    private var invalidFrames = 0

    fun observe(timestampNanos: Long, observedAtNanos: Long, force: Boolean = false): Map<String, Any?>? {
        val frameIsNew = timestampNanos > 0 && timestampNanos > lastTimestamp
        when {
            timestampNanos <= 0 || timestampNanos < lastTimestamp -> invalidFrames++
            timestampNanos == lastTimestamp -> repeatedFrames++
            else -> { freshFrames++; lastTimestamp = timestampNanos }
        }
        val previous = lastSampleAt
        if (!force && previous != null && observedAtNanos >= previous &&
            observedAtNanos - previous < intervalNanos) return null
        lastSampleAt = observedAtNanos
        return mapOf("frame_timestamp_ns" to timestampNanos, "observed_elapsed_ns" to observedAtNanos,
            "camera_frame_is_new" to frameIsNew,
            "sample_interval_ns" to previous?.let { observedAtNanos - it },
            "fresh_frames" to freshFrames, "repeated_frames" to repeatedFrames,
            "invalid_frames" to invalidFrames).also {
            freshFrames = 0
            repeatedFrames = 0
            invalidFrames = 0
        }
    }

    fun interrupt() {
        // Keep the timestamp: a repeated camera image after resume is still not a new observation.
        lastSampleAt = null
        freshFrames = 0
        repeatedFrames = 0
        invalidFrames = 0
    }
}
