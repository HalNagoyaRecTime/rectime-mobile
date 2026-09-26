package com.rectime.mobile.feature.notifications

import com.rectime.mobile.feature.auth.AuthSession

actual fun platformPushTokenLifecycle(): PushTokenLifecycle = JvmPushTokenLifecycle

private object JvmPushTokenLifecycle : PushTokenLifecycle {
    override fun updateSession(session: AuthSession?) = Unit

    override fun onTokenRefreshed(fcmToken: String) = Unit

    override fun beginLogout(session: AuthSession?) = Unit

    override fun completeLogout(session: AuthSession?) = Unit

    override suspend fun logout(
        session: AuthSession?,
        remoteLogout: suspend (String?) -> Unit,
    ) {
        remoteLogout(null)
    }
}
