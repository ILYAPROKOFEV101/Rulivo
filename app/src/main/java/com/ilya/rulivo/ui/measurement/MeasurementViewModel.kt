package com.ilya.rulivo.ui.measurement

import com.ilya.rulivo.R
import com.ilya.rulivo.i18n.text

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.ilya.rulivo.ar.ArFrameState
import com.ilya.rulivo.ar.SpaceScanPhase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class MeasurementViewModel(private val savedState: SavedStateHandle) : ViewModel() {
    private val mutableState = MutableStateFlow(MeasurementUiState(
        unit = savedState.get<String>("unit")?.let { name ->
            MeasurementUnit.entries.firstOrNull { it.name == name }
        } ?: MeasurementUnit.AUTO,
    ))
    val uiState = mutableState.asStateFlow()

    fun selectUnit(unit: MeasurementUnit) {
        savedState["unit"] = unit.name
        mutableState.update { it.copy(unit = unit) }
    }

    fun onStatus(status: MeasurementStatus) {
        mutableState.update {
            MeasurementUiState(status = status, unit = it.unit, hint = when (status) {
                MeasurementStatus.CheckingAvailability -> text(R.string.checking_ar_support)
                MeasurementStatus.ScanningSpace -> text(R.string.slowly_examine_the_space_and_surfaces_of)
                MeasurementStatus.CameraPermissionRequired -> text(R.string.camera_access_required_for_measurements)
                MeasurementStatus.ArNotSupported -> text(R.string.this_device_does_not_support_ar_measurements)
                is MeasurementStatus.Error -> status.message
                else -> text(R.string.move_the_phone_slowly_to_find_the)
            })
        }
    }

    fun onFrame(frame: ArFrameState) {
        mutableState.update { previous ->
            val previousById = previous.measurements.associateBy { it.id }
            val readings = frame.measurements.map { reading ->
                reading.copy(distanceMeters = reading.distanceMeters ?: previousById[reading.id]?.distanceMeters)
            }
            val previousContours = previous.contours.associateBy { it.id }
            val contours = frame.contours.map { reading ->
                reading.copy(areaSquareMeters = reading.areaSquareMeters ?: previousContours[reading.id]?.areaSquareMeters,
                    perimeterMeters = reading.perimeterMeters ?: previousContours[reading.id]?.perimeterMeters)
            }
            val canPlace = frame.canPlacePoint && frame.isTracking && readings.size < 30 && frame.scanPhase == SpaceScanPhase.MEASURING
            previous.copy(
                status = when {
                    frame.scanPhase.isPreparing -> MeasurementStatus.ScanningSpace
                    !canPlace -> MeasurementStatus.WaitingForSurface
                    frame.pointCount == 1 -> MeasurementStatus.ReadyForSecondPoint
                    else -> MeasurementStatus.ReadyForFirstPoint
                },
                pointCount = frame.pointCount,
                capture = frame.capture,
                trackingIssue = frame.trackingIssue,
                scanHasSurface = frame.scanHasSurface && frame.isTracking,
                notice = frame.notice,
                measurements = readings,
                contours = contours,
                isContinuing = frame.isContinuing,
                cornerCount = frame.cornerCount,
                canContinue = frame.canContinue && frame.isTracking,
                canCloseContour = frame.canCloseContour && frame.isTracking,
                contourError = frame.contourError,
                segmentLimitReached = frame.segmentLimitReached,
                previewDistanceMeters = if (frame.pointCount == 1) frame.previewDistanceMeters ?:
                    previous.previewDistanceMeters.takeIf { previous.pointCount == 1 && previous.sceneRevision == frame.sceneRevision }
                    else null,
                previewIsLive = frame.previewIsLive && frame.isTracking,
                canPlacePoint = canPlace,
                isTracking = frame.isTracking,
                hasTarget = frame.hasTarget && frame.isTracking,
                sceneRevision = frame.sceneRevision,
                scanPhase = frame.scanPhase,
                scanProgress = frame.scanProgress,
                canBeginMeasurement = frame.canBeginMeasurement && frame.isTracking && frame.scanPhase == SpaceScanPhase.READY,
                hint = frame.trackingHint ?: when {
                    frame.scanPhase == SpaceScanPhase.SCANNING -> text(R.string.slowly_examine_the_space_and_surfaces_of)
                    frame.scanPhase == SpaceScanPhase.READY -> text(R.string.surfaces_found_you_can_start_measuring)
                    frame.scanPhase == SpaceScanPhase.RECOVERING -> text(R.string.return_to_the_object_to_restore_tracking)
                    readings.size >= 30 -> text(R.string.the_scene_already_has_30_measurements_undo)
                    !canPlace -> text(R.string.aim_at_the_surface_of_the_object)
                    frame.pointCount == 1 -> text(R.string.mark_the_end_of_the_segment)
                    readings.isNotEmpty() -> text(R.string.line_saved_start_the_next_measurement)
                    else -> text(R.string.aim_at_the_beginning_of_the_segment)
                },
            )
        }
    }
}
