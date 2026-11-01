package com.ilya.rulivo.ui.measurement

import com.ilya.rulivo.R
import com.ilya.rulivo.i18n.text

import com.ilya.rulivo.ar.CaptureFrame
import com.ilya.rulivo.ar.SpaceScanPhase
import com.ilya.rulivo.ar.TrackingIssue

internal enum class CameraMotionCue(private val textId: Int) {
    SWEEP(R.string.move_the_phone_smoothly_left_and_right),
    STEADY(R.string.hold_the_phone_still_for_a_moment),
    RETURN(R.string.show_the_camera_the_previous_area),
    LIGHT(R.string.add_light_to_the_surface),
    DETAILS(R.string.aim_at_an_area_with_visible_details),
    AIM(R.string.aim_at_the_surface),
    HEIGHT(R.string.raise_and_lower_the_crosshair),
    READY(R.string.camera_ready_to_measure);
    val caption: String get() = text(textId)
}

/** These are demonstrations of gestures, never directions inferred from an unseen room. */
internal fun MeasurementUiState.cameraMotionCue(frame: CaptureFrame? = null): CameraMotionCue? = when {
    trackingIssue == TrackingIssue.CAMERA_UNAVAILABLE -> null
    trackingIssue == TrackingIssue.LOW_LIGHT -> CameraMotionCue.LIGHT
    trackingIssue == TrackingIssue.TOO_MUCH_MOTION -> CameraMotionCue.STEADY
    trackingIssue == TrackingIssue.FEW_FEATURES -> CameraMotionCue.DETAILS
    trackingIssue == TrackingIssue.RELOCALIZING || scanPhase == SpaceScanPhase.RECOVERING -> CameraMotionCue.RETURN
    scanPhase == SpaceScanPhase.READY -> if (canBeginMeasurement) CameraMotionCue.READY else CameraMotionCue.RETURN
    scanPhase.isPreparing -> if (scanProgress > 0f && !scanHasSurface) CameraMotionCue.RETURN else CameraMotionCue.SWEEP
    frame != null -> when {
        !frame.tracking -> CameraMotionCue.RETURN
        frame.cameraHeight && frame.cameraHeightReady -> CameraMotionCue.HEIGHT
        frame.showPlace && !frame.canPlace && !hasTarget -> CameraMotionCue.AIM
        else -> null
    }
    !isTracking -> CameraMotionCue.RETURN
    !canPlacePoint && !hasTarget && !atLimit -> CameraMotionCue.AIM
    else -> null
}
