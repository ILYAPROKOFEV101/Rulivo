package com.ilya.rulivo.core.dunta

import android.Manifest
import android.graphics.Bitmap
import android.os.Build
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import com.ilya.rulivo.MainActivity
import com.ilya.rulivo.R
import com.ilya.rulivo.RulivoApplication
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

class SupportModeUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val controller get() = (compose.activity.application as RulivoApplication).duntaSdkController
    private fun label(id: Int) = compose.activity.getString(id)

    @Test fun menuCanEnableWithConsentAndStopSupportMode() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            automation.grantRuntimePermission(compose.activity.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        try {
            compose.runOnIdle { controller.disable() }
            compose.onNodeWithText(label(R.string.settings_dunta_battery_title)).performScrollTo().performClick()
            compose.onNodeWithContentDescription(label(R.string.settings_dunta_title)).assertIsOff().performClick()
            compose.onNodeWithText(label(R.string.support_prompt_title)).assertIsDisplayed()
            compose.onNodeWithText(label(R.string.support_prompt_not_now)).assertIsDisplayed()
            compose.waitForIdle()
            screenshot("consent")
            compose.onNodeWithText(label(R.string.support_prompt_enable)).performClick()
            compose.waitUntil(10_000) { controller.state.value.isEnabled }
            compose.onNodeWithText(label(R.string.settings_dunta_battery_title)).performScrollTo().performClick()
            compose.onNodeWithContentDescription(label(R.string.settings_dunta_title)).assertIsOn()
            screenshot("settings-enabled")
            compose.onNodeWithContentDescription(label(R.string.settings_dunta_title)).performClick()
            compose.onNodeWithContentDescription(label(R.string.settings_dunta_title)).assertIsOff()
            compose.onNodeWithText(label(R.string.settings_dunta_disabled)).assertIsDisplayed()
            compose.runOnIdle { assertFalse(controller.isPeer) }
        } finally {
            compose.runOnIdle { controller.disable() }
        }
    }

    private fun screenshot(name: String) {
        val directory = File(compose.activity.getExternalFilesDir(null), "dunta-qa").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use {
            compose.onNode(isDialog()).captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
