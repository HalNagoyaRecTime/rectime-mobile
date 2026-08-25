package com.rectime.mobile.feature.notifications

import com.rectime.mobile.feature.auth.AuthSessionStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

actual fun updatePushTokenRegistration(accessToken: String?) {
    IosPushTokenRegistrar.updateAccessToken(accessToken)
}


/** SwiftのMessagingDelegateとKMPの認証/API層を接続するiOS専用ブリッジ。 */
object IosPushTokenRegistrar {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val registrationMutex = Mutex()

    private var currentAccessToken: String? = null
    private var currentFcmToken: String? = null
    private var lastRegisteredPair: Pair<String, String>? = null

    fun updateAccessToken(accessToken: String?) {
        scope.launch {
            registrationMutex.withLock {
                currentAccessToken = accessToken?.takeIf(String::isNotBlank)
                if (currentAccessToken == null) lastRegisteredPair = null
                registerIfReady()
            }
        }
    }

    fun onTokenRefreshed(fcmToken: String?) {
        val token = fcmToken?.takeIf(String::isNotBlank) ?: return
        scope.launch {
            registrationMutex.withLock {
                currentFcmToken = token
                if (currentAccessToken.isNullOrBlank()) {
                    currentAccessToken = AuthSessionStore().load()?.accessToken
                }
                registerIfReady()
            }
        }
    }

    private suspend fun registerIfReady() {
        val accessToken = currentAccessToken?.takeIf(String::isNotBlank) ?: return
        val fcmToken = currentFcmToken?.takeIf(String::isNotBlank) ?: return
        val pair = accessToken to fcmToken
        if (lastRegisteredPair == pair) return

        val api = FirebaseTokenApi()
        try {
            api.register(
                fcmToken = fcmToken,
                platform = FirebasePlatform.Ios,
                accessToken = accessToken,
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
