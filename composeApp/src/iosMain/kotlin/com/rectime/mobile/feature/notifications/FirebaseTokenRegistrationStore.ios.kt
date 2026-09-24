package com.rectime.mobile.feature.notifications

import com.rectime.mobile.feature.auth.KeychainStringStore
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

internal actual fun createFirebaseTokenRegistrationStore(): FirebaseTokenRegistrationStore =
    IosFirebaseTokenRegistrationStore()

private class IosFirebaseTokenRegistrationStore : FirebaseTokenRegistrationStore {
    private val secureStore = KeychainStringStore(SERVICE_NAME)

    override suspend fun load(): FirebaseTokenRegistrationState? {
        val raw = secureStore.read(REGISTRATION_KEY) ?: return null
        return runCatching { Json.decodeFromString<FirebaseTokenRegistrationState>(raw) }
            .getOrElse {
                secureStore.delete(REGISTRATION_KEY)
                null
            }
    }

    override suspend fun save(state: FirebaseTokenRegistrationState): Boolean =
        secureStore.write(REGISTRATION_KEY, Json.encodeToString(state))

    override suspend fun clear(): Boolean = secureStore.delete(REGISTRATION_KEY)

    private companion object {
        const val SERVICE_NAME = "com.rectime.mobile.push-registration"
        const val REGISTRATION_KEY = "firebase_token_registration_v1"
    }
}
