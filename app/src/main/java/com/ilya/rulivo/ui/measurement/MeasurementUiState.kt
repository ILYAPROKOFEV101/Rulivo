package com.ilya.rulivo.ui.measurement

import com.ilya.rulivo.R
import com.ilya.rulivo.i18n.text

import com.ilya.rulivo.ar.ContourReading
import com.ilya.rulivo.ar.MeasurementReading
import com.ilya.rulivo.ar.MeasurementScene
import com.ilya.rulivo.ar.SpaceScanPhase

sealed interface MeasurementStatus {
    data object CheckingAvailability : MeasurementStatus
    data object CameraPermissionRequired : MeasurementStatus
    data object ScanningSpace : MeasurementStatus
    data object WaitingForSurface : MeasurementStatus
    data object ReadyForFirstPoint : MeasurementStatus
    data object ReadyForSecondPoint : MeasurementStatus
    data object ArNotSupported : MeasurementStatus
    data class Error(val message: String) : MeasurementStatus
}

data class MeasurementUiState(
    val status: MeasurementStatus = MeasurementStatus.CheckingAvailability,
    val unit: MeasurementUnit = MeasurementUnit.AUTO,
    val measurements: List<MeasurementReading> = emptyList(),
    val previewDistanceMeters: Double? = null,
    val previewIsLive: Boolean = false,
    val pointCount: Int = 0,
    val canPlacePoint: Boolean = false,
    val isTracking: Boolean = false,
    val hasTarget: Boolean = false,
    val sceneRevision: Long = 0,
    val scanPhase: SpaceScanPhase = SpaceScanPhase.SCANNING,
    val scanProgress: Float = 0f,
    val canBeginMeasurement: Boolean = false,
    val hint: String = text(R.string.checking_ar_support),
    val capture: com.ilya.rulivo.ar.CaptureFrame? = null,
    val trackingIssue: com.ilya.rulivo.ar.TrackingIssue? = null,
    val scanHasSurface: Boolean = false,
    val notice: String? = null,
    val contours: List<ContourReading> = emptyList(),
    val isContinuing: Boolean = false,
    val cornerCount: Int = 0,
    val canContinue: Boolean = false,
    val canCloseContour: Boolean = false,
    val contourError: String? = null,
    val segmentLimitReached: Boolean = false,

) {
    val lastContour: ContourReading? get() = contours.lastOrNull()?.takeIf { pointCount == 0 && it.lastEdgeId == measurements.lastOrNull()?.id }
    val atLimit: Boolean get() = measurements.size >= MeasurementScene.MAX_MEASUREMENTS
    val hasElements: Boolean get() = pointCount > 0 || measurements.isNotEmpty()
    val distanceMeters: Double? get() = if (pointCount == 1) previewDistanceMeters else measurements.lastOrNull()?.distanceMeters
    val resultIsTracking: Boolean get() = if (pointCount == 1) isTracking && previewIsLive else measurements.lastOrNull()?.isTracking == true
}
