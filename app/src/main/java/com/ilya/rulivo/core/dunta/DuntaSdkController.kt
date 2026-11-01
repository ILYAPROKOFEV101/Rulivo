package com.ilya.rulivo.core.dunta

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.ilya.rulivo.BuildConfig
import com.progun.dunta_sdk.api.DuntaManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class DuntaConsentState { Peer, NotPeer, NotInitialized, Unavailable }

data class DuntaSdkUiState(
    val isConfigured: Boolean = false,
    val isEnabled: Boolean = false,
    val consentState: DuntaConsentState = DuntaConsentState.Unavailable,
    val showConsent: Boolean = false,
    val batteryOptimizationExempt: Boolean = false,
)

class DuntaSdkController(private val appContext: Context) {
    private val engine = DuntaSdkEngine(appContext)
    private val reminders = appContext.getSharedPreferences("support_mode_prompts", Context.MODE_PRIVATE)
    val isConfigured: Boolean get() = engine.isConfigured
    val isPeer: Boolean get() = engine.isPeer
    private val userState: DuntaManager.UserState
        get() = engine.userState
    private val _state = MutableStateFlow(DuntaSdkUiState())
    val state: StateFlow<DuntaSdkUiState> = _state.asStateFlow()

    init {
        refreshState()
    }

    fun prepare() {
        engine.refreshLanguage()
        refreshState()
    }

    fun refreshLanguage() = engine.refreshLanguage()

    fun syncStoredState() {
        if (canPostNotifications()) restartIfEnabled()
        syncRecovery()
        refreshState()
    }

    fun showInitialConsentIfNeeded() {
        if (!isConfigured && !BuildConfig.DEBUG) return
        when (userState) {
            DuntaManager.UserState.NOT_INIT -> showConsent()
            DuntaManager.UserState.NOT_PEER -> {
                val declines = reminders.getInt("declines", 0)
                if (declines >= 2) return
                if (declines == 0) {
                    reminders.edit().putInt("declines", 1).putInt("launches", 1).apply()
                    return
                }
                val launches = reminders.getInt("launches", 0) + 1
                reminders.edit().putInt("launches", launches).apply()
                if (launches >= 3) {
                    // One reminder, including migration of previously declined users.
                    finishReminders()
                    showConsent()
                }
            }
            DuntaManager.UserState.PEER -> Unit
        }
    }

    fun showConsent() {
        if (isConfigured || BuildConfig.DEBUG) _state.value = _state.value.copy(showConsent = true)
    }

    fun hideConsentForPermissionRequest() {
        finishReminders()
        _state.value = _state.value.copy(showConsent = false)
    }

    fun completeConsent(notificationsGranted: Boolean) {
        hideConsentForPermissionRequest()
        finishReminders()
        // Match MapShots: permission denial leaves SDK consent unchanged.
        if (notificationsGranted) engine.enable(appContext)
        refreshState()
    }

    fun resumeConsentedMode(notificationsGranted: Boolean) {
        if (notificationsGranted && isPeer) engine.enable(appContext)
        refreshState()
    }

    fun decline() {
        engine.decline(appContext)
        _state.value = _state.value.copy(showConsent = false)
        refreshState()
        val declines = reminders.getInt("declines", 0)
        if (declines == 0) {
            reminders.edit().putInt("declines", 1).putInt("launches", 0).apply()
        } else {
            finishReminders()
        }
    }

    fun disable() {
        stop()
        finishReminders()
    }

    private fun stop() {
        engine.stop(appContext)
        _state.value = _state.value.copy(showConsent = false)
        refreshState()
    }

    internal fun restartIfEnabled(): Boolean = engine.restartIfEnabled()

    fun syncRecovery() {
        engine.syncRecovery()
        refreshState()
    }

    fun onBatterySettingsReturned() {
        syncRecovery()
        if (isPeer) engine.start(appContext)
        refreshState()
    }

    internal fun canPostNotifications(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun finishReminders() {
        reminders.edit().putInt("declines", 2).apply()
    }

    private fun refreshState() {
        _state.value = _state.value.copy(
            isConfigured = isConfigured,
            isEnabled = isConfigured && isPeer,
            consentState = if (!isConfigured) DuntaConsentState.Unavailable else when (userState) {
                DuntaManager.UserState.PEER -> DuntaConsentState.Peer
                DuntaManager.UserState.NOT_PEER -> DuntaConsentState.NotPeer
                DuntaManager.UserState.NOT_INIT -> DuntaConsentState.NotInitialized
            },
            batteryOptimizationExempt = BatteryOptimizationController.isIgnoring(appContext),
        )
    }

    companion object {
        const val PRIVACY_POLICY_URL = "https://zmoba.com/privacyPolicy"
    }
}
