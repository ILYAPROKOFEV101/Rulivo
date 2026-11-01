package com.ilya.rulivo

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
import com.ilya.rulivo.ar.*
import com.ilya.rulivo.i18n.text
import com.ilya.rulivo.ui.measurement.*
import com.ilya.rulivo.ui.theme.RulivoTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class ContourOverlayTest {
    @get:Rule val compose = createComposeRule()

    @Test fun continueAddCornersCloseUndoAndClearThroughActualSceneCommands() {
        val scene = MeasurementScene<Point3D> {}
        val points = listOf(Point3D(-.405,-1.0,-2.0), Point3D(.405,-1.0,-2.0),
            Point3D(.405,1.0,-2.0), Point3D(-.405,1.0,-2.0))
        var next = 0
        fun place() { scene.place(scene.revision,true) { points[next++] } }
        place();place()
        fun snapshot(): MeasurementUiState {
            val readings = scene.measurements.map { MeasurementReading(it.id,DistanceCalculator.distanceMeters(it.startAnchor,it.endAnchor),true) }
            return MeasurementUiState(scanPhase=SpaceScanPhase.MEASURING, isTracking=true, hasTarget=true,
                canPlacePoint=scene.canAddSegment, measurements=readings, pointCount=if(scene.currentStart==null) 0 else 1,
                previewDistanceMeters=.8, previewIsLive=true, sceneRevision=scene.revision,
                isContinuing=scene.isContinuing, cornerCount=scene.activeCorners.size,canContinue=scene.canContinue,
                canCloseContour=scene.canClose,segmentLimitReached=!scene.canAddSegment,
                contours=scene.contours.map { c -> val r=ContourGeometry.evaluate(c.anchors)
                    ContourReading(c.id,scene.measurements.last { it.pathId==c.id }.id,r.area,r.perimeter,true) })
        }
        var state by mutableStateOf(snapshot())
        compose.setContent { RulivoTheme {
            Box(Modifier.fillMaxSize().background(Color(0xFF52625B))) {
                MeasurementOverlay(state, { place();state=snapshot() }, { scene.clear();state=snapshot() },
                    { state=state.copy(unit=it) }, {},
                    onContinue={ scene.continueLast(state.sceneRevision);state=snapshot() },
                    onCloseContour={ scene.closeContour(state.sceneRevision);state=snapshot() },
                    onUndo={ scene.undoLast(state.sceneRevision);state=snapshot() },
                    onCancelCurrent={ scene.cancelCurrent(state.sceneRevision);state=snapshot() },animationsEnabled=false)
            }
        } }
        compose.onNodeWithText(text(R.string.continue_line)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.contour_close)).performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText(text(R.string.contour_add_corner)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.contour_close)).performScrollTo().assertIsEnabled()
        compose.onNodeWithText(text(R.string.contour_add_corner)).performScrollTo().performClick()
        save("contour-controls-open.png")
        compose.onNodeWithText(text(R.string.contour_close)).performScrollTo().performClick()
        compose.onNodeWithText("1.62 m²").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1,scene.contours.size);assertEquals(4,scene.measurements.size) }
        compose.onNodeWithContentDescription(text(R.string.centimeters)).performScrollTo().performClick()
        compose.onNodeWithText("16200 cm²").assertIsDisplayed()
        save("contour-controls-closed.png")
        compose.onNodeWithText(text(R.string.undo_last)).performScrollTo().performClick()
        compose.runOnIdle { assertTrue(scene.contours.isEmpty());assertEquals(4,scene.activeCorners.size) }
        compose.onNodeWithText(text(R.string.contour_close)).performScrollTo().assertIsEnabled()
        compose.onNodeWithText(text(R.string.contour_close)).performClick()
        compose.onNodeWithText(text(R.string.clear_all)).performScrollTo().performClick()
        compose.runOnIdle { assertTrue(scene.isEmpty) }
        compose.onNodeWithText(text(R.string.lines_30,0)).assertIsDisplayed()
    }

    @Test fun closureWorksWithoutSurfaceHitAndIsDisabledDuringTrackingLoss() {
        var closed=0
        var state by mutableStateOf(MeasurementUiState(scanPhase=SpaceScanPhase.MEASURING,
            pointCount=1,isContinuing=true,cornerCount=4,isTracking=true,canPlacePoint=false,
            canCloseContour=true,hint=text(R.string.aim_at_the_surface_of_the_object)))
        compose.setContent { RulivoTheme { MeasurementOverlay(state, {}, {}, {}, {},
            onCloseContour={ closed++ },animationsEnabled=false) } }
        compose.onNodeWithText(text(R.string.contour_add_corner)).performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText(text(R.string.contour_close)).performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1,closed);state=state.copy(isTracking=false,canCloseContour=false) }
        compose.onNodeWithText(text(R.string.contour_close)).performScrollTo().assertIsNotEnabled()
    }

    @Test fun invalidContourKeepsCornersAndExplainsWhyItCannotClose() {
        val error=text(R.string.contour_same_plane)
        compose.setContent { RulivoTheme { MeasurementOverlay(MeasurementUiState(
            scanPhase=SpaceScanPhase.MEASURING, pointCount=1,isContinuing=true,cornerCount=4,
            isTracking=true,canPlacePoint=true,contourError=error), {}, {}, {}, {},animationsEnabled=false) } }
        compose.onNodeWithText(error).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.contour_close)).performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText(text(R.string.contour_finish_open)).assertIsEnabled()
    }

    private fun save(name: String) {
        val bitmap=compose.onRoot().captureToImage().asAndroidBitmap()
        val folder=File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),"qa").apply { mkdirs() }
        File(folder,name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
    }
}
