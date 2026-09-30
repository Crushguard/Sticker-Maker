package com.piptechnologies.stickermaker.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessaging
import com.piptechnologies.stickermaker.MainActivity
import com.piptechnologies.stickermaker.R

/**
 * Alerts and updates the team sends from the Firebase console (Messaging), over Firebase Cloud
 * Messaging like the other PIP apps. The Settings "Notifications" switch is the user's say: FCM
 * stays off until the switch and Android both allow notifications (AndroidManifest.xml keeps
 * auto-init off), and turning the switch off deletes this device's token, so campaigns stop
 * reaching it.
 */
object PushNotifications {

    /** The app's one channel, also FCM's default channel (AndroidManifest.xml). */
    const val CHANNEL_UPDATES = "updates"

    /** Creates the channel, or renames it into the current language. Cheap; safe to call often. */
    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_UPDATES,
            context.getString(R.string.notification_channel_updates),
            NotificationManager.IMPORTANCE_DEFAULT
        )
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    /** Whether Android lets the app post: the permission on 13+, and the app's switch in system settings. */
    fun systemAllows(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** Registers this device with FCM when [enabled]; otherwise forgets its token. */
    fun setPushEnabled(enabled: Boolean) {
        runCatching {
            val messaging = FirebaseMessaging.getInstance()
            messaging.isAutoInitEnabled = enabled
            if (!enabled) messaging.deleteToken()
        }
    }

    /**
     * Shows an alert that arrived while the app was open; FCM shows the others itself, with the
     * same channel, icon and color (AndroidManifest.xml). A tap opens the app.
     */
    fun show(context: Context, title: String?, body: String?) {
        if (title.isNullOrBlank() && body.isNullOrBlank()) return
        if (!systemAllows(context)) return
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_UPDATES)
            .setSmallIcon(R.drawable.ic_stat_notification)
            .setColor(ContextCompat.getColor(context, R.color.rose))
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(System.currentTimeMillis().toInt(), notification)
        } catch (_: SecurityException) {
            // The permission went away between the check and the post: nothing to show.
        }
    }
}
