package com.ilya.rulivo.i18n

import java.math.BigDecimal
import java.text.DecimalFormatSymbols

/** Accept both localized keyboards and existing saved decimal/comma input.
 * Grouping separators are deliberately not accepted: dimensions must be unambiguous.
 */
object LocalizedNumbers {
    fun normalize(input: String): String = buildString {
        input.trim().forEach { character ->
            val digit = Character.digit(character, 10)
            append(when {
                digit >= 0 -> '0' + digit
                character == ',' || character == '\u066b' -> '.'
                character == '\u2212' -> '-'
                else -> character
            })
        }
    }

    fun editable(value: Double): String {
        val symbols = DecimalFormatSymbols(AppStrings.locale)
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString().map { character ->
            when (character) {
                '.' -> symbols.decimalSeparator
                '-' -> symbols.minusSign
                in '0'..'9' -> symbols.zeroDigit + (character - '0')
                else -> character
            }
        }.joinToString("")
    }
}
