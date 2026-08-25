package com.rectime.mobile.feature.notifications

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.rectime.mobile.core.platform.initializePlatformContext

class RectimeFirebaseMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        initializePlatformContext(applicationContext)
        AndroidPushTokenRegistrar.onTokenRefreshed(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val notification = IncomingPushNotification.from(
            notificationTitle = message.notification?.title,
            notificationBody = message.notification?.body,
            data = message.data,
        )
        RectimeNotificationPresenter.show(
            context = applicationContext,
            notification = notification,
            notificationId = message.messageId?.hashCode() ?: notification.hashCode(),
            data = message.data,
        )
    }
}
