package com.rectime.mobile.feature.notifications

import com.rectime.mobile.feature.auth.AuthSession
import com.rectime.mobile.feature.auth.AuthSessionStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

actual fun updatePushTokenRegistration(session: AuthSession?) {
    IosPushTokenRegistrar.updateSession(session)
}

actual suspend fun unregisterPushToken(session: AuthSession) {
    IosPushTokenRegistrar.unregister(session)
}

/** SwiftのMessagingDelegateとKMPの認証/API層を接続するiOS専用ブリッジ。 */
object IosPushTokenRegistrar {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val registrationMutex = Mutex()

    private var currentSession: AuthSession? = null
    private var currentFcmToken: String? = null
    private var lastRegisteredPair: Pair<String, String>? = null
    private var isLoggingOut = false

    fun updateSession(session: AuthSession?) {
        scope.launch {
            registrationMutex.withLock {
                currentSession = session
                isLoggingOut = false
                if (session == null) lastRegisteredPair = null
                registerIfReady()
            }
        }
    }

    fun onTokenRefreshed(fcmToken: String?) {
        val token = fcmToken?.takeIf(String::isNotBlank) ?: return
        scope.launch {
            registrationMutex.withLock {
                currentFcmToken = token
                if (currentSession == null) {
                    currentSession = AuthSessionStore().load()
                }
                registerIfReady()
            }
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
                }
                try {
                    IosFirebaseTokenBridge.requestDeleteToken()
                } catch (error: Throwable) {
                    if (error is CancellationException) throw error
                    if (failure == null) failure = error
                }
            } finally {
                api.close()
            }
            failure?.let { throw it }
        }
    }

    private suspend fun registerIfReady() {
        if (isLoggingOut) return
        val session = currentSession ?: return
        val fcmToken = currentFcmToken?.takeIf(String::isNotBlank) ?: return
        val pair = session.user.id to fcmToken
        if (lastRegisteredPair == pair) return

        val api = FirebaseTokenApi()
        try {
            api.register(
                fcmToken = fcmToken,
                platform = FirebasePlatform.Ios,
                accessToken = session.accessToken,
            )
            lastRegisteredPair = pair
            println("[IosPushTokenRegistrar] FCM token registration completed")
        } catch (error: CancellationException) {
            throw error
        } catch (error: FirebaseTokenRegistrationException) {
            println("[IosPushTokenRegistrar] FCM token registration failed: HTTP ${error.statusCode}")
        } catch (error: Throwable) {
            println("[IosPushTokenRegistrar] FCM token registration failed: ${error::class.simpleName}")
        } finally {
            api.close()
        }
    }
}

/** Swift側のFirebase Messaging.deleteToken()を呼ぶための軽量ブリッジ。 */
object IosFirebaseTokenBridge {
    private var deleteHandler: (() -> Unit)? = null

    fun setDeleteHandler(handler: (() -> Unit)?) {
        deleteHandler = handler
    }

    fun requestDeleteToken() {
        deleteHandler?.invoke()
    }
}
