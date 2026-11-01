package com.ilya.rulivo.core.dunta

import android.Manifest
import android.os.Build
import android.os.SystemClock
import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.progun.dunta_sdk.android.core.DuntaService
import com.ilya.rulivo.MainActivity
import com.ilya.rulivo.RulivoApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SupportModeConsentTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val controller = (context as RulivoApplication).duntaSdkController

    @Test
    fun consentSurvivesRecreationAndDeclineStaysDisabledAfterReentry() {
        assumeTrue(controller.isConfigured)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity {
                controller.disable()
                controller.showConsent()
                assertTrue(controller.state.value.showConsent)
                assertFalse(controller.isPeer)
            }
            scenario.recreate()
            scenario.onActivity {
                assertTrue(controller.state.value.showConsent)
                controller.decline()
                assertFalse(controller.state.value.showConsent)
                assertFalse(controller.restartIfEnabled())
            }
            scenario.recreate()
            scenario.onActivity {
                assertEquals(DuntaConsentState.NotPeer, controller.state.value.consentState)
                assertFalse(controller.state.value.isEnabled)
                assertFalse(controller.state.value.showConsent)
            }
        }
    }

    @Test
    fun deniedNotificationsKeepPreviouslyDeclinedChoice() {
        assumeTrue(controller.isConfigured)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity {
                controller.disable()
                controller.showConsent()
                controller.completeConsent(notificationsGranted = false)
                controller.syncStoredState()
                assertFalse(controller.isPeer)
                assertFalse(controller.restartIfEnabled())
                assertFalse(controller.state.value.showConsent)
                assertEquals(DuntaConsentState.NotPeer, controller.state.value.consentState)
            }
        }
    }

    @Test
    fun acceptedConsentStartsServiceAndExplicitDisableStopsIt() {
        assumeTrue(controller.isConfigured)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
                context.packageName, Manifest.permission.POST_NOTIFICATIONS
            )
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            try {
                scenario.onActivity {
                    controller.completeConsent(notificationsGranted = true)
                    assertTrue(controller.isPeer)
                }
                awaitServiceState(running = true)
                scenario.onActivity {
                    // Reference app leaves an existing PEER choice unchanged on permission denial.
                    controller.completeConsent(notificationsGranted = false)
                    assertTrue(controller.isPeer)
                    controller.resumeConsentedMode(notificationsGranted = false)
                    assertTrue(controller.isPeer)
                    controller.disable()
                }
                awaitServiceState(running = false)
                scenario.recreate()
                scenario.onActivity { assertFalse(controller.isPeer) }
            } finally {
                scenario.onActivity { controller.disable() }
            }
        }
    }

    private fun awaitServiceState(running: Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 10_000
        while (DuntaService.serviceIsRunning != running && SystemClock.elapsedRealtime() < deadline) {
            SystemClock.sleep(100)
        }
        assertEquals("SDK service state", running, DuntaService.serviceIsRunning)
    }

    @Test
    fun explicitDisablePreventsAutomaticReminders() {
        assumeTrue(controller.isConfigured)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity {
                controller.disable()
                repeat(5) { controller.showInitialConsentIfNeeded() }
                assertFalse(controller.state.value.showConsent)
                assertFalse(controller.state.value.isEnabled)
            }
        }
    }
}
