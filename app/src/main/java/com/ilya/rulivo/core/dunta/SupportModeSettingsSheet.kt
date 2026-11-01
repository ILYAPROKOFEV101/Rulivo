package com.ilya.rulivo.core.dunta

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ilya.rulivo.R
import com.ilya.rulivo.i18n.LanguageDropdown

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportModeSettingsSheet(
    state: DuntaSdkUiState,
    onEnabledChange: (Boolean) -> Unit,
    onBatterySettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val sharingTitle = stringResource(R.string.settings_dunta_title)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .navigationBarsPadding().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.settings_dunta_battery_title), style = MaterialTheme.typography.headlineSmall)
            LanguageDropdown()
            ListItem(
                headlineContent = { Text(sharingTitle) },
                supportingContent = {
                    Text(stringResource(if (state.isConfigured) R.string.settings_dunta_subtitle else R.string.settings_dunta_unavailable))
                },
                trailingContent = {
                    Switch(
                        checked = state.isEnabled,
                        onCheckedChange = onEnabledChange,
                        enabled = state.isConfigured,
                        modifier = Modifier.semantics { contentDescription = sharingTitle },
                    )
                },
            )
            Text(
                stringResource(if (state.isEnabled) R.string.settings_dunta_enabled else R.string.settings_dunta_disabled),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (state.isEnabled) {
                TextButton(onClick = onBatterySettings) {
                    Text(stringResource(
                        if (state.batteryOptimizationExempt) R.string.settings_dunta_battery_unrestricted
                        else R.string.settings_dunta_battery_restricted,
                    ))
                }
            }
            TextButton(onClick = { uriHandler.openUri(DuntaSdkController.PRIVACY_POLICY_URL) }) {
                Text(stringResource(R.string.settings_dunta_more_info_title))
            }
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        }
    }
}
