package com.ilya.rulivo.i18n

import com.ilya.rulivo.ar.HeightEditing
import com.ilya.rulivo.measure.*
import com.ilya.rulivo.ui.measurement.DistanceFormatter
import com.ilya.rulivo.ui.measurement.measurementText
import org.junit.Assert.*
import org.junit.Test

class LocalizationTest : LocalizedTest() {
    @Test fun localizedDigitsAndDecimalSeparatorsAreAcceptedWithoutGroupingAmbiguity() {
        for (input in listOf("१२.५", "۱۲٫۵", "١٢٫٥", "12,5", "12.5")) {
            assertEquals(input, 12.5, Quantities.parse(input)!!, 0.0)
        }
        assertEquals(-1.25, HeightEditing.parse("−۱٫۲۵")!!, 0.0)
        for (input in listOf("1,234.5", "1.234,5", "1٬234", "१२abc", "NaN")) {
            assertNull(input, Quantities.parse(input))
        }
    }

    @Test fun numbersFollowLocaleAndEditableValuesRoundTrip() {
        useLanguage("id-ID")
        assertEquals("1,25 m", DistanceFormatter.format(1.25))
        assertEquals("0,125", Quantities.editable(0.125))
        for (tag in listOf("en-US", "id-ID", "ur-PK", "ha-NG", "hi-IN", "hi-IN-u-nu-deva")) {
            useLanguage(tag)
            assertEquals(tag, 0.125, Quantities.parse(Quantities.editable(0.125))!!, 0.0)
        }
    }

    @Test fun enumLabelsAndSavedPresentationChangeLanguageWithoutChangingUserData() {
        val draft = MeasureDraft(name = "My shelf").withInput("length", "2")
            .withInput("width", "3").withInput("height", "4")
        val record = SavedMeasurement("id", 123L, draft, MeasurementCalculator.calculate(draft).result!!)
        val english = MeasureKind.BOX.title
        val oldTitle = record.result.title
        useLanguage("hi-IN")
        assertEquals("वस्तु", MeasureKind.BOX.title)
        assertNotEquals(english, MeasureKind.BOX.title)
        assertNotEquals(oldTitle, record.localizedResult.title)
        assertEquals(record.result.metrics, record.localizedResult.metrics)
        assertEquals("My shelf", record.displayName)
        assertTrue(measurementText(record).contains("My shelf"))
        assertFalse(measurementText(record).contains(oldTitle))
        assertEquals(record.localizedResult.title, record.copy(draft = draft.copy(name = "")).displayName)
        useLanguage("en")
        assertEquals(english, MeasureKind.BOX.title)
    }

    @Test fun shortfallLabelsDescribeTheCorrectAxisInEveryLanguage() {
        for (tag in listOf("en", "id", "ur", "ha", "hi")) {
            useLanguage(tag)
            for (axis in listOf("remainingWidth", "remainingDepth", "remainingHeight")) {
                assertNotEquals(tag, Quantities.label(axis), Quantities.label(axis, shortfall = true))
            }
        }
    }
}
