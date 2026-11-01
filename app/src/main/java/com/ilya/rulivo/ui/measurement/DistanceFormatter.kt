package com.ilya.rulivo.ui.measurement

import com.ilya.rulivo.R
import com.ilya.rulivo.i18n.text

import com.ilya.rulivo.ar.DistanceCalculator
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import com.ilya.rulivo.i18n.AppStrings

enum class MeasurementUnit(private val textId: Int) { AUTO(R.string.unit_auto), CENTIMETERS(R.string.unit_centimeter), METERS(R.string.unit_meter_short);
    val label: String get() = text(textId)
}

object DistanceFormatter {
    fun effectiveUnit(meters: Double, unit: MeasurementUnit): MeasurementUnit = when (unit) {
        MeasurementUnit.AUTO -> if (meters < 1.0) MeasurementUnit.CENTIMETERS else MeasurementUnit.METERS
        else -> unit
    }

    fun format(meters: Double, unit: MeasurementUnit = MeasurementUnit.AUTO): String {
        require(meters.isFinite() && meters >= 0.0) { "Distance must be finite and non-negative" }
        val centimeters = effectiveUnit(meters, unit) == MeasurementUnit.CENTIMETERS
        val value = if (centimeters) DistanceCalculator.metersToCentimeters(meters) else meters
        val formatter = DecimalFormat(if (centimeters) "0.#" else "0.##", DecimalFormatSymbols(AppStrings.locale))
        formatter.roundingMode = RoundingMode.HALF_UP
        return text(R.string.numeric_value_with_unit, formatter.format(if (value == 0.0) 0.0 else value),
            text(if (centimeters) R.string.unit_centimeter else R.string.unit_meter))
    }
}
