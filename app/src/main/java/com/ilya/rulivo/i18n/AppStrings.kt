package com.ilya.rulivo.i18n

import android.content.res.Resources
import androidx.annotation.StringRes
import com.ilya.rulivo.R
import java.text.NumberFormat
import java.util.Locale

/** Resource access for the camera/geometry threads as well as the UI.
 * Android selects translations from the current system or per-app language list.
 * Keep Resources, never translated strings, so configuration changes take effect.
 */
object AppStrings {
    internal interface Source {
        val locale: Locale
        fun get(@StringRes id: Int): String
    }

    @Volatile internal var source: Source? = null

    fun initialize(resources: Resources) {
        source = object : Source {
            override fun get(id: Int) = resources.getString(id)
            override val locale: Locale
                get() {
                    val translatedLocale = Locale.forLanguageTag(get(R.string.resource_locale))
                    val locales = resources.configuration.locales
                    // Preserve the selected language's regional number/date conventions.
                    return (0 until locales.size()).map { locales[it] }
                        .firstOrNull { it.language == translatedLocale.language } ?: translatedLocale
                }
        }
    }

    private val current: Source get() = checkNotNull(source) { "AppStrings must be initialized by RulivoApplication" }
    val locale: Locale get() = current.locale

    fun get(@StringRes id: Int, vararg arguments: Any): String {
        val resources = current
        val template = resources.get(id)
        if (arguments.isEmpty()) return template
        val locale = resources.locale
        val integerFormat = NumberFormat.getIntegerInstance(locale).apply { isGroupingUsed = false }
        val localizedArguments = arguments.map {
            when (it) {
                is Int, is Long -> integerFormat.format(it)
                else -> it
            }
        }.toTypedArray()
        return String.format(locale, template, *localizedArguments)
    }
}

fun text(@StringRes id: Int, vararg arguments: Any): String = AppStrings.get(id, *arguments)
