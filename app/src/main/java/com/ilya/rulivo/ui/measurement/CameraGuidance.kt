package com.ilya.rulivo.ui.measurement

import com.ilya.rulivo.R
import com.ilya.rulivo.i18n.text

import com.ilya.rulivo.ar.CaptureFrame
import com.ilya.rulivo.ar.SpaceScanPhase
import com.ilya.rulivo.ar.TrackingIssue

internal data class CameraGuidance(val title: String, val instruction: String, val detail: String? = null)

/** One source of guidance for the ruler, contours, figures and height controls. */
internal fun MeasurementUiState.cameraGuidance(frame: CaptureFrame? = null): CameraGuidance {
    val guidance = cameraGuidanceContent(frame)
    return if (frame == null && trackingIssue == null && scanPhase == SpaceScanPhase.MEASURING && notice != null)
        guidance.copy(detail = notice) else guidance
}

private fun MeasurementUiState.cameraGuidanceContent(frame: CaptureFrame?): CameraGuidance {
    val retained = when {
        scanPhase.isPreparing && scanProgress > 0f -> text(R.string.preparation_progress_saved_continue_from_here)
        scanPhase.isPreparing -> text(R.string.preparation_will_continue_when_the_camera_sees)
        else -> text(R.string.measurement_paused_continue_when_tracking_is_restored)
    }
    trackingIssue?.let { issue ->
        val (title, action) = when (issue) {
            TrackingIssue.LOW_LIGHT -> text(R.string.not_enough_light) to text(R.string.add_light_and_point_the_camera_at)
            TrackingIssue.TOO_MUCH_MOTION -> text(R.string.move_the_phone_more_slowly) to text(R.string.stop_for_a_moment_then_smoothly_move)
            TrackingIssue.FEW_FEATURES -> text(R.string.camera_needs_more_visual_detail) to text(R.string.point_the_camera_at_an_area_with)
            TrackingIssue.CAMERA_UNAVAILABLE -> text(R.string.camera_is_temporarily_unavailable) to text(R.string.close_other_camera_applications_and_return_to)
            TrackingIssue.RELOCALIZING -> text(R.string.return_to_measurement_location) to text(R.string.show_the_camera_the_same_area_from)
        }
        return CameraGuidance(title, action, retained)
    }
    if (scanPhase == SpaceScanPhase.RECOVERING ||
        scanPhase == SpaceScanPhase.READY && !canBeginMeasurement) {
        return CameraGuidance(text(R.string.restoring_tracking),
            text(R.string.bring_back_into_frame_the_area_that), retained)
    }
    if (scanPhase.isPreparing) return when {
        canBeginMeasurement -> CameraGuidance(text(R.string.surfaces_found),
            text(R.string.tap_start_measurement_then_aim_the_crosshair),
            text(R.string.there_is_no_need_to_scan_the))
        scanHasSurface -> CameraGuidance(text(R.string.surface_recognized),
            text(R.string.continue_to_smoothly_move_the_phone_left),
            text(R.string.collecting_reference_points_for_measurements))
        scanProgress > 0f -> CameraGuidance(text(R.string.bring_the_surface_back_into_frame),
            text(R.string.point_the_camera_back_at_the_floor), retained)
        else -> CameraGuidance(text(R.string.find_a_surface),
            text(R.string.point_the_camera_at_the_floor_or),
            text(R.string.select_a_lit_area_with_a_pattern))
    }
    if (frame != null) return CameraGuidance(
        title = when {
            !frame.tracking -> text(R.string.measurement_paused)
            frame.replacement != null -> text(R.string.correct_point, frame.replacement + 1)
            frame.canConfirm -> text(R.string.check_result)
            else -> frame.stage.ifBlank { text(R.string.mark_point, frame.count + 1) }
        },
        instruction = frame.hint,
        detail = if (frame.tracking && frame.showPlace && !frame.canPlace && !hasTarget)
            text(R.string.no_surface_under_the_crosshair_aim_at) else null,
    )
    return when {
        !isTracking -> CameraGuidance(text(R.string.measurement_paused), hint, retained)
        isContinuing -> CameraGuidance(text(R.string.contour_corners, cornerCount),
            when {
                segmentLimitReached -> text(R.string.contour_close_or_finish)
                !canPlacePoint -> hint
                else -> text(R.string.contour_instruction)
            }, contourError ?: if (cornerCount < 3) text(R.string.mark_at_least_three_corners) else null)
        atLimit -> CameraGuidance(text(R.string.there_are_30_lines_in_the_scene), hint)
        !canPlacePoint -> CameraGuidance(text(R.string.find_a_surface_under_the_crosshair), hint,
            if (pointCount == 1) text(R.string.first_point_saved_aim_the_crosshair_at)
            else text(R.string.aim_at_a_visible_part_of_the))
        pointCount == 1 -> CameraGuidance(text(R.string.message_2_2_end_of_line),
            text(R.string.aim_at_the_end_and_tap_place))
        else -> CameraGuidance(text(R.string.message_1_2_start_of_line),
            text(R.string.aim_at_the_start_and_tap_place),
            if (canContinue) text(R.string.contour_continue_hint)
            else if (measurements.isNotEmpty()) text(R.string.line_saved_you_can_measure_the_next) else null)
    }
}

internal val MeasurementUiState.scanActionLabel: String
    get() = when {
        canBeginMeasurement -> text(R.string.start_measurement)
        trackingIssue != null || scanPhase == SpaceScanPhase.READY -> text(R.string.waiting_for_camera_tracking)
        scanHasSurface -> text(R.string.preparing_the_surface)
        else -> text(R.string.searching_for_a_surface)
    }
