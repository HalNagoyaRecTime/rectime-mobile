package com.rectime.mobile.feature.auth

import java.util.prefs.Preferences

actual class AuthSessionStore {
    private val preferences = Preferences.userRoot().node("com/rectime/mobile/auth")

    actual suspend fun load(): AuthSession? {
        val value = preferences.get(KEY, null) ?: return null
        return decodeAuthSession(value)
    }

    actual suspend fun save(session: AuthSession) {
        preferences.put(KEY, encodeAuthSession(session))
    }

    actual suspend fun clear() {
        preferences.remove(KEY)
    }

    actual suspend fun loadPendingAuth(): PendingAuth? {
        val value = preferences.get(PENDING_KEY, null) ?: return null
        return decodePendingAuth(value)
    }

    actual suspend fun savePendingAuth(pending: PendingAuth) {
        preferences.put(PENDING_KEY, encodePendingAuth(pending))
    }

    actual suspend fun clearPendingAuth() {
        preferences.remove(PENDING_KEY)
    }

    private companion object {
        const val KEY = "session"
        const val PENDING_KEY = "pending_auth"
    }
}
