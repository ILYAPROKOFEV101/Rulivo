package com.ilya.rulivo

import android.app.Application
import android.content.res.Configuration
import android.os.LocaleList
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.SavedStateHandle
import androidx.test.platform.app.InstrumentationRegistry
import com.ilya.rulivo.measure.MeasurementRepository
import com.ilya.rulivo.ui.measurement.ToolkitScreen
import com.ilya.rulivo.ui.measurement.ToolkitState
import com.ilya.rulivo.ui.measurement.ToolkitViewModel
import com.ilya.rulivo.ui.theme.RulivoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File

class LocaleUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun menuRecomposesAcrossLanguagesAndUrduUsesRightToLeft() {
        val application = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
        val language = mutableStateOf("en")
        var direction = LayoutDirection.Ltr
        val model = ToolkitViewModel(application, SavedStateHandle(), MeasurementRepository(File(application.cacheDir, "locale-ui-empty.json")))
        compose.setContent {
            val configuration = Configuration(application.resources.configuration).apply {
                setLocales(LocaleList.forLanguageTags(language.value))
                fontScale = 1.3f
            }
            val context = application.createConfigurationContext(configuration)
            CompositionLocalProvider(LocalContext provides context, LocalConfiguration provides configuration,
                LocalResources provides context.resources) {
                RulivoTheme {
                    direction = LocalLayoutDirection.current
                    ToolkitScreen(ToolkitState(loading = false), model, emptyList(), { _, _ -> }, {}, {})
                }
            }
        }
        for ((tag, objectLabel) in listOf("en" to "Object", "id" to "Objek", "ur" to "شے", "hi" to "वस्तु", "ha" to "Abu", "ar" to "Object")) {
            compose.runOnIdle { language.value = tag }
            compose.onNodeWithText(objectLabel, useUnmergedTree = true).assertIsDisplayed()
            compose.runOnIdle { assertEquals(tag, if (tag == "ur") LayoutDirection.Rtl else LayoutDirection.Ltr, direction) }
        }
    }
}
