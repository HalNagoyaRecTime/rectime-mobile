package com.rectime.mobile.feature.auth

import platform.Foundation.NSUserDefaults

actual class AuthSessionStore {
    private val defaults = NSUserDefaults.standardUserDefaults

    actual suspend fun load(): AuthSession? {
        val value = defaults.stringForKey(KEY) ?: return null
        return decodeAuthSession(value)
    }

    actual suspend fun save(session: AuthSession) {
        defaults.setObject(encodeAuthSession(session), KEY)
    }

    actual suspend fun clear() {
        defaults.removeObjectForKey(KEY)
    }

    actual suspend fun loadPendingAuth(): PendingAuth? {
        val value = defaults.stringForKey(PENDING_KEY) ?: return null
        return decodePendingAuth(value)
    }

    actual suspend fun savePendingAuth(pending: PendingAuth) {
        defaults.setObject(encodePendingAuth(pending), PENDING_KEY)
    }

    actual suspend fun clearPendingAuth() {
        defaults.removeObjectForKey(PENDING_KEY)
    }

    private companion object {
        const val KEY = "rectime_auth_session"
        const val PENDING_KEY = "rectime_auth_pending"
    }
}
