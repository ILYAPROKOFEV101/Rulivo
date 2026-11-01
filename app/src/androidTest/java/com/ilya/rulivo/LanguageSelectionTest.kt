package com.ilya.rulivo

import android.graphics.Bitmap
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.ilya.rulivo.i18n.AppLanguages
import com.ilya.rulivo.i18n.AppStrings
import com.ilya.rulivo.ui.measurement.ToolkitViewModel
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

class LanguageSelectionTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private var originalLocales = LocaleListCompat.getEmptyLocaleList()

    @Before fun rememberLanguageAndDismissConsent() {
        compose.runOnIdle {
            originalLocales = AppCompatDelegate.getApplicationLocales()
            (compose.activity.application as RulivoApplication).duntaSdkController.hideConsentForPermissionRequest()
        }
    }

    @After fun restoreLanguage() {
        compose.runOnIdle { AppCompatDelegate.setApplicationLocales(originalLocales) }
        compose.waitForIdle()
    }

    @Test fun compactPickerChangesEveryLanguageAndKeepsItsChoiceAfterRecreation() {
        val languages = AppLanguages.supported(compose.activity.resources)
        compose.runOnIdle {
            ViewModelProvider(compose.activity)[ToolkitViewModel::class.java].change {
                it.withInput("length", "12.5").copy(name = "My shelf")
            }
        }
        compose.onNodeWithText(compose.activity.getString(R.string.settings_dunta_battery_title))
            .performScrollTo().performClick()
        // The options take no space until the user expands the single-row control.
        languages.forEach { compose.onNodeWithTag("language-option-${it.language}").assertDoesNotExist() }
        screenshot("language-collapsed")
        for (language in languages) {
            compose.onNodeWithTag("language-picker").performScrollTo().performClick()
            if (language == languages.first()) screenshot("language-expanded")
            compose.onNodeWithTag("language-option-${language.language}").performClick()
            compose.waitUntil(10_000) {
                compose.activity.resources.getString(R.string.resource_locale) == language.language
            }
            compose.waitForIdle()
            compose.onNodeWithTag("language-picker").assertIsDisplayed()
                .assertTextContains(language.getDisplayName(language))
            compose.runOnIdle {
                assertEquals(language.language, AppLanguages.selected?.language)
                assertEquals(compose.activity.getString(R.string.language_title), AppStrings.get(R.string.language_title))
                val draft = ViewModelProvider(compose.activity)[ToolkitViewModel::class.java].state.value.draft
                assertEquals("12.5", draft.input("length").text)
                assertEquals("My shelf", draft.name)
            }
            if (language.language == "ur") screenshot("language-urdu")
            compose.activityRule.scenario.recreate()
            compose.waitForIdle()
            compose.onNodeWithTag("language-picker").assertIsDisplayed()
                .assertTextContains(language.getDisplayName(language))
            compose.onNodeWithTag("language-picker").performClick()
            compose.onNodeWithTag("language-option-${language.language}").assertIsSelected().performClick()
        }
        compose.onNodeWithTag("language-picker").performClick()
        compose.onNodeWithTag("language-option-").performClick()
        compose.waitUntil(10_000) { AppCompatDelegate.getApplicationLocales().isEmpty }
        compose.waitForIdle()
        compose.onNodeWithTag("language-picker")
            .assertTextContains(compose.activity.getString(R.string.language_system))
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        compose.runOnIdle { assertNull(AppLanguages.selected) }
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val directory = File(compose.activity.getExternalFilesDir(null), "language-qa").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use {
            InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                .compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
