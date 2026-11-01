package com.ilya.rulivo

import com.ilya.rulivo.R
import com.ilya.rulivo.i18n.text

import android.graphics.Bitmap
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import com.ilya.rulivo.ar.*
import com.ilya.rulivo.ui.measurement.*
import com.ilya.rulivo.ui.theme.RulivoTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** UI fixtures verify recovery instructions; they do not simulate physical AR tracking. */
class CameraGuidanceUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val request = CaptureRequest("guidance", CaptureType.BOX_VOLUME, "box")

    @Test fun captureExplainsLightFailureAndReturnsToTheSameHeight() {
        val frame = CaptureFrame(request.id, count = 3, heightAvailable = true, showPlace = false,
            signedHeight = 1.2, volume = 2.4, tracking = false, hint = "Вернитесь к точкам")
        var state by mutableStateOf(MeasurementUiState(scanPhase = SpaceScanPhase.RECOVERING,
            trackingIssue = TrackingIssue.LOW_LIGHT, capture = frame))
        compose.setContent { RulivoTheme { CaptureOverlay(state, request, {}, {}, {}) } }
        compose.onNodeWithText(text(R.string.not_enough_light)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.add_light_and_point_the_camera_at)).assertIsDisplayed()
        screenshot("capture-recovery.png")
        compose.onNodeWithTag("capture-height-input").performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { state = state.copy(scanPhase = SpaceScanPhase.MEASURING,
            trackingIssue = null, isTracking = true, capture = frame.copy(tracking = true, canConfirm = true)) }
        compose.onNodeWithText(text(R.string.not_enough_light)).assertDoesNotExist()
        compose.onNodeWithTag("capture-height-input").performScrollTo().assertIsEnabled().assertTextContains(com.ilya.rulivo.measure.Quantities.editable(1.2))
    }

    @Test fun scanHelpCanBeOpenedWithoutLosingProgressOrStartingMeasurement() {
        var starts = 0
        compose.setContent {
            RulivoTheme { Box(Modifier.fillMaxSize().background(Color(0xFF52625B))) {
                CaptureOverlay(MeasurementUiState(scanProgress = .45f, isTracking = true,
                    scanHasSurface = true), request, { starts++ }, {}, {})
            } }
        }
        compose.onNodeWithText(text(R.string.surface_recognized)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.preparing_the_surface)).assertIsNotEnabled()
        screenshot("capture-scanning.png")
        compose.onNodeWithText(text(R.string.need_help)).performClick()
        compose.onNodeWithText(text(R.string.help_with_scanning)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.got_it)).performClick()
        compose.onNodeWithText(text(R.string.surface_recognized)).assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, starts) }
    }

    @Test fun preparationInstructionsAndStartRemainReachableWithLargeText() {
        var state by mutableStateOf(MeasurementUiState(isTracking = true))
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                RulivoTheme { Box(Modifier.fillMaxSize().background(Color(0xFF52625B))) {
                    MeasurementOverlay(state, {}, {}, {}, {})
                } }
            }
        }
        compose.onNodeWithText(text(R.string.point_the_camera_at_the_floor_or))
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.need_help)).performScrollTo().assertIsDisplayed()
        compose.runOnIdle { state = state.copy(scanPhase = SpaceScanPhase.READY, scanProgress = 1f, canBeginMeasurement = true) }
        compose.onNodeWithText(text(R.string.start_measurement)).performScrollTo().assertIsEnabled()
        assertReticleIsClear("measurement-controls")
        screenshot("scan-large-text.png")
    }

    @Test fun landscapePreparationKeepsAimAndActionsAccessibleInBothModes() {
        var figure by mutableStateOf(false)
        val state = MeasurementUiState(isTracking = true, scanPhase = SpaceScanPhase.READY,
            canBeginMeasurement = true, scanProgress = 1f)
        compose.setContent { RulivoTheme { Box(Modifier.fillMaxSize().background(Color(0xFF52625B))) {
            if (figure) CaptureOverlay(state, request, {}, {}, {})
            else MeasurementOverlay(state, {}, {}, {}, {})
        } } }
        try {
            compose.runOnIdle { compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            compose.waitUntil(5000) { compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
            for (capture in listOf(false, true)) {
                compose.runOnIdle { figure = capture }
                compose.onNodeWithText(text(R.string.start_measurement)).performScrollTo().assertIsDisplayed().assertIsEnabled()
                compose.onNodeWithText(text(R.string.need_help)).performScrollTo().assertIsDisplayed()
                assertReticleIsClear(if (capture) "capture-controls" else "measurement-controls")
                screenshot(if (capture) "capture-landscape.png" else "ruler-landscape.png")
            }
        } finally { compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
    }

    private fun assertReticleIsClear(panelTag: String) {
        val reticle = compose.onNodeWithContentDescription(text(R.string.crosshair_searching_for_a_surface)).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val panel = compose.onNodeWithTag(panelTag).fetchSemanticsNode().boundsInRoot
        assertFalse("Controls must leave the camera's central aim visible", panel.overlaps(reticle))
    }

    private fun screenshot(name: String) {
        val folder = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "scan-guidance-qa").apply { mkdirs() }
        File(folder, name).outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
