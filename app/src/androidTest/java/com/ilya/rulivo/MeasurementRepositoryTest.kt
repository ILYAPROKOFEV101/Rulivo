package com.ilya.rulivo

import com.ilya.rulivo.R
import com.ilya.rulivo.i18n.text

import androidx.test.platform.app.InstrumentationRegistry
import com.ilya.rulivo.measure.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class MeasurementRepositoryTest {
    @Test fun persistedResultRetainsInputsSourcesOutlineUnitsAndTimestamp() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val file=File(context.cacheDir,"history-${UUID.randomUUID()}.json")
        try {
            val d=MeasureDraft(kind=MeasureKind.ROOM,name="Гостиная",shape=FloorShape.CONTOUR,
                contour=listOf(Point2(0.0,0.0),Point2(4.0,0.0),Point2(4.0,2.0),Point2(2.0,2.0),Point2(2.0,4.0),Point2(0.0,4.0)),
                contourSource=ValueSource.AR,volumeUnit=VolumeUnit.LITERS).withInput("height","2,7",ValueSource.MANUAL)
            val record=SavedMeasurement("saved-room",123456L,d,MeasurementCalculator.calculate(d).result!!)
            MeasurementRepository(file).save(listOf(record))
            val restored=MeasurementRepository(file).load().single()
            assertEquals(record,restored)
            assertEquals(32.4,restored.result.metrics.getValue("volume"),1e-10)
            assertEquals(record.result,MeasurementCalculator.calculate(restored.draft).result)
            assertTrue(com.ilya.rulivo.ui.measurement.measurementText(restored).contains("32400 l"))
        } finally {file.delete()}
    }
    @Test fun unrecognizedHistoryIsNotSilentlyOverwrittenOrTreatedAsEmpty() {
        val file=File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,"history-${UUID.randomUUID()}.json")
        try {
            file.writeText("{\"version\":999,\"records\":[]}")
            try {MeasurementRepository(file).load();fail("Unsupported schema accepted")}catch(expected:IllegalArgumentException){assertEquals(text(R.string.history_version_not_supported),expected.message)}
            assertTrue(file.readText().contains("999"))
        } finally {file.delete()}
    }
}
