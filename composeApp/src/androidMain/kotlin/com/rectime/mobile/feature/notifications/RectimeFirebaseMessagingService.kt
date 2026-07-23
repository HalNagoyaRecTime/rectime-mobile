package com.rectime.mobile.feature.notifications

import com.google.firebase.messaging.FirebaseMessagingService
import com.rectime.mobile.feature.auth.setAuthPlatformContext

class RectimeFirebaseMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        setAuthPlatformContext(applicationContext)
        AndroidPushTokenRegistrar.onTokenRefreshed(token)
    }
}
