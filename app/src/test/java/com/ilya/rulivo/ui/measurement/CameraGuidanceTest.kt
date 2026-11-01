package com.ilya.rulivo.ui.measurement

import com.ilya.rulivo.R
import com.ilya.rulivo.i18n.text

import com.ilya.rulivo.ar.*
import org.junit.Assert.*
import org.junit.Test

class CameraGuidanceTest : com.ilya.rulivo.i18n.LocalizedTest() {
    @Test fun placementFailureRemainsVisibleAndNeverHidesCameraRecovery() {
        val state = MeasurementUiState(scanPhase = SpaceScanPhase.MEASURING, isTracking = true,
            canPlacePoint = true, notice = "Недостаточно ресурсов AR")
        assertEquals(state.notice, state.cameraGuidance().detail)
        assertEquals(text(R.string.not_enough_light), state.copy(trackingIssue = TrackingIssue.LOW_LIGHT).cameraGuidance().title)
    }

    @Test fun preparedButInterruptedScanNeverClaimsUserCanStart() {
        val state = MeasurementUiState(scanPhase = SpaceScanPhase.READY, scanProgress = 1f)
        assertEquals(text(R.string.restoring_tracking), state.cameraGuidance().title)
        assertTrue(state.cameraGuidance().detail!!.contains("saved"))
        assertEquals(text(R.string.waiting_for_camera_tracking), state.scanActionLabel)
        val restored = state.copy(isTracking = true, canBeginMeasurement = true)
        assertEquals(text(R.string.surfaces_found), restored.cameraGuidance().title)
        assertEquals(text(R.string.start_measurement), restored.scanActionLabel)
    }

    @Test fun searchingObservingAndLookingAwayHaveDifferentActions() {
        val state = MeasurementUiState(isTracking = true)
        assertEquals(text(R.string.find_a_surface), state.cameraGuidance().title)
        assertEquals(text(R.string.surface_recognized), state.copy(scanHasSurface = true, scanProgress = .3f).cameraGuidance().title)
        val away = state.copy(scanProgress = .3f).cameraGuidance()
        assertEquals(text(R.string.bring_the_surface_back_into_frame), away.title)
        assertTrue(away.detail!!.contains("saved"))
    }

    @Test fun cameraCauseTakesPriorityOverCaptureAndHeightInstructions() {
        val frame = CaptureFrame("box", count = 3, heightAvailable = true, hint = "Задайте высоту ниже")
        for (issue in TrackingIssue.entries) {
            val state = MeasurementUiState(scanPhase = SpaceScanPhase.RECOVERING, trackingIssue = issue)
            val guidance = state.cameraGuidance(frame)
            assertNotEquals(frame.hint, guidance.instruction)
            assertTrue(guidance.detail!!.contains("Continue"))
        }
        assertEquals(text(R.string.not_enough_light), MeasurementUiState(trackingIssue = TrackingIssue.LOW_LIGHT).cameraGuidance(frame).title)
    }

    @Test fun aMissingSurfaceDoesNotSendUserBackToPreparation() {
        val state = MeasurementUiState(scanPhase = SpaceScanPhase.MEASURING, isTracking = true, pointCount = 1)
        assertEquals(text(R.string.find_a_surface_under_the_crosshair), state.cameraGuidance().title)
        assertTrue(state.cameraGuidance().detail!!.contains("First point saved"))
        assertEquals(text(R.string.message_2_2_end_of_line), state.copy(canPlacePoint = true).cameraGuidance().title)
    }

    @Test fun permanentlyLostCapturePointKeepsItsRepairInstruction() {
        val frame = CaptureFrame("room", count = 3, hint = "Точка 2 потеряна. Выберите её для повторного измерения")
        assertEquals(frame.hint, MeasurementUiState(scanPhase = SpaceScanPhase.MEASURING, isTracking = true).cameraGuidance(frame).instruction)
    }
}
