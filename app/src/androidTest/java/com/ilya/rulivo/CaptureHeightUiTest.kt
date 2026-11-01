package com.ilya.rulivo

import com.ilya.rulivo.R
import com.ilya.rulivo.i18n.text

import android.app.Application
import android.graphics.Bitmap
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.lifecycle.SavedStateHandle
import androidx.test.platform.app.InstrumentationRegistry
import com.ilya.rulivo.ar.*
import com.ilya.rulivo.measure.*
import com.ilya.rulivo.ui.measurement.*
import com.ilya.rulivo.ui.theme.RulivoTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

/** Camera hits are fixtures; UI commands use the production draft, geometry and result path. */
class CaptureHeightUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val app get()=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
    private lateinit var draft: CaptureDraft<Point3D>
    private lateinit var model: ToolkitViewModel
    private val tracked=mutableStateOf(true)
    private val font=mutableFloatStateOf(1f)
    private val changed=mutableIntStateOf(0)
    private val aimedHeight=mutableDoubleStateOf(0.0)

    private fun show(kind: MeasureKind) {
        model=ToolkitViewModel(app,SavedStateHandle(),MeasurementRepository(File(app.cacheDir,"height-${UUID.randomUUID()}.json")))
        val points=when(kind) {
            MeasureKind.CYLINDER -> listOf(Point3D(0.0,0.0,0.0),Point3D(.5,0.0,0.0))
            MeasureKind.BOX -> listOf(Point3D(0.0,0.0,0.0),Point3D(2.0,0.0,0.0),Point3D(1.0,0.0,3.0))
            else -> listOf(Point3D(0.0,0.0,0.0),Point3D(4.0,0.0,0.0),Point3D(4.0,0.0,3.0),Point3D(0.0,0.0,3.0))
        }
        compose.setContent {
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,font.floatValue)) {
                RulivoTheme {
                    val state by model.state.collectAsState()
                    val req=state.capture
                    if(req==null)ToolkitScreen(state,model,emptyList(),{key,type->model.requestCapture(key,type)},{},{})
                    else {
                        val d=remember(req.id){CaptureDraft<Point3D>(req){}.also {draft=it}}
                        changed.intValue
                        val dy=aimedHeight.doubleValue-1.5
                        val length=kotlin.math.sqrt(9+dy*dy)
                        d.updateCameraHeight(if(tracked.value)CameraRay(Point3D(0.0,1.5,3.0),Point3D(0.0,dy/length,-3/length))else null,Point3D(0.0,0.0,0.0))
                        val geometry=d.evaluate(d.anchors)
                        val frame=CaptureFrame(req.id,d.revision,d.anchors.size,d.closed,
                            canPlace=tracked.value&&d.acceptsPoint,tracking=tracked.value,
                            canConfirm=tracked.value&&!d.cameraHeight&&geometry.complete&&(!req.isContour||d.closed),
                            area=geometry.area,volume=geometry.volume,dimensions=geometry.dimensions,
                            points=geometry.outline,canClose=req.isContour&&geometry.baseValid&&!d.closed,
                            showPlace=d.acceptsPoint,baseCount=d.baseCount,
                            placeLabel=capturePlaceLabel(req.type,d.anchors.size,d.closed),
                            heightAvailable=d.heightAvailable,manualHeight=d.manualHeight,
                            cameraHeight=d.cameraHeight&&d.heightAvailable,cameraHeightReady=d.cameraHeightReady,heightSource=d.heightSource,
                            signedHeight=if(d.manualHeight)d.signedHeight else d.heightBaseCount?.let {d.anchors.getOrNull(it)?.y})
                        Box(Modifier.fillMaxSize()) {
                            CaptureOverlay(MeasurementUiState(scanPhase=SpaceScanPhase.MEASURING,capture=frame),req,{}, {c->
                                when(c.action) {
                                    CaptureAction.PLACE -> d.place(c,frame.canPlace){points.getOrNull(d.anchors.size) ?: points.first().copy(y=1.35)}
                                    CaptureAction.CLOSE -> d.close(c,frame.canClose)
                                    CaptureAction.SET_HEIGHT -> d.setHeight(c)
                                    CaptureAction.UNDO -> d.undo(c)
                                    CaptureAction.CLEAR -> d.clear()
                                    CaptureAction.CAMERA_HEIGHT -> d.measureHeight(c)
                                    CaptureAction.AIM_HEIGHT -> d.aimHeight(c)
                                    CaptureAction.FREEZE_HEIGHT -> d.freezeHeight(c)
                                    CaptureAction.REPLACE -> d.select(c)
                                    CaptureAction.CONFIRM -> if(frame.canConfirm)model.acceptCapture(d.result(geometry))
                                }
                                changed.intValue++
                            },model::cancelCapture)
                        }
                    }
                }
            }
        }
        compose.waitUntil(5000){!model.state.value.loading}
        compose.onNodeWithText(kind.title).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.height_from_base)).assertDoesNotExist()
        for(i in points.indices)compose.onNodeWithText(capturePlaceLabel(draft.request.type,i,false)).performScrollTo().performClick()
        if(kind==MeasureKind.ROOM)compose.onNodeWithText(text(R.string.close_floor_outline)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.height_from_base)).assertIsDisplayed()
        compose.onNodeWithTag("capture-height-input").assertIsDisplayed()
    }
    private fun screenshot(name: String) {
        val folder=File(app.getExternalFilesDir(null),"height-qa").apply {mkdirs()}
        File(folder,name).outputStream().use {compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
    }
    private fun exercise(kind: MeasureKind) {
        show(kind)
        val base=draft.anchors.toList()
        compose.onNodeWithText(text(R.string.lock_height)).assertIsNotEnabled()
        compose.onNodeWithContentDescription(text(R.string.raise_the_top_by_5_cm)).performClick()
        compose.runOnIdle {assertEquals(.05,draft.evaluate(draft.anchors).dimensions.getValue("height"),0.0)}
        compose.onNodeWithContentDescription(text(R.string.raise_the_top_by_5_cm)).performClick()
        compose.onNodeWithContentDescription(text(R.string.lower_the_top_by_5_cm)).performClick()
        compose.onNodeWithTag("capture-height-input").assertTextContains(com.ilya.rulivo.measure.Quantities.editable(0.05))
        compose.onNodeWithTag("capture-height-input").performTextReplacement("2,735")
        compose.runOnIdle {
            val figure=draft.evaluate(draft.anchors)
            assertEquals(2.735,figure.edges.maxOf {maxOf(it.start.y,it.end.y)},0.0)
            assertEquals(figure.area!!*2.735,figure.volume!!,1e-9)
            assertEquals(base,draft.anchors)
        }
        compose.onNodeWithTag("capture-height-input").performImeAction()
        screenshot("${kind.name.lowercase()}-height.png")
        compose.runOnIdle {tracked.value=false}
        compose.onNodeWithTag("capture-height-input").assertIsNotEnabled()
        compose.onNodeWithText(text(R.string.use_result)).assertIsNotEnabled()
        compose.runOnIdle {tracked.value=true}
        compose.onNodeWithTag("capture-height-input").assertTextContains(com.ilya.rulivo.measure.Quantities.editable(2.735))
        compose.onNodeWithTag("capture-height-input").performTextReplacement("0")
        compose.onNodeWithText(text(R.string.use_result)).assertIsNotEnabled()
        compose.runOnIdle {assertFalse(draft.evaluate(draft.anchors).complete)}
        compose.onNodeWithTag("capture-height-input").performTextReplacement("1.25")
        compose.onNodeWithTag("capture-height-input").performImeAction()
        compose.onNodeWithText(text(R.string.use_result)).performScrollTo().performClick()
        compose.runOnIdle {
            assertNull(model.state.value.capture)
            assertEquals(1.25,model.state.value.draft.value("height")!!,0.0)
            assertEquals(ValueSource.MANUAL,model.state.value.draft.input("height").source)
            assertEquals(ValueSource.AR,model.state.value.draft.input(if(kind==MeasureKind.CYLINDER)"diameter" else if(kind==MeasureKind.BOX)"length" else "area").source)
        }
    }
    @Test fun cylinderBaseToHeightButtonsExactInputPauseAndResult()=exercise(MeasureKind.CYLINDER)
    @Test fun roomFloorToHeightButtonsExactInputPauseAndResult()=exercise(MeasureKind.ROOM)
    @Test fun boxBaseToHeightButtonsExactInputPauseAndResult()=exercise(MeasureKind.BOX)

    private fun cameraExercise(kind: MeasureKind) {
        show(kind)
        val base=draft.anchors.toList()
        compose.runOnIdle {aimedHeight.doubleValue=1.7}
        compose.onNodeWithTag("capture-height-input").assertTextContains(com.ilya.rulivo.measure.Quantities.editable(1.7))
        compose.runOnIdle {
            assertEquals(1.7,draft.evaluate(draft.anchors).edges.maxOf {maxOf(it.start.y,it.end.y)},0.0)
            assertEquals(base,draft.anchors)
        }
        screenshot("${kind.name.lowercase()}-camera-moving.png")
        compose.onNodeWithText(text(R.string.lock_height)).performScrollTo().performClick()
        compose.runOnIdle {aimedHeight.doubleValue=3.0}
        compose.onNodeWithTag("capture-height-input").performScrollTo().assertTextContains(com.ilya.rulivo.measure.Quantities.editable(1.7))
        compose.onNodeWithText(text(R.string.move_the_top_with_the_camera)).performScrollTo().performClick()
        compose.runOnIdle {assertEquals(1.7,draft.signedHeight!!,0.0);aimedHeight.doubleValue=3.25}
        compose.onNodeWithTag("capture-height-input").performScrollTo().assertTextContains(com.ilya.rulivo.measure.Quantities.editable(1.95))
        compose.runOnIdle {tracked.value=false}
        compose.onNodeWithText(text(R.string.lock_height)).assertIsNotEnabled()
        compose.runOnIdle {aimedHeight.doubleValue=5.0;tracked.value=true}
        compose.onNodeWithTag("capture-height-input").assertTextContains(com.ilya.rulivo.measure.Quantities.editable(1.95))
        compose.onNodeWithText(text(R.string.lock_height)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.use_result)).performScrollTo().performClick()
        compose.runOnIdle {
            assertNull(model.state.value.capture)
            assertEquals(1.95,model.state.value.draft.value("height")!!,0.0)
            assertEquals(ValueSource.CAMERA_AIM,model.state.value.draft.input("height").source)
        }
    }
    @Test fun cylinderCameraMovesTopFreezesAndResumesWithoutJump()=cameraExercise(MeasureKind.CYLINDER)
    @Test fun roomCameraMovesTopFreezesAndResumesWithoutJump()=cameraExercise(MeasureKind.ROOM)
    @Test fun boxCameraMovesTopFreezesAndResumesWithoutJump()=cameraExercise(MeasureKind.BOX)

    @Test fun belowBaseUpButtonMovesUpAndCrossesBaseWithoutReversing() {
        show(MeasureKind.CYLINDER)
        compose.onNodeWithTag("capture-height-input").performTextReplacement("-1,7")
        compose.onNodeWithTag("capture-height-input").performImeAction()
        compose.onNodeWithContentDescription(text(R.string.raise_the_top_by_5_cm)).performScrollTo().performClick()
        compose.onNodeWithTag("capture-height-input").assertTextContains(com.ilya.rulivo.measure.Quantities.editable(-1.65))
        compose.runOnIdle {assertEquals(-1.65,draft.evaluate(draft.anchors).edges.minOf {minOf(it.start.y,it.end.y)},0.0)}
        compose.onNodeWithContentDescription(text(R.string.lower_the_top_by_5_cm)).performClick()
        compose.onNodeWithTag("capture-height-input").assertTextContains(com.ilya.rulivo.measure.Quantities.editable(-1.7))
        compose.onNodeWithTag("capture-height-input").performTextReplacement("-0,05")
        compose.onNodeWithTag("capture-height-input").performImeAction()
        compose.onNodeWithContentDescription(text(R.string.raise_the_top_by_5_cm)).performClick()
        compose.onNodeWithContentDescription(text(R.string.raise_the_top_by_5_cm)).performClick()
        compose.onNodeWithTag("capture-height-input").assertTextContains(com.ilya.rulivo.measure.Quantities.editable(0.05))
        screenshot("cylinder-negative-height-corrected.png")
    }

    @Test fun heightRemainsReachableAtLargeFontAndInLandscape() {
        show(MeasureKind.CYLINDER)
        compose.runOnIdle {font.floatValue=1.6f}
        try {for(landscape in listOf(false,true)) {
            compose.runOnIdle {compose.activity.requestedOrientation=if(landscape)ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT}
            compose.waitUntil(5000) {compose.activity.resources.configuration.orientation==if(landscape)Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT}
            compose.onNodeWithTag("capture-height-input").performScrollTo().assertIsDisplayed()
            compose.onNodeWithContentDescription(text(R.string.raise_the_top_by_5_cm)).performScrollTo().performClick()
            compose.onNodeWithTag("capture-height-input").performTextReplacement("0,75")
            compose.onNodeWithTag("capture-height-input").performImeAction()
            compose.runOnIdle {assertEquals(.75,draft.signedHeight!!,0.0)}
            screenshot("height-large-font-${if(landscape)"landscape"else "portrait"}.png")
            compose.onNodeWithText(text(R.string.use_result)).performScrollTo().assertIsDisplayed().assertIsEnabled()
        }}finally {compose.activity.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_PORTRAIT}
    }
}
