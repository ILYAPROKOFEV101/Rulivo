package com.ilya.rulivo.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.res.stringResource
import android.text.TextUtils
import android.view.View
import com.ilya.rulivo.R
import com.ilya.rulivo.i18n.AppStrings
import java.util.Locale

private val RulivoColors = darkColorScheme(
    primary = Lime,
    onPrimary = Ink,
    secondary = Lime,
    secondaryContainer = Color(0xFF2A3827),
    onSecondaryContainer = Lime,
    background = Ink,
    surface = Color(0xFF182222),
    onSurface = Color.White,
    onBackground = Color.White,
    onSurfaceVariant = Muted,
)

@Composable
fun RulivoTheme(content: @Composable () -> Unit) {
    // Observe configuration changes, including Compose previews and localized test contexts.
    AppStrings.initialize(LocalResources.current)
    val language = Locale.forLanguageTag(stringResource(R.string.resource_locale))
    val direction = if (TextUtils.getLayoutDirectionFromLocale(language) == View.LAYOUT_DIRECTION_RTL)
        LayoutDirection.Rtl else LayoutDirection.Ltr
    MaterialTheme(colorScheme = RulivoColors, typography = Typography) {
        CompositionLocalProvider(LocalContentColor provides RulivoColors.onBackground,
            LocalLayoutDirection provides direction, content = content)
    }
}
