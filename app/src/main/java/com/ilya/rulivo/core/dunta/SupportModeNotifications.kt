package com.ilya.rulivo.core.dunta

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationManagerCompat
import com.ilya.rulivo.R
import com.ilya.rulivo.i18n.text
import com.progun.dunta_sdk.android.core.DuntaService

/** The bundled SDK caches notification text and hardcodes its channel's display name. */
internal object SupportModeNotifications {
    private val handler = Handler(Looper.getMainLooper())
    private var pending: Runnable? = null

    fun refreshAfterStart(context: Context) {
        val appContext = context.applicationContext
        handler.post {
            pending?.let(handler::removeCallbacks)
            var attempts = 0
            val refresh = object : Runnable {
                override fun run() {
                    // Service creation is asynchronous; only retry briefly while it starts.
                    if (!refreshExisting(appContext) && ++attempts < 20) handler.postDelayed(this, 250)
                    else pending = null
                }
            }
            pending = refresh
            refresh.run()
        }
    }

    internal fun refreshExisting(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
        val manager = context.getSystemService(NotificationManager::class.java) ?: return true
        manager.getNotificationChannel(DuntaService.CHANNEL_ID)?.let { channel ->
            channel.name = text(R.string.dunta_notification_title)
            manager.createNotificationChannel(channel)
        }
        val active = manager.activeNotifications.filter { it.notification.channelId == DuntaService.CHANNEL_ID }
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return true
        for (entry in active) {
            val title = text(R.string.dunta_notification_title)
            val content = text(R.string.dunta_notification_content)
            if (entry.notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() == title &&
                entry.notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() == content) continue
            val notification = Notification.Builder.recoverBuilder(context, entry.notification)
                .setContentTitle(title).setContentText(content).setOnlyAlertOnce(true).build()
            // Update the existing foreground notification without changing SDK consent or service state.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED) {
                manager.notify(entry.tag, entry.id, notification)
            }
        }
        return active.isNotEmpty()
    }
}
