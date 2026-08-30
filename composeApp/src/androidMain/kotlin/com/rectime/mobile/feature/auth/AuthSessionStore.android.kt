package com.rectime.mobile.feature.auth

import android.content.Context
import com.rectime.mobile.core.platform.getPlatformContext
import com.rectime.mobile.core.security.KeystoreCipher
import com.rectime.mobile.core.security.SecureValueStore
import com.rectime.mobile.core.security.SharedPreferencesStringStore

private const val PREFS_NAME = "rectime_auth"
private const val KEY_ALIAS = "rectime_auth_storage_key"
private const val SESSION_KEY = "session_v1"
private const val PENDING_AUTH_KEY = "pending_auth_v1"
private const val LEGACY_SESSION_KEY = "session"
private const val LEGACY_PENDING_AUTH_KEY = "pending_auth"

actual class AuthSessionStore {

    actual suspend fun load(): AuthSession? =
        read(SESSION_KEY, LEGACY_SESSION_KEY, ::decodeAuthSession)

    actual suspend fun save(session: AuthSession) {
        secureStore()?.write(SESSION_KEY, LEGACY_SESSION_KEY, encodeAuthSession(session))
    }

    actual suspend fun clear(): Boolean =
        secureStore()?.remove(SESSION_KEY, LEGACY_SESSION_KEY) ?: false

    actual suspend fun loadPendingAuth(): PendingAuth? =
        read(PENDING_AUTH_KEY, LEGACY_PENDING_AUTH_KEY, ::decodePendingAuth)

    actual suspend fun savePendingAuth(pending: PendingAuth) {
        secureStore()?.write(PENDING_AUTH_KEY, LEGACY_PENDING_AUTH_KEY, encodePendingAuth(pending))
    }

    actual suspend fun clearPendingAuth(): Boolean =
        secureStore()?.remove(PENDING_AUTH_KEY, LEGACY_PENDING_AUTH_KEY) ?: false

    private fun <T> read(key: String, legacyKey: String, decode: (String) -> T?): T? {
        val store = secureStore() ?: return null
        val plaintext = store.read(key, legacyKey) ?: return null
        val value = decode(plaintext)
        if (value == null) store.remove(key, legacyKey)
        return value
    }

    private fun secureStore(): SecureValueStore? {
        val context = getPlatformContext() ?: return null
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return SecureValueStore(SharedPreferencesStringStore(prefs), KeystoreCipher(KEY_ALIAS))
    }
}
