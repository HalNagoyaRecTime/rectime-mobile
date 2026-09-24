package com.rectime.mobile.feature.notifications

import android.content.Context
import com.rectime.mobile.core.platform.getPlatformContext
import com.rectime.mobile.core.security.KeystoreCipher
import com.rectime.mobile.core.security.SecureValueStore
import com.rectime.mobile.core.security.SharedPreferencesStringStore
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

internal actual fun createFirebaseTokenRegistrationStore(): FirebaseTokenRegistrationStore =
    AndroidFirebaseTokenRegistrationStore()

private class AndroidFirebaseTokenRegistrationStore : FirebaseTokenRegistrationStore {
    override suspend fun load(): FirebaseTokenRegistrationState? {
        val raw = secureStore()?.read(REGISTRATION_KEY, LEGACY_REGISTRATION_KEY) ?: return null
        return runCatching { Json.decodeFromString<FirebaseTokenRegistrationState>(raw) }
            .getOrElse {
                secureStore()?.remove(REGISTRATION_KEY, LEGACY_REGISTRATION_KEY)
                null
            }
    }

    override suspend fun save(state: FirebaseTokenRegistrationState): Boolean =
        secureStore()?.write(
            REGISTRATION_KEY,
            LEGACY_REGISTRATION_KEY,
            Json.encodeToString(state),
        ) ?: false

    override suspend fun clear(): Boolean =
        secureStore()?.remove(REGISTRATION_KEY, LEGACY_REGISTRATION_KEY) ?: false

    private fun secureStore(): SecureValueStore? {
        val context = getPlatformContext() ?: return null
        val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        return SecureValueStore(SharedPreferencesStringStore(preferences), KeystoreCipher(KEY_ALIAS))
    }

    private companion object {
        const val PREFERENCES_NAME = "rectime_push_registration"
        const val KEY_ALIAS = "rectime_push_registration_key"
        const val REGISTRATION_KEY = "registration_v1"
        const val LEGACY_REGISTRATION_KEY = "registration"
    }
}
