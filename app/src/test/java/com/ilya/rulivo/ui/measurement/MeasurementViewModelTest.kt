package com.ilya.rulivo.ui.measurement

import com.ilya.rulivo.R
import com.ilya.rulivo.i18n.text

import androidx.lifecycle.SavedStateHandle
import com.ilya.rulivo.ar.ArFrameState
import com.ilya.rulivo.ar.SpaceScanPhase
import com.ilya.rulivo.ar.MeasurementReading
import org.junit.Assert.*
import org.junit.Test

class MeasurementViewModelTest : com.ilya.rulivo.i18n.LocalizedTest() {
    @Test fun trackingPauseKeepsPreviewOnlyForTheSameUnfinishedSegment() {
        val model = MeasurementViewModel(SavedStateHandle())
        model.onFrame(ArFrameState(1, true, true, previewDistanceMeters = .8, previewIsLive = true,
            sceneRevision = 4, scanPhase = SpaceScanPhase.MEASURING))
        model.onFrame(ArFrameState(1, false, false, sceneRevision = 4, scanPhase = SpaceScanPhase.RECOVERING))
        assertEquals(.8, model.uiState.value.previewDistanceMeters!!, 0.0)
        assertFalse(model.uiState.value.resultIsTracking)
        model.onFrame(ArFrameState(1, false, true, sceneRevision = 6, scanPhase = SpaceScanPhase.MEASURING))
        assertNull(model.uiState.value.previewDistanceMeters)
    }

    @Test fun recoveryCauseClearsOnNextHealthyFrameWithoutClearingProgress() {
        val model = MeasurementViewModel(SavedStateHandle())
        model.onFrame(ArFrameState(0, false, false, scanProgress = .5f,
            trackingIssue = com.ilya.rulivo.ar.TrackingIssue.LOW_LIGHT))
        assertEquals(text(R.string.not_enough_light), model.uiState.value.cameraGuidance().title)
        model.onFrame(ArFrameState(0, false, true, scanProgress = .5f, scanHasSurface = true))
        assertNull(model.uiState.value.trackingIssue)
        assertEquals(.5f, model.uiState.value.scanProgress, 0f)
        assertEquals(text(R.string.surface_recognized), model.uiState.value.cameraGuidance().title)
    }

    @Test fun completingLineReturnsToFirstPointWithoutDiscardingMeasurements() {
        val model = MeasurementViewModel(SavedStateHandle())
        model.onFrame(ArFrameState(0, false, true, scanPhase = SpaceScanPhase.MEASURING))
        assertEquals(MeasurementStatus.WaitingForSurface, model.uiState.value.status)
        model.onFrame(ArFrameState(0, true, true, scanPhase = SpaceScanPhase.MEASURING))
        assertEquals(MeasurementStatus.ReadyForFirstPoint, model.uiState.value.status)
        model.onFrame(ArFrameState(1, true, true, previewDistanceMeters = 0.6, previewIsLive = true, scanPhase = SpaceScanPhase.MEASURING))
        assertEquals(MeasurementStatus.ReadyForSecondPoint, model.uiState.value.status)
        assertEquals(0.6, model.uiState.value.distanceMeters!!, 0.0)
        val completed = listOf(MeasurementReading(1L, 1.42, true))
        model.onFrame(ArFrameState(0, true, true, measurements = completed, scanPhase = SpaceScanPhase.MEASURING))
        assertEquals(MeasurementStatus.ReadyForFirstPoint, model.uiState.value.status)
        assertEquals(completed, model.uiState.value.measurements)
        assertEquals(1.42, model.uiState.value.distanceMeters!!, 0.0)
        model.onFrame(ArFrameState(0, false, true, scanPhase = SpaceScanPhase.MEASURING))
        assertNull(model.uiState.value.distanceMeters)
        assertTrue(model.uiState.value.measurements.isEmpty())
    }

    @Test fun pausedMeasurementsKeepLastValuesByIdWithoutResurrectingDeletedLines() {
        val model = MeasurementViewModel(SavedStateHandle())
        model.onFrame(ArFrameState(0, true, true, listOf(MeasurementReading(1, 0.5, true), MeasurementReading(2, 0.8, true)), scanPhase = SpaceScanPhase.MEASURING))
        model.onFrame(ArFrameState(0, false, false, listOf(MeasurementReading(1, null, false)), scanPhase = SpaceScanPhase.MEASURING))
        assertEquals(1, model.uiState.value.measurements.size)
        assertEquals(0.5, model.uiState.value.distanceMeters!!, 0.0)
        assertFalse(model.uiState.value.resultIsTracking)
        model.onFrame(ArFrameState(0, true, true, listOf(MeasurementReading(1, 0.51, true)), scanPhase = SpaceScanPhase.MEASURING))
        assertEquals(0.51, model.uiState.value.distanceMeters!!, 0.0)
    }

    @Test fun unitSurvivesSessionRestartAndSavedStateRestoration() {
        val saved = SavedStateHandle()
        val model = MeasurementViewModel(saved)
        model.selectUnit(MeasurementUnit.METERS)
        model.onStatus(MeasurementStatus.CheckingAvailability)
        assertEquals(MeasurementUnit.METERS, MeasurementViewModel(saved).uiState.value.unit)
    }

