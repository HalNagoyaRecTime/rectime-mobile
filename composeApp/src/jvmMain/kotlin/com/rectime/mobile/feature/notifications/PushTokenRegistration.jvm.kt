package com.rectime.mobile.feature.notifications

import com.rectime.mobile.feature.auth.AuthSession

actual fun updatePushTokenRegistration(session: AuthSession?) = Unit

actual suspend fun unregisterPushToken(session: AuthSession) = Unit
