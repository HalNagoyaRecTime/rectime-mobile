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

    actual suspend fun clear(): Boolean = removeAndFlush(KEY)

    actual suspend fun loadPendingAuth(): PendingAuth? {
        val value = preferences.get(PENDING_KEY, null) ?: return null
        return decodePendingAuth(value)
    }

    actual suspend fun savePendingAuth(pending: PendingAuth) {
        preferences.put(PENDING_KEY, encodePendingAuth(pending))
    }

    actual suspend fun clearPendingAuth(): Boolean = removeAndFlush(PENDING_KEY)

    // remove()の直後にget()を読んでもメモリ上の値が消えているだけで、永続化できたかは
    // 分からない。flush()が成功したことだけを削除成功の根拠にする。
    private fun removeAndFlush(key: String): Boolean {
        preferences.remove(key)
        return runCatching { preferences.flush() }.isSuccess
    }

    private companion object {
        const val KEY = "session"
        const val PENDING_KEY = "pending_auth"
    }
}
