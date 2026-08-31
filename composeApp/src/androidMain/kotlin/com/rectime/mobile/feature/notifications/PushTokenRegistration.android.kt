package com.rectime.mobile.feature.notifications

import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.rectime.mobile.feature.auth.AuthSessionStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

actual fun updatePushTokenRegistration(accessToken: String?) {
    AndroidPushTokenRegistrar.updateAccessToken(accessToken)
}

internal object AndroidPushTokenRegistrar {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val registrationMutex = Mutex()

    @Volatile
    private var currentAccessToken: String? = null

    fun updateAccessToken(accessToken: String?) {
        currentAccessToken = accessToken
        if (accessToken.isNullOrBlank()) return

        scope.launch {
            runCatching {
                fetchFirebaseToken()
            }.onSuccess { fcmToken ->
                register(fcmToken, accessToken)
            }.onFailure(::logFailure)
        }
    }

    fun onTokenRefreshed(fcmToken: String) {
        if (fcmToken.isBlank()) return

        scope.launch {
            val accessToken = currentAccessToken
                ?: AuthSessionStore().load()?.accessToken
                ?: return@launch
            register(fcmToken, accessToken)
        }
    }

    private suspend fun register(fcmToken: String, accessToken: String) {
        registrationMutex.withLock {
            val api = FirebaseTokenApi()
            runCatching {
                try {
                    api.register(fcmToken, FirebasePlatform.Android, accessToken)
                } finally {
                    api.close()
                }
            }.onSuccess {
                Log.i(TAG, "FCM token registration completed")
            }.onFailure(::logFailure)
        }
    }

    private fun logFailure(error: Throwable) {
        if (error is CancellationException) throw error
        when (error) {
            is FirebaseTokenRegistrationException ->
                Log.w(TAG, "FCM token registration failed: HTTP ${error.statusCode}")
            else ->
                Log.w(TAG, "FCM token registration failed", error)
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
