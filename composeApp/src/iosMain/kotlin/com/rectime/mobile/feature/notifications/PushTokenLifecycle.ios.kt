package com.rectime.mobile.feature.notifications

import com.rectime.mobile.feature.auth.AuthSession
import com.rectime.mobile.feature.auth.AuthSessionStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

actual fun platformPushTokenLifecycle(): PushTokenLifecycle = IosPushTokenLifecycle

interface IosFirebaseMessagingTokenDeletionHandler {
    fun deleteToken(completion: (Boolean) -> Unit)
}

object IosPushTokenLifecycle : PushTokenLifecycle {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var deletionHandler: IosFirebaseMessagingTokenDeletionHandler? = null
    private val manager = PushTokenLifecycleManager(
        platform = FirebasePlatform.Ios,
        scope = scope,
        register = { fcmToken, platform, accessToken ->
            withApi { api -> api.register(fcmToken, platform, accessToken) }
        },
        currentFcmToken = { null },
        deleteMessagingToken = ::deleteMessagingToken,
        restoreSession = { AuthSessionStore().load() },
        onFailure = ::logFailure,
    )

    fun installDeletionHandler(handler: IosFirebaseMessagingTokenDeletionHandler) {
        deletionHandler = handler
    }

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

    private suspend fun deleteMessagingToken() = suspendCancellableCoroutine { continuation ->
        val handler = deletionHandler
        if (handler == null) {
            continuation.resumeWithException(
                IllegalStateException("FCMトークン削除処理が設定されていません"),
            )
            return@suspendCancellableCoroutine
        }
        handler.deleteToken { deleted ->
            if (!continuation.isActive) return@deleteToken
            if (deleted) {
                continuation.resume(Unit)
            } else {
                continuation.resumeWithException(
                    IllegalStateException("FCMトークンを削除できませんでした"),
                )
            }
        }
    }

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
