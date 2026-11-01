package com.ilya.rulivo.ui.measurement

import org.junit.Assert.assertEquals
import org.junit.Test

class DistanceFormatterTest : com.ilya.rulivo.i18n.LocalizedTest() {
    @Test fun requestedExamples() {
        assertEquals("1 m", DistanceFormatter.format(1.0))
        assertEquals("50 cm", DistanceFormatter.format(0.5))
        assertEquals("0 cm", DistanceFormatter.format(0.0))
    }

    @Test fun autoUsesUnroundedMetersAtBoundary() {
        assertEquals(MeasurementUnit.CENTIMETERS, DistanceFormatter.effectiveUnit(0.9999, MeasurementUnit.AUTO))
        assertEquals(MeasurementUnit.METERS, DistanceFormatter.effectiveUnit(1.0, MeasurementUnit.AUTO))
        assertEquals(MeasurementUnit.METERS, DistanceFormatter.effectiveUnit(10.0, MeasurementUnit.AUTO))
    }

    @Test fun reasonableRounding() {
        assertEquals("34.7 cm", DistanceFormatter.format(0.3472))
        assertEquals("1.42 m", DistanceFormatter.format(1.423))
        assertEquals("1.43 m", DistanceFormatter.format(1.426))
    }

    @Test fun explicitUnitsOverrideAuto() {
        assertEquals("142 cm", DistanceFormatter.format(1.42, MeasurementUnit.CENTIMETERS))
        assertEquals("0.5 m", DistanceFormatter.format(0.5, MeasurementUnit.METERS))
        assertEquals("0 m", DistanceFormatter.format(0.0, MeasurementUnit.METERS))
    }

    @Test fun removesTrailingZerosAndNegativeZero() {
        assertEquals("2 m", DistanceFormatter.format(2.0))
        assertEquals("1.4 m", DistanceFormatter.format(1.4))
        assertEquals("0 cm", DistanceFormatter.format(-0.0))
    }

    @Test(expected = IllegalArgumentException::class) fun rejectsNegativeDistance() {
        DistanceFormatter.format(-1.0)
    }

    @Test(expected = IllegalArgumentException::class) fun rejectsNonFiniteDistance() {
        DistanceFormatter.format(Double.NaN)
    }
}
