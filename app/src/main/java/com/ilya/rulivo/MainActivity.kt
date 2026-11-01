package com.ilya.rulivo

import android.Manifest
import android.graphics.Color
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ilya.rulivo.core.dunta.BatteryOptimizationController
import com.ilya.rulivo.core.dunta.DuntaBatterySettingsRequester
import com.ilya.rulivo.core.dunta.SupportModePromptDialog
import com.ilya.rulivo.core.dunta.SupportModeSettingsSheet
import com.ilya.rulivo.ui.measurement.RulivoScreen
import com.ilya.rulivo.ui.theme.RulivoTheme
import com.ilya.rulivo.i18n.AppStrings

class MainActivity : AppCompatActivity(), DuntaBatterySettingsRequester {
    private val duntaSdkController get() = (application as RulivoApplication).duntaSdkController
    private var consentAwaitingPermission = false
    private var resumeAwaitingPermission = false
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (consentAwaitingPermission) {
            consentAwaitingPermission = false
            duntaSdkController.completeConsent(granted)
        } else if (resumeAwaitingPermission) {
            resumeAwaitingPermission = false
            duntaSdkController.resumeConsentedMode(granted)
        }
    }
    private val batterySettingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { duntaSdkController.onBatterySettingsReturned() }

    override fun openDuntaBatterySettings() {
        batterySettingsLauncher.launch(BatteryOptimizationController.settingsIntent())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // AppCompat applies the selected language before view models resolve any text.
        AppStrings.initialize(resources)
        consentAwaitingPermission = savedInstanceState?.getBoolean(PENDING_CONSENT) == true
        resumeAwaitingPermission = savedInstanceState?.getBoolean(PENDING_RESUME) == true
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        setContent {
            val supportMode by duntaSdkController.state.collectAsStateWithLifecycle()
            var showSupportSettings by rememberSaveable { mutableStateOf(false) }
            RulivoTheme {
                RulivoScreen(
                    activity = this,
                    measurementModel = viewModel(),
                    onSupportSettings = { showSupportSettings = true },
                )
                if (showSupportSettings) {
                    SupportModeSettingsSheet(
                        state = supportMode,
                        onEnabledChange = { enabled ->
                            if (enabled) {
                                showSupportSettings = false
                                duntaSdkController.showConsent()
                            } else {
                                duntaSdkController.disable()
                            }
                        },
                        onBatterySettings = ::openDuntaBatterySettings,
                        onDismiss = { showSupportSettings = false },
                    )
                }
                if (supportMode.showConsent) {
                    SupportModePromptDialog(
                        sdkConfigured = supportMode.isConfigured,
                        onEnable = ::enableSupportMode,
                        onDismiss = duntaSdkController::decline,
                    )
                }
            }
        }
        if (savedInstanceState == null) {
            if (duntaSdkController.isPeer) {
                if (duntaSdkController.canPostNotifications()) {
                    duntaSdkController.resumeConsentedMode(true)
                } else {
                    resumeAwaitingPermission = true
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            } else {
                duntaSdkController.showInitialConsentIfNeeded()
            }
        } else if (savedInstanceState.getBoolean(SHOW_CONSENT)) {
            duntaSdkController.showConsent()
        }
    }

    override fun onResume() {
        super.onResume()
        duntaSdkController.syncStoredState()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(PENDING_CONSENT, consentAwaitingPermission)
        outState.putBoolean(PENDING_RESUME, resumeAwaitingPermission)
        outState.putBoolean(SHOW_CONSENT, duntaSdkController.state.value.showConsent)
        super.onSaveInstanceState(outState)
    }

    private fun enableSupportMode() {
        duntaSdkController.hideConsentForPermissionRequest()
        if (duntaSdkController.canPostNotifications()) {
            duntaSdkController.completeConsent(true)
        } else {
            consentAwaitingPermission = true
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private companion object {
        const val SHOW_CONSENT = "support_mode_show_consent"
        const val PENDING_RESUME = "support_mode_pending_resume"
        const val PENDING_CONSENT = "support_mode_pending_consent"
    }
}
