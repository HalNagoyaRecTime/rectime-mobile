package com.rectime.mobile.feature.notifications

import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.rectime.mobile.feature.auth.AuthSession
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

actual fun updatePushTokenRegistration(session: AuthSession?) {
    AndroidPushTokenRegistrar.updateSession(session)
}

actual suspend fun unregisterPushToken(session: AuthSession) {
    AndroidPushTokenRegistrar.unregister(session)
}

internal object AndroidPushTokenRegistrar {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val registrationMutex = Mutex()

    @Volatile
    private var currentSession: AuthSession? = null
    @Volatile
    private var isLoggingOut = false
    private var lastRegisteredPair: Pair<String, String>? = null

    fun updateSession(session: AuthSession?) {
        currentSession = session
        if (session == null) {
            isLoggingOut = false
            lastRegisteredPair = null
            return
        }
        isLoggingOut = false

        scope.launch {
            runCatching {
                fetchFirebaseToken()
            }.onSuccess { fcmToken ->
                register(fcmToken, session)
            }.onFailure(::logFailure)
        }
    }

    fun onTokenRefreshed(fcmToken: String) {
        if (fcmToken.isBlank()) return

        scope.launch {
            val session = currentSession
                ?: AuthSessionStore().load()
                ?: return@launch
            register(fcmToken, session)
        }
    }

    suspend fun unregister(session: AuthSession) {
        registrationMutex.withLock {
            isLoggingOut = true
            currentSession = null
            lastRegisteredPair = null
            val api = FirebaseTokenApi()
            var failure: Throwable? = null
            try {
                try {
                    api.unregister(session.accessToken)
                } catch (error: Throwable) {
                    if (error is CancellationException) throw error
                    failure = error
                    logFailure(error)
                }
                try {
                    deleteFirebaseToken()
                } catch (error: Throwable) {
                    if (error is CancellationException) throw error
                    if (failure == null) failure = error
                    Log.w(TAG, "FCM token deletion failed", error)
                }
            } finally {
                api.close()
            }
            failure?.let { throw it }
        }
    }

    private suspend fun register(fcmToken: String, session: AuthSession) {
        registrationMutex.withLock {
            if (isLoggingOut || currentSession?.user?.id != session.user.id) return
            val pair = session.user.id to fcmToken
            if (lastRegisteredPair == pair) return
            val api = FirebaseTokenApi()
            runCatching {
                try {
                    api.register(fcmToken, FirebasePlatform.Android, session.accessToken)
                } finally {
                    api.close()
                }
            }.onSuccess {
                lastRegisteredPair = pair
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

    private suspend fun deleteFirebaseToken() =
        suspendCancellableCoroutine { continuation ->
            FirebaseMessaging.getInstance().deleteToken().addOnCompleteListener { task ->
                if (!continuation.isActive) return@addOnCompleteListener
                val error = task.exception
                if (error != null) continuation.resumeWithException(error)
                else continuation.resume(Unit)
            }
        }

    private const val TAG = "RectimeFCM"
}
