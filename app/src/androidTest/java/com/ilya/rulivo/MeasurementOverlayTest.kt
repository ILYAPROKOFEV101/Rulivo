package com.ilya.rulivo

import com.ilya.rulivo.R
import com.ilya.rulivo.i18n.text

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.Bitmap
import com.ilya.rulivo.ar.SpaceScanPhase
import com.ilya.rulivo.ar.MeasurementReading
import com.ilya.rulivo.ui.measurement.*
import com.ilya.rulivo.ui.theme.RulivoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File

/** UI contracts only. The coloured background represents the camera viewport, not a real AR session. */
class MeasurementOverlayTest {
    @get:Rule val compose = createComposeRule()
    private val lines = (1L..5L).map { MeasurementReading(it, it * 0.2, true) }

    @Test fun missingSurfaceExplainsDisabledPlacementWithoutRequiringATap() {
        val state = MeasurementUiState(status = MeasurementStatus.WaitingForSurface, isTracking = true,
            hint = text(R.string.aim_at_the_surface_of_the_object), scanPhase = SpaceScanPhase.MEASURING)
        var placements = 0
        compose.setContent {
            RulivoTheme { MeasurementOverlay(state, { placements++ }, {}, {}, {}) }
        }
        compose.onNodeWithContentDescription(text(R.string.crosshair_searching_for_a_surface)).assertIsDisplayed()
        compose.onNodeWithContentDescription(text(R.string.aiming_grid_two_vertical_and_two_horizontal)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.place_first_point)).performScrollTo().assertIsNotEnabled().performClick()
        compose.runOnIdle { assertEquals(0, placements) }
        compose.onNodeWithText(text(R.string.aim_at_the_surface_of_the_object)).performScrollTo()
        compose.onNodeWithText(text(R.string.aim_at_the_surface_of_the_object)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.undo_last)).assertIsNotEnabled()
        compose.onNodeWithText(text(R.string.clear_all)).assertIsNotEnabled()
    }

    @Test fun readySecondPointShowsLivePreviewAndReticle() {
        var placements = 0
        compose.setContent {
            RulivoTheme {
                MeasurementOverlay(MeasurementUiState(status = MeasurementStatus.ReadyForSecondPoint,
                    pointCount = 1, previewDistanceMeters = 0.426, previewIsLive = true,
                    canPlacePoint = true, isTracking = true, scanPhase = SpaceScanPhase.MEASURING), { placements++ }, {}, {}, {})
            }
        }
        compose.onNodeWithContentDescription(text(R.string.crosshair_ready_to_place_a_point)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.preview)).assertIsDisplayed()
        compose.onNodeWithText("42.6 cm").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.place_second_point)).performClick()
        compose.runOnIdle { assertEquals(1, placements) }
    }

    @Test fun fiveSavedLinesAllowNewMeasurementAndUnitSwitching() {
        var state by mutableStateOf(MeasurementUiState(status = MeasurementStatus.ReadyForFirstPoint,
            measurements = lines, canPlacePoint = true, isTracking = true,
            hint = text(R.string.line_saved_start_the_next_measurement), scanPhase = SpaceScanPhase.MEASURING))
        compose.setContent {
            RulivoTheme {
                Box(Modifier.fillMaxSize().background(Color(0xFF52625B))) {
                    MeasurementOverlay(state, {}, {}, { state = state.copy(unit = it) }, {})
                }
            }
        }
        compose.onNodeWithText(text(R.string.lines_30, 5)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.place_first_point)).assertIsEnabled()
        compose.onNodeWithContentDescription(text(R.string.centimeters)).performClick()
        compose.onNodeWithText("100 cm").assertIsDisplayed()
        saveUi("five-lines-controls.png")
    }

    @Test fun undoRemovesLastCompletedLineAndPreservesCurrentStart() {
        var state by mutableStateOf(MeasurementUiState(pointCount = 1, measurements = lines,
            previewDistanceMeters = 0.8, isTracking = true, scanPhase = SpaceScanPhase.MEASURING))
        compose.setContent {
            RulivoTheme { MeasurementOverlay(state, {}, {}, {}, {}, onUndo = { state = state.copy(measurements = state.measurements.dropLast(1)) }) }
        }
        compose.onNodeWithText(text(R.string.undo_last)).performClick()
        compose.onNodeWithText(text(R.string.lines_30, 4)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.place_second_point)).assertIsDisplayed()
        compose.onNodeWithText("80 cm").assertIsDisplayed()
    }

    @Test fun cancelCurrentKeepsCompletedLinesAndClearRemovesAll() {
        var state by mutableStateOf(MeasurementUiState(pointCount = 1, measurements = lines, scanPhase = SpaceScanPhase.MEASURING))
        compose.setContent {
            RulivoTheme { MeasurementOverlay(state, {}, { state = MeasurementUiState(scanPhase = SpaceScanPhase.MEASURING) }, {}, {},
                onCancelCurrent = { state = state.copy(pointCount = 0) }) }
        }
        compose.onNodeWithText(text(R.string.remove_first_point)).performClick()
        compose.onNodeWithText(text(R.string.lines_30, 5)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.place_first_point)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.clear_all)).performClick()
        compose.onNodeWithText(text(R.string.lines_30, 0)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.undo_last)).assertIsNotEnabled()
    }

    @Test fun limitIsVisibleAndPreventsPlacement() {
        compose.setContent {
            RulivoTheme { MeasurementOverlay(MeasurementUiState(measurements = (1L..30L).map { MeasurementReading(it, 1.0, true) }, scanPhase = SpaceScanPhase.MEASURING), {}, {}, {}, {}) }
        }
        compose.onNodeWithText(text(R.string.limit_reached_30_lines)).assertIsNotEnabled()
        compose.onNodeWithText(text(R.string.undo_last)).assertIsEnabled()
    }

    @Test fun scanningShowsPreparationAndPreventsPointPlacement() {
        compose.setContent {
            RulivoTheme {
                Box(Modifier.fillMaxSize().background(Color(0xFF52625B))) {
                    MeasurementOverlay(MeasurementUiState(status = MeasurementStatus.ScanningSpace,
                        scanProgress = 0.6f, hint = "Медленно перемещайте телефон влево-вправо"), {}, {}, {}, {})
                }
            }
        }
        compose.onNodeWithText(text(R.string.message_1_prepare_to_measure)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.searching_for_a_surface)).assertIsNotEnabled()
        compose.onNodeWithText(text(R.string.need_help)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.place_first_point)).assertDoesNotExist()
        saveUi("space-scanning.png")
    }

    @Test fun readyScanStartsMeasurementWithoutPlacingAnAnchor() {
        var state by mutableStateOf(MeasurementUiState(status = MeasurementStatus.ScanningSpace,
            scanPhase = SpaceScanPhase.READY, scanProgress = 1f, canBeginMeasurement = true,
            hint = text(R.string.surfaces_found_you_can_start_measuring)))
        var starts = 0
        compose.setContent {
            RulivoTheme {
                MeasurementOverlay(state, {
                    starts++
                    state = state.copy(scanPhase = SpaceScanPhase.MEASURING, canPlacePoint = true)
                }, {}, {}, {})
            }
        }
        compose.onNodeWithText(text(R.string.surfaces_found)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.start_measurement)).assertIsEnabled().performClick()
        compose.onNodeWithText(text(R.string.place_first_point)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.of_2_points, 0)).assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, starts) }
    }

    @Test fun preparedScanStaysVisibleDuringRecoveryButStartIsDisabled() {
        var state by mutableStateOf(MeasurementUiState(status = MeasurementStatus.ScanningSpace,
            scanPhase = SpaceScanPhase.READY, scanProgress = 1f,
            hint = text(R.string.scan_saved_restoring_tracking)))
        compose.setContent { RulivoTheme { MeasurementOverlay(state, {}, {}, {}, {}) } }
        compose.onNodeWithText(text(R.string.restoring_tracking)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.waiting_for_camera_tracking)).assertIsNotEnabled()
        compose.onNodeWithText("Сканирование…").assertDoesNotExist()
        compose.onNodeWithText(text(R.string.place_first_point)).assertDoesNotExist()
        compose.runOnIdle { state = state.copy(isTracking = true, canBeginMeasurement = true) }
        compose.onNodeWithText(text(R.string.start_measurement)).assertIsEnabled()
    }

    private fun saveUi(name: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val folder = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "qa").apply { mkdirs() }
        File(folder, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
