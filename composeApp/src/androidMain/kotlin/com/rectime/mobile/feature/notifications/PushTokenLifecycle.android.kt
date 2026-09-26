package com.rectime.mobile.feature.notifications

import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.rectime.mobile.core.network.HttpStatusException
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

actual fun platformPushTokenLifecycle(): PushTokenLifecycle = AndroidPushTokenLifecycle

internal object AndroidPushTokenLifecycle : PushTokenLifecycle {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val manager = PushTokenLifecycleManager(
        platform = FirebasePlatform.Android,
        scope = scope,
        register = { fcmToken, platform, accessToken ->
            withApi { api -> api.register(fcmToken, platform, accessToken) }
        },
        currentFcmToken = { fetchFirebaseToken() },
        deleteMessagingToken = ::deleteMessagingToken,
        // FirebaseがbackgroundでonNewTokenだけを届けてprocessを起動する場合、memory Sessionが無いため保存値を復元する。
        restoreSession = { AuthSessionStore().load() },
        onFailure = ::logFailure,
    )

    override fun updateSession(session: AuthSession?) = manager.updateSession(session)

    override fun onTokenRefreshed(fcmToken: String) = manager.onTokenRefreshed(fcmToken)

    override fun beginLogout(session: AuthSession?) = manager.beginLogout(session)

    override suspend fun logout(
        session: AuthSession?,
        remoteLogout: suspend (String?) -> Unit,
    ) = manager.logout(session, remoteLogout)

    private suspend fun deleteMessagingToken() = suspendCancellableCoroutine { continuation ->
        FirebaseMessaging.getInstance().deleteToken().addOnCompleteListener { task ->
            if (!continuation.isActive) return@addOnCompleteListener
            val error = task.exception
            when {
                error != null -> continuation.resumeWithException(error)
                task.isSuccessful -> continuation.resume(true)
                else -> continuation.resumeWithException(
                    IllegalStateException("FCMトークンを削除できませんでした"),
                )
            }
        }
    }

    private suspend fun fetchFirebaseToken(): String? = suspendCancellableCoroutine { continuation ->
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!continuation.isActive) return@addOnCompleteListener
            val error = task.exception
            when {
                error != null -> continuation.resumeWithException(error)
                task.isSuccessful -> continuation.resume(task.result)
                else -> continuation.resumeWithException(
                    IllegalStateException("FCMトークンを取得できませんでした"),
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
        when (error) {
            is HttpStatusException ->
                Log.w(TAG, "FCM token lifecycle failed: HTTP ${error.status.value} (${error.code})")
            else -> Log.w(TAG, "FCM token lifecycle failed")
        }
    }

    private const val TAG = "RectimeFCM"
}
