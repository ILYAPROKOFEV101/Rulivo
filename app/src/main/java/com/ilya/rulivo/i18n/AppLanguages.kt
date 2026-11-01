package com.ilya.rulivo.i18n

import android.content.res.Resources
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.ilya.rulivo.R
import org.xmlpull.v1.XmlPullParser
import java.util.Locale

object AppLanguages {
    /** The same catalog powers both the Android settings page and our picker. */
    fun supported(resources: Resources): List<Locale> = buildList {
        resources.getXml(R.xml.locales_config).use { parser ->
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG && parser.name == "locale") {
                    val tag = parser.getAttributeValue("http://schemas.android.com/apk/res/android", "name")
                    add(Locale.forLanguageTag(tag))
                }
            }
        }
    }

    val selected: Locale? get() = AppCompatDelegate.getApplicationLocales()[0]

    /** An empty list restores the device's language preference order. */
    fun select(locale: Locale?) {
        val locales = locale?.let { LocaleListCompat.create(it) } ?: LocaleListCompat.getEmptyLocaleList()
        if (locales != AppCompatDelegate.getApplicationLocales()) {
            AppCompatDelegate.setApplicationLocales(locales)
        }
    }
}
