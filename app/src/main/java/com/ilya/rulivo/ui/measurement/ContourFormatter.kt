package com.ilya.rulivo.ui.measurement

import com.ilya.rulivo.R
import com.ilya.rulivo.i18n.text
import com.ilya.rulivo.measure.Quantities

object ContourFormatter {
    fun area(squareMeters: Double, unit: MeasurementUnit): String {
        val centimeters = unit == MeasurementUnit.CENTIMETERS || unit == MeasurementUnit.AUTO && squareMeters < 1.0
        return Quantities.number(if (centimeters) squareMeters * 10_000 else squareMeters, 2) +
            text(if (centimeters) R.string.square_centimeters_suffix else R.string.square_meters_suffix)
    }

    fun label(area: Double, perimeter: Double, unit: MeasurementUnit): String =
        text(R.string.contour_area_label, area(area, unit)) + "\n" +
            text(R.string.contour_perimeter_label, DistanceFormatter.format(perimeter, unit))
}
