package com.ilya.rulivo.core.dunta

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.ilya.rulivo.R
import com.ilya.rulivo.i18n.AppStrings
import com.progun.dunta_sdk.android.core.DuntaService
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class SupportModeNotificationLocaleTest {
    @Test fun existingSdkNotificationAndChannelFollowLanguageWithoutStartingTheSdk() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        val tag = "locale-test"
        val id = 7241
        try {
            manager.createNotificationChannel(NotificationChannel(DuntaService.CHANNEL_ID,
                "SDK channel", NotificationManager.IMPORTANCE_LOW))
            manager.notify(tag, id, NotificationCompat.Builder(context, DuntaService.CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher).setContentTitle("Old title").setContentText("Old text").build())
            awaitNotification { manager.activeNotifications.any { it.id == id && it.tag == tag } }
            for (language in listOf("en", "id", "ur", "ha", "hi")) {
                val resources = context.createConfigurationContext(Configuration(context.resources.configuration).apply {
                    setLocales(LocaleList.forLanguageTags(language))
                }).resources
                AppStrings.initialize(resources)
                assertTrue(SupportModeNotifications.refreshExisting(context))
                // NotificationManager enqueues updates in the system process.
                awaitNotification {
                    manager.activeNotifications.firstOrNull { it.id == id && it.tag == tag }
                        ?.notification?.extras?.getString(Notification.EXTRA_TITLE) ==
                        resources.getString(R.string.dunta_notification_title)
                }
                val notification = manager.activeNotifications.single { it.id == id && it.tag == tag }.notification
                assertEquals(resources.getString(R.string.dunta_notification_title),
                    notification.extras.getString(Notification.EXTRA_TITLE))
                assertEquals(resources.getString(R.string.dunta_notification_content),
                    notification.extras.getString(Notification.EXTRA_TEXT))
                assertEquals(resources.getString(R.string.dunta_notification_title),
                    manager.getNotificationChannel(DuntaService.CHANNEL_ID).name.toString())
            }
        } finally {
            manager.cancel(tag, id)
            AppStrings.initialize(context.resources)
            SupportModeNotifications.refreshExisting(context)
        }
    }

    private fun awaitNotification(ready: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 5_000
        while (!ready() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(50)
        assertTrue("Notification update was not delivered", ready())
    }
}
