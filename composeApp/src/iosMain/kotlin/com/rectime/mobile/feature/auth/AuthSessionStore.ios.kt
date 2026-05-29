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

    private companion object {
        const val KEY = "rectime_auth_session"
    }
}
