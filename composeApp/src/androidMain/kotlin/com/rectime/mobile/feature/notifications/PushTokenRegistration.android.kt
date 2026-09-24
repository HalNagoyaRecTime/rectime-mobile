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

actual fun updatePushTokenRegistration(accessToken: String?) {
    val session = FirebaseTokenRegistrationContext.current()
        ?.takeIf { it.accessToken == accessToken }
    AndroidPushTokenRegistrar.updateSession(session)
}

internal object AndroidPushTokenRegistrar : FirebaseTokenLogoutHandler {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val coordinator = FirebaseTokenRegistrationCoordinator(
        platform = FirebasePlatform.Android,
        store = createFirebaseTokenRegistrationStore(),
        scope = scope,
        register = { fcmToken, platform, accessToken ->
            withApi { api -> api.register(fcmToken, platform, accessToken) }
        },
        delete = { id, accessToken ->
            withApi { api -> api.delete(id, accessToken) }
        },
        deleteMessagingToken = ::deleteMessagingToken,
        onRegistrationFailure = ::logFailure,
    )

    init {
        FirebaseTokenLogoutHandlerRegistry.install(this)
    }

    fun updateSession(session: AuthSession?) {
        coordinator.updateSession(session)
        if (session != null) {
            scope.launch {
                runCatching { fetchFirebaseToken() }
                    .onSuccess(coordinator::onTokenRefreshed)
                    .onFailure(::logFailure)
            }
        }
    }

    fun onTokenRefreshed(fcmToken: String) {
        coordinator.onTokenRefreshed(fcmToken) {
            AuthSessionStore().load()
        }
    }

    override fun stopRegistration(session: AuthSession?) {
        coordinator.stopRegistration(session)
    }

    override suspend fun unregister(session: AuthSession?) {
        if (session != null) coordinator.unregister(session)
    }

    private suspend fun deleteMessagingToken() =
        suspendCancellableCoroutine { continuation ->
            FirebaseMessaging.getInstance().deleteToken().addOnCompleteListener { task ->
                if (!continuation.isActive) return@addOnCompleteListener
                val error = task.exception
                when {
                    error != null -> continuation.resumeWithException(error)
                    task.isSuccessful -> continuation.resume(Unit)
                    else -> continuation.resumeWithException(
                        IllegalStateException("Firebase token deletion was not successful"),
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
                Log.w(
                    TAG,
                    "FCM token registration failed: HTTP ${error.status.value} (${error.code})",
                )
            else -> Log.w(TAG, "FCM token registration failed")
        }
    }

    private suspend fun fetchFirebaseToken(): String =
        suspendCancellableCoroutine { continuation ->
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (!continuation.isActive) return@addOnCompleteListener
                val error = task.exception
                when {
                    error != null -> continuation.resumeWithException(error)
                    task.isSuccessful -> continuation.resume(task.result)
                    else -> continuation.resumeWithException(
                        IllegalStateException("Firebase token request was not successful"),
                    )
                }
            }
        }

    private const val TAG = "RectimeFCM"
}