    @Test fun newSessionAndErrorsRemoveOldSceneRatherThanDuplicatingIt() {
        val model = MeasurementViewModel(SavedStateHandle())
        model.onFrame(ArFrameState(0, true, true, listOf(MeasurementReading(1, 2.0, true)), scanPhase = SpaceScanPhase.MEASURING))
        model.onStatus(MeasurementStatus.Error("Камера недоступна"))
        assertTrue(model.uiState.value.measurements.isEmpty())
        assertNull(model.uiState.value.distanceMeters)
        model.onStatus(MeasurementStatus.CheckingAvailability)
        model.onFrame(ArFrameState(0, true, true, listOf(MeasurementReading(1, 1.0, true)), scanPhase = SpaceScanPhase.MEASURING))
        assertEquals(1, model.uiState.value.measurements.size)
    }

    @Test fun missingSurfaceDisablesPlacementWhileRetainingFrozenPreview() {
        val model = MeasurementViewModel(SavedStateHandle())
        model.onFrame(ArFrameState(1, false, true, previewDistanceMeters = 0.8, previewIsLive = false, scanPhase = SpaceScanPhase.MEASURING))
        assertEquals(0.8, model.uiState.value.distanceMeters!!, 0.0)
        assertFalse(model.uiState.value.canPlacePoint)
        assertFalse(model.uiState.value.resultIsTracking)
        assertEquals(text(R.string.aim_at_the_surface_of_the_object), model.uiState.value.hint)
    }

    @Test fun limitDisablesPlacementAndUndoAllowsAnotherMeasurement() {
        val model = MeasurementViewModel(SavedStateHandle())
        val readings = (1L..30L).map { MeasurementReading(it, 1.0, true) }
        model.onFrame(ArFrameState(0, true, true, readings, scanPhase = SpaceScanPhase.MEASURING))
        assertTrue(model.uiState.value.atLimit)
        assertFalse(model.uiState.value.canPlacePoint)
        model.onFrame(ArFrameState(0, true, true, readings.dropLast(1), scanPhase = SpaceScanPhase.MEASURING))
        assertFalse(model.uiState.value.atLimit)
        assertTrue(model.uiState.value.canPlacePoint)
    }
    @Test fun scanReadinessDoesNotEnablePointsUntilMeasurementStarts() {
        val model = MeasurementViewModel(SavedStateHandle())
        model.onStatus(MeasurementStatus.ScanningSpace)
        model.onFrame(ArFrameState(0, true, true, scanPhase = SpaceScanPhase.READY, scanProgress = 1f))
        assertEquals(MeasurementStatus.ScanningSpace, model.uiState.value.status)
        assertFalse(model.uiState.value.canPlacePoint)
        assertEquals(1f, model.uiState.value.scanProgress, 0f)
        model.onFrame(ArFrameState(0, true, true, scanPhase = SpaceScanPhase.MEASURING, scanProgress = 1f))
        assertEquals(MeasurementStatus.ReadyForFirstPoint, model.uiState.value.status)
        assertTrue(model.uiState.value.canPlacePoint)
    }

    @Test fun recoveringTrackingPreservesFinishedLinesAndBlocksPlacement() {
        val model = MeasurementViewModel(SavedStateHandle())
        val line = MeasurementReading(1, 0.5, true)
        model.onFrame(ArFrameState(0, true, true, listOf(line), scanPhase = SpaceScanPhase.MEASURING))
        model.onFrame(ArFrameState(0, false, false, listOf(line.copy(distanceMeters = null, isTracking = false)),
            scanPhase = SpaceScanPhase.RECOVERING))
        assertEquals(0.5, model.uiState.value.distanceMeters!!, 0f.toDouble())
        assertFalse(model.uiState.value.canPlacePoint)
        assertEquals(SpaceScanPhase.RECOVERING, model.uiState.value.scanPhase)
    }

    @Test fun interruptedPreparationKeepsProgressButCannotStartUntilTrackingRecovers() {
        val model = MeasurementViewModel(SavedStateHandle())
        model.onFrame(ArFrameState(0, false, true, scanProgress = 0.5f))
        model.onFrame(ArFrameState(0, false, false, scanProgress = 0.5f))
        assertEquals(0.5f, model.uiState.value.scanProgress, 0f)
        assertFalse(model.uiState.value.canBeginMeasurement)
        model.onFrame(ArFrameState(0, false, true, scanPhase = SpaceScanPhase.READY,
            scanProgress = 1f, canBeginMeasurement = true))
        assertTrue(model.uiState.value.canBeginMeasurement)
        model.onFrame(ArFrameState(0, false, false, scanPhase = SpaceScanPhase.READY, scanProgress = 1f))
        assertEquals(1f, model.uiState.value.scanProgress, 0f)
        assertFalse(model.uiState.value.canBeginMeasurement)
        model.onFrame(ArFrameState(0, false, true, scanPhase = SpaceScanPhase.READY, scanProgress = 1f))
        assertFalse(model.uiState.value.canBeginMeasurement)
        model.onFrame(ArFrameState(0, false, true, scanPhase = SpaceScanPhase.READY,
            scanProgress = 1f, canBeginMeasurement = true))
        assertTrue(model.uiState.value.canBeginMeasurement)
        assertFalse(model.uiState.value.canPlacePoint)
    }

}
