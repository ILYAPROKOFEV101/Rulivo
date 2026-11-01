package com.ilya.rulivo

import android.graphics.Bitmap
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import com.ilya.rulivo.ar.*
import com.ilya.rulivo.i18n.text
import com.ilya.rulivo.ui.components.CameraMotionGuide
import com.ilya.rulivo.ui.measurement.*
import com.ilya.rulivo.ui.theme.RulivoTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class CameraMotionGuideTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun phoneActuallyMovesAndStopsWhenCameraScreenIsHidden() {
        var enabled by mutableStateOf(true)
        compose.mainClock.autoAdvance = false
        compose.setContent { RulivoTheme {
            CameraMotionGuide(CameraMotionCue.SWEEP, Modifier.width(280.dp), animationsEnabled = enabled)
        } }
        compose.mainClock.advanceTimeBy(32)
        val guide = compose.onNodeWithTag("camera-motion-SWEEP")
        val first = guide.captureToImage().asAndroidBitmap()
        compose.mainClock.advanceTimeBy(900)
        val moved = guide.captureToImage().asAndroidBitmap()
        assertFalse("The tutorial must visibly move the phone, not just describe movement", first.sameAs(moved))
        save(first, "sweep-left.png")
        save(moved, "sweep-middle.png")
        compose.runOnIdle { enabled = false }
        compose.mainClock.advanceTimeBy(32)
        val paused = guide.captureToImage().asAndroidBitmap()
        compose.mainClock.advanceTimeBy(1200)
        assertTrue("Hidden camera screens must not keep animating", paused.sameAs(guide.captureToImage().asAndroidBitmap()))
    }

    @Test fun scanSwitchesFromSweepToPauseAndReadyThenRemovesTutorial() {
        var state by mutableStateOf(MeasurementUiState(isTracking = true, scanHasSurface = true, scanProgress = .3f))
        compose.setContent { RulivoTheme { Box(Modifier.fillMaxSize().background(Color(0xFF52625B))) {
            MeasurementOverlay(state, {}, {}, {}, {})
        } } }
        compose.onNodeWithTag("camera-motion-SWEEP").assertIsDisplayed()
        save(compose.onRoot().captureToImage().asAndroidBitmap(), "scan-sweep.png")
        compose.runOnIdle { state = state.copy(trackingIssue = TrackingIssue.TOO_MUCH_MOTION, isTracking = false) }
        compose.onNodeWithTag("camera-motion-SWEEP").assertDoesNotExist()
        compose.onNodeWithTag("camera-motion-STEADY").assertIsDisplayed()
        save(compose.onRoot().captureToImage().asAndroidBitmap(), "scan-steady.png")
        compose.runOnIdle { state = state.copy(trackingIssue = null, isTracking = true,
            scanPhase = SpaceScanPhase.READY, canBeginMeasurement = true, scanProgress = 1f) }
        compose.onNodeWithTag("camera-motion-READY").assertIsDisplayed()
        compose.runOnIdle { state = state.copy(scanPhase = SpaceScanPhase.MEASURING, canPlacePoint = true, hasTarget = true) }
        compose.onNodeWithTag("camera-motion-READY").assertDoesNotExist()
        compose.onAllNodes(hasTestTag("camera-motion-SWEEP") or hasTestTag("camera-motion-AIM")).assertCountEquals(0)
    }

    @Test fun heightGestureYieldsToLightRecoveryAndReturnsAfterTrackingRecovers() {
        val request = CaptureRequest("motion-height", CaptureType.BOX_VOLUME, "box")
        val frame = CaptureFrame(request.id, count = 3, tracking = true, heightAvailable = true,
            cameraHeight = true, cameraHeightReady = true, signedHeight = 1.2, showPlace = false)
        var state by mutableStateOf(MeasurementUiState(isTracking = true, scanPhase = SpaceScanPhase.MEASURING, capture = frame))
        compose.setContent { RulivoTheme { Box(Modifier.fillMaxSize().background(Color(0xFF52625B))) {
            CaptureOverlay(state, request, {}, {}, {})
        } } }
        compose.onNodeWithTag("camera-motion-HEIGHT").assertIsDisplayed()
        save(compose.onRoot().captureToImage().asAndroidBitmap(), "height-motion.png")
        compose.runOnIdle { state = state.copy(isTracking = false, scanPhase = SpaceScanPhase.RECOVERING,
            trackingIssue = TrackingIssue.LOW_LIGHT, capture = frame.copy(tracking = false, cameraHeightReady = false)) }
        compose.onNodeWithTag("camera-motion-HEIGHT").assertDoesNotExist()
        compose.onNodeWithTag("camera-motion-LIGHT").assertIsDisplayed()
        compose.runOnIdle { state = state.copy(isTracking = true, scanPhase = SpaceScanPhase.MEASURING, trackingIssue = null, capture = frame) }
        compose.onNodeWithTag("camera-motion-HEIGHT").assertIsDisplayed()
    }

    @Test fun animatedPreparationFitsBothOrientationsAndLargeText() {
        var figure by mutableStateOf(false)
        var ready by mutableStateOf(false)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                val state = MeasurementUiState(isTracking = true, scanHasSurface = true,
                    scanProgress = if (ready) 1f else .3f, canBeginMeasurement = ready,
                    scanPhase = if (ready) SpaceScanPhase.READY else SpaceScanPhase.SCANNING)
                RulivoTheme { Box(Modifier.fillMaxSize().background(Color(0xFF52625B))) {
                    if (figure) CaptureOverlay(state, CaptureRequest("layout", CaptureType.ROOM_VOLUME, "room"), {}, {}, {})
                    else MeasurementOverlay(state, {}, {}, {}, {})
                } }
            }
        }
        try {
            for (landscape in listOf(false, true)) {
                compose.runOnIdle { compose.activity.requestedOrientation = if (landscape) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
                compose.waitUntil(5000) { compose.activity.resources.configuration.orientation == if (landscape) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT }
                for (capture in listOf(false, true)) {
                    compose.runOnIdle { figure = capture; ready = false }
                    val demo = compose.onNodeWithTag("camera-motion-SWEEP").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
                    val reticle = compose.onNodeWithContentDescription(text(R.string.crosshair_searching_for_a_surface)).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
                    assertFalse("The animation must not cover the real camera aim", demo.overlaps(reticle))
                    save(compose.onRoot().captureToImage().asAndroidBitmap(), "large-${capture}-${landscape}.png")
                    compose.runOnIdle { ready = true }
                    compose.onNodeWithText(text(R.string.start_measurement)).performScrollTo().assertIsDisplayed().assertIsEnabled()
                }
            }
        } finally { compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
    }

    private fun save(bitmap: Bitmap, name: String) {
        val folder = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "camera-motion-qa").apply { mkdirs() }
        File(folder, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
