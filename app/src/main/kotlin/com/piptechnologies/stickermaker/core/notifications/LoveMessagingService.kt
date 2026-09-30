package com.piptechnologies.stickermaker.core.notifications

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Receives alerts and updates sent from the Firebase console. Only messages that arrive while the
 * app is in the foreground land here; FCM shows the others itself (see [PushNotifications]).
 */
class LoveMessagingService : FirebaseMessagingService() {

    /**
     * Campaigns reach the app's devices through the Firebase console, which tracks tokens itself:
     * there is no server of the app's own to hand a new token to.
     */
    override fun onNewToken(token: String) = Unit

    override fun onMessageReceived(message: RemoteMessage) {
        val notification = message.notification ?: return
        PushNotifications.show(this, notification.title, notification.body)
    }
}
