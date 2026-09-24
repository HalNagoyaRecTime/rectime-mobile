package com.rectime.mobile.feature.notifications

import java.util.prefs.Preferences
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

internal actual fun createFirebaseTokenRegistrationStore(): FirebaseTokenRegistrationStore =
    JvmFirebaseTokenRegistrationStore()

private class JvmFirebaseTokenRegistrationStore : FirebaseTokenRegistrationStore {
    private val preferences = Preferences.userRoot().node(PREFERENCES_PATH)

    override suspend fun load(): FirebaseTokenRegistrationState? {
        val raw = preferences.get(REGISTRATION_KEY, null) ?: return null
        return runCatching { Json.decodeFromString<FirebaseTokenRegistrationState>(raw) }
            .getOrElse {
                preferences.remove(REGISTRATION_KEY)
                null
            }
    }

    override suspend fun save(state: FirebaseTokenRegistrationState): Boolean =
        runCatching {
            preferences.put(REGISTRATION_KEY, Json.encodeToString(state))
            preferences.flush()
            true
        }.getOrDefault(false)

    override suspend fun clear(): Boolean = runCatching {
        preferences.remove(REGISTRATION_KEY)
        preferences.flush()
        true
    }.getOrDefault(false)

    private companion object {
        const val PREFERENCES_PATH = "com/rectime/mobile/push-registration"
        const val REGISTRATION_KEY = "firebase_token_registration_v1"
    }
}
