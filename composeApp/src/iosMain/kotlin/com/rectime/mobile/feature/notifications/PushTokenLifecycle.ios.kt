package com.rectime.mobile.feature.notifications

import com.rectime.mobile.feature.auth.AuthSession

actual fun platformPushTokenLifecycle(): PushTokenLifecycle = IosPushTokenLifecycle

/** #280では既存のiOS登録経路を維持し、logout削除処理は#281で接続する。 */
private object IosPushTokenLifecycle : PushTokenLifecycle {
    override fun updateSession(session: AuthSession?) {
        IosPushTokenRegistrar.updateAccessToken(session?.accessToken)
    }

    override fun onTokenRefreshed(fcmToken: String) {
        IosPushTokenRegistrar.onTokenRefreshed(fcmToken)
    }

    override fun beginLogout(session: AuthSession?) = Unit

    override suspend fun logout(
        session: AuthSession?,
        remoteLogout: suspend (String?) -> Unit,
    ) {
        remoteLogout(null)
    }

    override fun completeLogout(session: AuthSession?) = Unit
}
