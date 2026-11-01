package com.ilya.rulivo

import com.ilya.rulivo.R
import com.ilya.rulivo.i18n.text

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
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

class ToolkitScreenTest {
    @get:Rule val compose=createComposeRule()
    private val app get()=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
    private fun model(handle: SavedStateHandle=SavedStateHandle())=ToolkitViewModel(app,handle,MeasurementRepository(File(app.cacheDir,"test-history-${UUID.randomUUID()}.json")))
    private fun show(model: ToolkitViewModel) {
        compose.setContent {RulivoTheme {val state by model.state.collectAsState();ToolkitScreen(state,model,emptyList(),{key,type->model.requestCapture(key,type)},{},{})}}
        compose.waitUntil(5_000){!model.state.value.loading}
    }
    private fun type(label: String,value: String) {
        compose.onNodeWithText(label).performScrollTo().performTextReplacement(value)
        if(label!=text(R.string.measurement_name))compose.onNodeWithText(label).performImeAction()
        compose.waitForIdle()
    }
    private fun click(text: String) {compose.onNodeWithText(text).performScrollTo().performClick()}
    private fun screenshot(name: String) {
        val dir=File(app.getExternalFilesDir(null),"modes-qa").apply{mkdirs()}
        File(dir,name).outputStream().use {compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
    }
    @Test fun boxCanBeEnteredSavedReopenedAndItsSnapshotDoesNotChange() {
        val model=model();show(model)
        click(text(R.string.mode_object));click(text(R.string.enter_dimensions_manually));type(text(R.string.length_m),"0,6");type(text(R.string.width_m),"0,4");type(text(R.string.height_m),"0,35")
        compose.onNodeWithText(text(R.string.approximate_cubic_meters, com.ilya.rulivo.measure.Quantities.number(0.084))).performScrollTo().assertIsDisplayed();screenshot("box-result.png")
        type(text(R.string.measurement_name),"Коробка для переезда");click(text(R.string.save_result))
        compose.waitUntil(5_000){model.state.value.history.size==1}
        type(text(R.string.height_m),"0,7")
        compose.runOnIdle {assertEquals(.084,model.state.value.history.single().result.metrics.getValue("volume"),1e-12)}
        click(text(R.string.open_saved_measurements));click("Коробка для переезда")
        compose.onNodeWithText(text(R.string.approximate_cubic_meters, com.ilya.rulivo.measure.Quantities.number(0.084))).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.input_with_source, text(R.string.width_m), "0,4", ValueSource.MANUAL.title)).assertExists()
    }
    @Test fun roomAreaCanBeSavedWithoutHeightAndMaterialsUseUnroundedArea() {
        val model=model();show(model)
        click(text(R.string.mode_room));click(text(R.string.enter_dimensions_manually));type(text(R.string.length_m),"4,2");type(text(R.string.width_m),"3,6")
        compose.runOnIdle {val r=MeasurementCalculator.calculate(model.state.value.draft).result!!;assertFalse(r.metrics.containsKey("volume"));assertEquals(15.12,r.metrics.getValue("area"),1e-10)}
        type(text(R.string.height_m),"2,7");compose.onNodeWithText(text(R.string.approximate_cubic_meters, com.ilya.rulivo.measure.Quantities.number(40.824))).performScrollTo().assertIsDisplayed();screenshot("room-result.png")
        click(text(R.string.calculate_materials));type(text(R.string.area_per_pack_m),"2,2");type(text(R.string.allowance),"10")
        compose.onNodeWithText(text(R.string.label_value, text(R.string.packs), "8")).performScrollTo().assertIsDisplayed()
        click(text(R.string.back))
        compose.runOnIdle {assertEquals(MeasureKind.ROOM,model.state.value.draft.kind);assertEquals("2,7",model.state.value.draft.input("height").text)}
    }
    @Test fun roomAcceptsArContourAndRejectsLateCaptureFromAnotherRequest() {
        val model=model();show(model)
        compose.runOnIdle {model.start(MeasureKind.ROOM);model.requestCapture("contour",CaptureType.ROOM_FLOOR)}
        val request=model.state.value.capture!!
        val points=listOf(Point2(0.0,0.0),Point2(4.0,0.0),Point2(4.0,2.0),Point2(2.0,2.0),Point2(2.0,4.0),Point2(0.0,4.0))
        compose.runOnIdle {model.acceptCapture(CaptureResult("wrong",points=points));assertTrue(model.state.value.draft.contour.isEmpty());model.acceptCapture(CaptureResult(request.id,points=points))}
        click(text(R.string.enter_dimensions_manually));type(text(R.string.height_m),"2,7")
        compose.onNodeWithText(text(R.string.approximate_cubic_meters, com.ilya.rulivo.measure.Quantities.number(32.4))).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.outline, ValueSource.AR.title)).performScrollTo().assertIsDisplayed();screenshot("room-contour.png")
    }
    @Test fun fitReportsAxisShortageAfterRotation() {
        val model=model();show(model);click(text(R.string.mode_fit));click(text(R.string.enter_dimensions_manually))
        type(text(R.string.width_m),"1");type(text(R.string.depth_m),"0,6");type(text(R.string.height_m),"0,8")
        type(text(R.string.object_width_m),"0,98");type(text(R.string.object_depth_m),"0,55");type(text(R.string.height_of_object_m),"0,75");type(text(R.string.clearance_on_each_side_m),"0,01")
        compose.onNodeWithText(text(R.string.exact_fit)).performScrollTo().assertIsDisplayed()
        click(text(R.string.d_w_h))
        compose.onNodeWithText(text(R.string.does_not_fit)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.label_value, text(R.string.shortfall_depth), "0.4 m")).assertIsDisplayed();screenshot("fit-result.png")
        compose.runOnIdle {
            val inner=MeasureDraft(internal=true).withInput("length","2").withInput("width","1").withInput("height","3")
            val record=SavedMeasurement("inner",0,inner,MeasurementCalculator.calculate(inner).result!!)
            model.useBox(record,asItem=true)
            assertEquals("0,98",model.state.value.draft.input("itemWidth").text)
            model.useBox(record,asItem=false)
            assertEquals("2",model.state.value.draft.input("width").text)
        }
    }
    @Test fun unsavedDraftSurvivesNavigationAndModeSwitchRequiresExplicitDiscard() {
        val handle=SavedStateHandle();val model=model(handle);show(model);click(text(R.string.mode_object));click(text(R.string.enter_dimensions_manually));type(text(R.string.length_m),"0,6");click(text(R.string.back));click(text(R.string.mode_room))
        compose.onNodeWithText(text(R.string.unfinished_measurement)).assertIsDisplayed();compose.onNodeWithText(text(R.string.continue_draft)).performClick()
        compose.runOnIdle {assertEquals(MeasureKind.BOX,model.state.value.draft.kind);assertEquals("0,6",model.state.value.draft.input("length").text)}
        compose.runOnIdle {val restored=model(handle);assertEquals(model.state.value.draft,restored.state.value.draft)}
    }
    @Test fun captureOverlayCannotConfirmPausedOrInvalidGeometry() {
        val req=CaptureRequest("capture",CaptureType.AREA,"contour")
        var commands=0
        compose.setContent {RulivoTheme {CaptureOverlay(MeasurementUiState(scanPhase=SpaceScanPhase.MEASURING,
            capture=CaptureFrame(req.id,count=4,closed=true,tracking=false,hint=text(R.string.restoring_tracking))),req,{}, {commands++},{})}}
        compose.onNodeWithText(text(R.string.use_result)).assertIsNotEnabled()
        compose.runOnIdle {assertEquals(0,commands)}
    }
    @Test fun menuStartsWholeFigureCaptureWithoutDimensionFields() {
        val model=model();show(model);click(text(R.string.mode_object))
        compose.runOnIdle {assertEquals(CaptureType.BOX_VOLUME,model.state.value.capture?.type)}
        compose.onNodeWithText(text(R.string.length_m)).assertDoesNotExist()
        compose.runOnIdle {
            val req=model.state.value.capture!!
            model.acceptCapture(CaptureResult(req.id,dimensions=mapOf("length" to .6,"width" to .4,"height" to .35)))
            assertNull(model.state.value.capture)
            assertTrue(model.state.value.draft.inputs.values.all {it.source==ValueSource.AR})
        }
        compose.onNodeWithText(text(R.string.approximate_cubic_meters, com.ilya.rulivo.measure.Quantities.number(0.084))).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.length_m)).assertDoesNotExist();screenshot("camera-box-result.png")
    }
    @Test fun fullRoomCaptureTransfersFloorAndHeightTogether() {
        val model=model();show(model);click(text(R.string.mode_room))
        val points=listOf(Point2(0.0,0.0),Point2(4.0,0.0),Point2(4.0,3.0),Point2(0.0,3.0))
        compose.runOnIdle {
            val req=model.state.value.capture!!;assertEquals(CaptureType.ROOM_VOLUME,req.type)
            model.acceptCapture(CaptureResult(req.id,points=points,dimensions=mapOf("height" to 2.7,"area" to 12.0,"perimeter" to 14.0)))
            assertEquals(ValueSource.AR,model.state.value.draft.input("height").source)
        }
        compose.onNodeWithText(text(R.string.approximate_cubic_meters, com.ilya.rulivo.measure.Quantities.number(32.4))).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.height_m)).assertDoesNotExist();screenshot("camera-room-result.png")
    }
    @Test fun nicheThenItemUsesTwoWholeCapturesAndRejectsLateFirstResult() {
        val model=model();show(model);click(text(R.string.mode_fit))
        compose.runOnIdle {
            val first=model.state.value.capture!!;assertEquals("fitSpace",first.target)
            model.acceptCapture(CaptureResult(first.id,dimensions=mapOf("length" to 1.0,"width" to .6,"height" to .8)))
            val second=model.state.value.capture!!;assertEquals("fitItem",second.target);assertNotEquals(first.id,second.id)
            model.acceptCapture(CaptureResult(first.id,dimensions=mapOf("length" to 99.0)))
            assertEquals("1",model.state.value.draft.input("width").text)
            model.acceptCapture(CaptureResult(second.id,dimensions=mapOf("length" to .98,"width" to .55,"height" to .75)))
            assertNull(model.state.value.capture)
        }
        compose.onNodeWithText(text(R.string.fits_by_dimensions)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.object_width_m)).assertDoesNotExist()
    }
    @Test fun closedRoomFloorOffersCeilingInsteadOfPrematureConfirmation() {
        val req=CaptureRequest("room",CaptureType.ROOM_VOLUME,"room")
        var action:CaptureAction?=null
        compose.setContent {RulivoTheme {CaptureOverlay(MeasurementUiState(scanPhase=SpaceScanPhase.MEASURING,
            capture=CaptureFrame(req.id,count=4,closed=true,tracking=true,canPlace=true,area=12.0,baseCount=4,
                stage=text(R.string.message_2_2_room_height),placeLabel=text(R.string.mark_ceiling),hint="Отметьте потолок")),req,{}, {action=it.action},{})}}
        compose.onNodeWithText(text(R.string.use_result)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.mark_ceiling)).performClick()
        compose.runOnIdle {assertEquals(CaptureAction.PLACE,action)}
        screenshot("camera-room-height.png")
    }
    @Test fun completedBoxOverlayShowsVolumeAndNoExtraPointButton() {
        val req=CaptureRequest("box",CaptureType.BOX_VOLUME,"figure")
        compose.setContent {RulivoTheme {CaptureOverlay(MeasurementUiState(scanPhase=SpaceScanPhase.MEASURING,
            capture=CaptureFrame(req.id,count=4,tracking=true,canConfirm=true,showPlace=false,volume=.084,
                dimensions=mapOf("length" to .6,"width" to .4,"height" to .35),stage="Предмет · шаг 4 из 4",
                hint="Обойдите фигуру и проверьте каркас")),req,{}, {},{})}}
        compose.onNodeWithText(text(R.string.approximate_cubic_meters, com.ilya.rulivo.measure.Quantities.number(0.084))).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.use_result)).assertIsEnabled()
        compose.onNodeWithText(text(R.string.place_point_282)).assertDoesNotExist();screenshot("camera-box-complete.png")
    }

    @Test fun cameraPanelsLeaveReticleClearWithLargeFontInBothOrientations() {
        val wide=androidx.compose.runtime.mutableStateOf(false)
        val req=CaptureRequest("layout",CaptureType.ROOM_VOLUME,"room")
        compose.setContent {
            val density=androidx.compose.ui.platform.LocalDensity.current
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density,1.6f)) {
                RulivoTheme {
                    Box(Modifier.requiredSize(if(wide.value)640.dp else 380.dp,if(wide.value)360.dp else 720.dp)) {
                        CaptureOverlay(MeasurementUiState(scanPhase=SpaceScanPhase.MEASURING,
                            capture=CaptureFrame(req.id,count=4,tracking=true,canPlace=true,area=12.0,
                                dimensions=mapOf("height" to 2.7),volume=32.4,preview=true,
                                stage=text(R.string.message_2_2_room_height),placeLabel=text(R.string.mark_ceiling),
                                hint=text(R.string.mark_the_ceiling_or_top_of_the))),req,{}, {},{})
                    }
                }
            }
        }
        for(landscape in listOf(false,true)) {
            compose.runOnIdle {wide.value=landscape}
            val reticle=compose.onNodeWithContentDescription(text(R.string.crosshair_ready_to_place_a_point)).fetchSemanticsNode().boundsInRoot
            for(tag in listOf("capture-header","capture-controls")) {
                val bounds=compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
                assertFalse("$tag covers reticle; landscape=$landscape",bounds.overlaps(reticle))
            }
        }
    }

}
