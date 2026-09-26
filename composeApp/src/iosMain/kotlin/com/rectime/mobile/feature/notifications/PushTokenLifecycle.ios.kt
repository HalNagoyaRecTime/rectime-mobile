package com.rectime.mobile.feature.notifications

import com.rectime.mobile.feature.auth.AuthSession
import com.rectime.mobile.feature.auth.AuthSessionStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

actual fun platformPushTokenLifecycle(): PushTokenLifecycle = IosPushTokenLifecycle

interface IosFirebaseMessagingTokenDeletionHandler {
    fun deleteToken(completion: (String?) -> Unit)
}

interface IosFirebaseMessagingTokenProvider {
    fun getToken(completion: (String?) -> Unit)
}

object IosPushTokenLifecycle : PushTokenLifecycle {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var deletionHandler: IosFirebaseMessagingTokenDeletionHandler? = null
    private var tokenProvider: IosFirebaseMessagingTokenProvider? = null
    private val manager = PushTokenLifecycleManager(
        platform = FirebasePlatform.Ios,
        scope = scope,
        register = { fcmToken, platform, accessToken ->
            withApi { api -> api.register(fcmToken, platform, accessToken) }
        },
        currentFcmToken = ::currentFirebaseMessagingToken,
        deleteMessagingToken = ::deleteMessagingToken,
        restoreSession = { AuthSessionStore().load() },
        onFailure = ::logFailure,
    )

    fun installDeletionHandler(handler: IosFirebaseMessagingTokenDeletionHandler) {
        deletionHandler = handler
    }

    fun installTokenProvider(provider: IosFirebaseMessagingTokenProvider) {
        tokenProvider = provider
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

    override fun completeLogout(session: AuthSession?) = manager.completeLogout(session)

    private suspend fun deleteMessagingToken(): Boolean = suspendCancellableCoroutine { continuation ->
        val handler = deletionHandler
        if (handler == null) {
            continuation.resumeWithException(
                IllegalStateException("FCMトークン削除処理が設定されていません"),
            )
            return@suspendCancellableCoroutine
        }
        handler.deleteToken { errorMessage ->
            if (!continuation.isActive) return@deleteToken
            if (errorMessage == null) {
                continuation.resume(true)
            } else {
                continuation.resumeWithException(
                    IllegalStateException("FCMトークンを削除できませんでした"),
                )
            }
        }
    }

    private suspend fun currentFirebaseMessagingToken(): String? =
        suspendCancellableCoroutine { continuation ->
            val provider = tokenProvider
            if (provider == null) {
                continuation.resume(null)
                return@suspendCancellableCoroutine
            }
            provider.getToken { token ->
                if (continuation.isActive) {
                    continuation.resume(token?.takeIf(String::isNotBlank))
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
