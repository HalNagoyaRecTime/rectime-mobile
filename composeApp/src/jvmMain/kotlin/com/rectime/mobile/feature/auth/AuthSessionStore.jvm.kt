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

    private companion object {
        const val KEY = "session"
    }
}
