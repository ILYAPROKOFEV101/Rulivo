package com.ilya.rulivo.ar

// Only immutable values cross to Compose. No lifecycle-bound AR objects enter the ViewModel.
data class MeasurementReading(val id: Long, val distanceMeters: Double?, val isTracking: Boolean)

data class ContourReading(val id: Long, val lastEdgeId: Long, val areaSquareMeters: Double?,
    val perimeterMeters: Double?, val isTracking: Boolean)

data class ArFrameState(
    val pointCount: Int,
    val canPlacePoint: Boolean,
    val isTracking: Boolean,
    val measurements: List<MeasurementReading> = emptyList(),
    val previewDistanceMeters: Double? = null,
    val previewIsLive: Boolean = false,
    val trackingHint: String? = null,
    val hasTarget: Boolean = canPlacePoint,
    val sceneRevision: Long = 0,
    val scanPhase: SpaceScanPhase = SpaceScanPhase.SCANNING,
    val scanProgress: Float = 0f,
    val canBeginMeasurement: Boolean = false,
    val capture: CaptureFrame? = null,
    val trackingIssue: TrackingIssue? = null,
    val scanHasSurface: Boolean = false,
    val notice: String? = null,
    val contours: List<ContourReading> = emptyList(),
    val isContinuing: Boolean = false,
    val cornerCount: Int = 0,
    val canContinue: Boolean = false,
    val canCloseContour: Boolean = false,
    val contourError: String? = null,
    val segmentLimitReached: Boolean = false,

)
