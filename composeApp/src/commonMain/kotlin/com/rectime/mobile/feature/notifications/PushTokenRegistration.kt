package com.rectime.mobile.feature.notifications

import com.rectime.mobile.feature.auth.AuthSession

expect fun platformPushTokenLifecycle(): PushTokenLifecycle

interface PushTokenLifecycle {
    fun updateSession(session: AuthSession?)
    fun onTokenRefreshed(fcmToken: String)
    fun beginLogout(session: AuthSession?)
    suspend fun logout(
        session: AuthSession?,
        remoteLogout: suspend (String?) -> Unit,
    )
}
