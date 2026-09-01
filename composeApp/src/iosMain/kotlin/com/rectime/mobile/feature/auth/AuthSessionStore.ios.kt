package com.rectime.mobile.feature.auth

import platform.Foundation.NSUserDefaults

actual class AuthSessionStore {
    private val defaults = NSUserDefaults.standardUserDefaults
    private val secureStore: SecureStringStore = KeychainStringStore(
        service = KEYCHAIN_SERVICE,
    )

    actual suspend fun load(): AuthSession? {
        if (!prepareSecureStoreForCurrentInstall()) return null
        val value = runCatching { secureStore.read(SESSION_KEY) }.getOrNull()
            ?: migrateLegacySession()
            ?: return null
        return decodeAuthSession(value) ?: run {
            runCatching { secureStore.delete(SESSION_KEY) }
            null
        }
    }

    actual suspend fun save(session: AuthSession) {
        check(prepareSecureStoreForCurrentInstall()) { "認証情報の保存領域を初期化できませんでした" }
        check(secureStore.write(SESSION_KEY, encodeAuthSession(session))) {
            "認証情報を保存できませんでした"
        }
        defaults.removeObjectForKey(LEGACY_SESSION_KEY)
    }

    actual suspend fun clear(): Boolean = clearSecureAndLegacy(
        secureKey = SESSION_KEY,
        legacyKey = LEGACY_SESSION_KEY,
    )

    actual suspend fun loadPendingAuth(): PendingAuth? {
        if (!prepareSecureStoreForCurrentInstall()) return null
        val value = runCatching { secureStore.read(PENDING_AUTH_KEY) }.getOrNull()
            ?: migrateLegacyPendingAuth()
            ?: return null
        return decodePendingAuth(value) ?: run {
            runCatching { secureStore.delete(PENDING_AUTH_KEY) }
            null
        }
    }

    actual suspend fun savePendingAuth(pending: PendingAuth) {
        check(prepareSecureStoreForCurrentInstall()) { "認証情報の保存領域を初期化できませんでした" }
        check(secureStore.write(PENDING_AUTH_KEY, encodePendingAuth(pending))) {
            "認証情報を保存できませんでした"
        }
        defaults.removeObjectForKey(LEGACY_PENDING_AUTH_KEY)
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
        if (decoder(value) == null) {
            defaults.removeObjectForKey(legacyKey)
            return null
        }
        if (!secureStore.write(secureKey, value)) return value
        defaults.removeObjectForKey(legacyKey)
        return value
    }

    private fun prepareSecureStoreForCurrentInstall(): Boolean {
        if (defaults.boolForKey(INSTALL_MARKER_KEY)) return true

        val cleared = secureStore.delete(SESSION_KEY) and secureStore.delete(PENDING_AUTH_KEY)
        if (!cleared) return false

        defaults.setBool(true, forKey = INSTALL_MARKER_KEY)
        return true
    }

    private companion object {
        const val KEYCHAIN_SERVICE = "com.rectime.mobile.auth"
        const val SESSION_KEY = "auth_session"
        const val PENDING_AUTH_KEY = "pending_auth"
        const val LEGACY_SESSION_KEY = "rectime_auth_session"
        const val LEGACY_PENDING_AUTH_KEY = "rectime_auth_pending"
        const val INSTALL_MARKER_KEY = "rectime_auth_install_initialized"
    }
}
