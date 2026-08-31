package com.rectime.mobile.feature.notifications

import com.rectime.mobile.feature.auth.AuthSession

expect fun updatePushTokenRegistration(session: AuthSession?)

expect suspend fun unregisterPushToken(session: AuthSession)
