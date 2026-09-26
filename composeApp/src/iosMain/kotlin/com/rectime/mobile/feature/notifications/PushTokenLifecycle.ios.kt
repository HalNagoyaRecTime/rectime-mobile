package com.rectime.mobile.feature.notifications

import com.rectime.mobile.feature.auth.AuthSession
import com.rectime.mobile.feature.auth.AuthSessionStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

actual fun platformPushTokenLifecycle(): PushTokenLifecycle = IosPushTokenLifecycle

object IosPushTokenLifecycle : PushTokenLifecycle {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val manager = PushTokenLifecycleManager(
        platform = FirebasePlatform.Ios,
        scope = scope,
        register = { fcmToken, platform, accessToken ->
            withApi { api -> api.register(fcmToken, platform, accessToken) }
        },
        currentFcmToken = { null },
        // #280は既存のtoken登録を維持する。iOS Messaging token削除bridgeは#281で接続する。
        deleteMessagingToken = { false },
        restoreSession = { AuthSessionStore().load() },
        onFailure = ::logFailure,
    )

    fun onFirebaseTokenRefreshed(fcmToken: String?) {
        fcmToken?.takeIf(String::isNotBlank)?.let(manager::onTokenRefreshed)
    }

    override fun updateSession(session: AuthSession?) = manager.updateSession(session)

    override fun onTokenRefreshed(fcmToken: String) = manager.onTokenRefreshed(fcmToken)

    override fun beginLogout(session: AuthSession?) = manager.beginLogout(session)

    override suspend fun logout(
        session: AuthSession?,
        remoteLogout: suspend (String?) -> Unit,
    ) = manager.logout(session, remoteLogout)

    private suspend fun <T> withApi(block: suspend (FirebaseTokenApi) -> T): T {
        val api = FirebaseTokenApi()
        return try {
            block(api)
        } finally {
            api.close()
        }
    }

    private fun logFailure(error: Throwable) {
        if (error is CancellationException) throw error
    }
}
