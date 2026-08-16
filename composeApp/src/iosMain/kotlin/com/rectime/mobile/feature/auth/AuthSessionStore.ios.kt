package com.rectime.mobile.feature.auth

import platform.Foundation.NSUserDefaults
import platform.Foundation.NSBundle

actual class AuthSessionStore {
    private val defaults = NSUserDefaults.standardUserDefaults
    private val secureStore: SecureStringStore = KeychainStringStore(
        service = requireNotNull(NSBundle.mainBundle.bundleIdentifier),
    )

    actual suspend fun load(): AuthSession? {
        val value = runCatching { secureStore.read(SESSION_KEY) }.getOrNull()
            ?: migrateLegacySession()
            ?: return null
        return decodeAuthSession(value) ?: run {
            runCatching { secureStore.delete(SESSION_KEY) }
            null
        }
    }

    actual suspend fun save(session: AuthSession) {
        defaults.removeObjectForKey(LEGACY_SESSION_KEY)
        secureStore.write(SESSION_KEY, encodeAuthSession(session))
    }

    actual suspend fun clear(): Boolean = clearSecureAndLegacy(
        secureKey = SESSION_KEY,
        legacyKey = LEGACY_SESSION_KEY,
    )

    actual suspend fun loadPendingAuth(): PendingAuth? {
        val value = runCatching { secureStore.read(PENDING_AUTH_KEY) }.getOrNull()
            ?: migrateLegacyPendingAuth()
            ?: return null
        return decodePendingAuth(value) ?: run {
            runCatching { secureStore.delete(PENDING_AUTH_KEY) }
            null
        }
    }

    actual suspend fun savePendingAuth(pending: PendingAuth) {
        defaults.removeObjectForKey(LEGACY_PENDING_AUTH_KEY)
        secureStore.write(PENDING_AUTH_KEY, encodePendingAuth(pending))
    }

    actual suspend fun clearPendingAuth(): Boolean = clearSecureAndLegacy(
        secureKey = PENDING_AUTH_KEY,
        legacyKey = LEGACY_PENDING_AUTH_KEY,
    )

    private fun clearSecureAndLegacy(secureKey: String, legacyKey: String): Boolean {
        val secureDeleted = runCatching {
            secureStore.delete(secureKey)
            true
        }.getOrDefault(false)
        defaults.removeObjectForKey(legacyKey)
        return secureDeleted && defaults.synchronize()
    }

    private fun migrateLegacySession(): String? = migrateLegacyValue(
        legacyKey = LEGACY_SESSION_KEY,
        secureKey = SESSION_KEY,
        decoder = ::decodeAuthSession,
    )

    private fun migrateLegacyPendingAuth(): String? = migrateLegacyValue(
        legacyKey = LEGACY_PENDING_AUTH_KEY,
        secureKey = PENDING_AUTH_KEY,
        decoder = ::decodePendingAuth,
    )

    private fun <T> migrateLegacyValue(
        legacyKey: String,
        secureKey: String,
        decoder: (String) -> T?,
    ): String? {
        val value = defaults.stringForKey(legacyKey) ?: return null
        defaults.removeObjectForKey(legacyKey)
        if (decoder(value) == null) return null
        return runCatching {
            secureStore.write(secureKey, value)
            value
        }.getOrNull()
    }

    private companion object {
        const val SESSION_KEY = "auth_session"
        const val PENDING_AUTH_KEY = "pending_auth"
        const val LEGACY_SESSION_KEY = "rectime_auth_session"
        const val LEGACY_PENDING_AUTH_KEY = "rectime_auth_pending"
    }
}
