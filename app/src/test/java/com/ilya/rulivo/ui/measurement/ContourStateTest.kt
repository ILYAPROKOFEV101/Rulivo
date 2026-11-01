package com.ilya.rulivo.ui.measurement

import androidx.lifecycle.SavedStateHandle
import com.ilya.rulivo.ar.ArFrameState
import com.ilya.rulivo.ar.ContourReading
import com.ilya.rulivo.ar.MeasurementReading
import com.ilya.rulivo.ar.SpaceScanPhase
import com.ilya.rulivo.i18n.LocalizedTest
import org.junit.Assert.*
import org.junit.Test

class ContourStateTest : LocalizedTest() {
    @Test fun pausedContourKeepsValuesAndDeletionDoesNotResurrectIt() {
        val model=MeasurementViewModel(SavedStateHandle())
        val frame=ArFrameState(0,true,true,measurements=listOf(MeasurementReading(4,.81,true)),
            contours=listOf(ContourReading(1,4,1.62,5.62,true)),scanPhase=SpaceScanPhase.MEASURING)
        model.onFrame(frame)
        assertEquals(1.62,model.uiState.value.lastContour!!.areaSquareMeters!!,0.0)
        model.onFrame(frame.copy(canPlacePoint=false,isTracking=false,
            contours=listOf(ContourReading(1,4,null,null,false))))
        assertEquals(1.62,model.uiState.value.lastContour!!.areaSquareMeters!!,0.0)
        assertFalse(model.uiState.value.lastContour!!.isTracking)
        model.onFrame(frame.copy(contours=emptyList()))
        assertNull(model.uiState.value.lastContour)
    }

    @Test fun closingDoesNotRequireAHitButDoesRequireCameraTracking() {
        val model=MeasurementViewModel(SavedStateHandle())
        val frame=ArFrameState(1,false,true,isContinuing=true,cornerCount=4,canCloseContour=true,
            scanPhase=SpaceScanPhase.MEASURING)
        model.onFrame(frame)
        assertFalse(model.uiState.value.canPlacePoint);assertTrue(model.uiState.value.canCloseContour)
        model.onFrame(frame.copy(isTracking=false));assertFalse(model.uiState.value.canCloseContour)
    }

    @Test fun squaredUnitsAndTwoLineCaptionUseTheSelectedLocale() {
        assertEquals("1.62 m²",ContourFormatter.area(1.62,MeasurementUnit.METERS))
        assertEquals("16200 cm²",ContourFormatter.area(1.62,MeasurementUnit.CENTIMETERS))
        assertEquals("S ≈ 1.62 m²\nP ≈ 5.62 m",ContourFormatter.label(1.62,5.62,MeasurementUnit.METERS))
        useLanguage("id")
        assertEquals("1,62 m²",ContourFormatter.area(1.62,MeasurementUnit.METERS))
    }
}
