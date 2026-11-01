package com.ilya.rulivo

import com.ilya.rulivo.R
import com.ilya.rulivo.i18n.text

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.Before
import java.io.File

/** Runs the actual activity and its default ViewModel factories, without requiring AR hardware. */
class RulivoLaunchTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Before fun dismissOptionalSupportOnboarding() {
        compose.runOnIdle { (compose.activity.application as RulivoApplication).duntaSdkController.disable() }
    }
    @Test fun realAppOpensModesAndRestoresUnsavedDimensionAcrossActivityRecreation() {
        compose.onNodeWithText(text(R.string.what_are_we_measuring)).assertIsDisplayed()
        val folder=File(compose.activity.getExternalFilesDir(null),"modes-qa").apply{mkdirs()}
        File(folder,"mode-picker.png").outputStream().use {compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
        compose.onNodeWithText(text(R.string.mode_object),useUnmergedTree=true).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.length_m)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.back_to_modes)).performClick()
        compose.onNodeWithText(text(R.string.enter_dimensions_manually)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.length_m)).performScrollTo().performTextReplacement("0,6")
        compose.onNodeWithText(text(R.string.length_m)).performImeAction()
        compose.activityRule.scenario.recreate()
        // Manual editing is an explicit option after activity recreation as well.
        if(compose.onAllNodesWithText(text(R.string.enter_dimensions_manually)).fetchSemanticsNodes().isNotEmpty())
            compose.onNodeWithText(text(R.string.enter_dimensions_manually)).performScrollTo().performClick()
        compose.onNodeWithText("0,6").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.save_result)).performScrollTo().assertIsNotEnabled()
    }
}
