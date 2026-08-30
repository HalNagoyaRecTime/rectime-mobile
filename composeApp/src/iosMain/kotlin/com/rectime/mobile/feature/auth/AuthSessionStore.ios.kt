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

    actual suspend fun clear(): Boolean = removeAndFlush(KEY)

    actual suspend fun loadPendingAuth(): PendingAuth? {
        val value = defaults.stringForKey(PENDING_KEY) ?: return null
        return decodePendingAuth(value)
    }

    actual suspend fun savePendingAuth(pending: PendingAuth) {
        defaults.setObject(encodePendingAuth(pending), PENDING_KEY)
    }

    actual suspend fun clearPendingAuth(): Boolean = removeAndFlush(PENDING_KEY)

    // removeObjectForKey()の直後にstringForKey()を読んでもメモリ上の値が消えているだけで、
    // 永続化できたかは分からない。synchronize()の結果だけを削除成功の根拠にする。
    private fun removeAndFlush(key: String): Boolean {
        defaults.removeObjectForKey(key)
        return defaults.synchronize()
    }

    private companion object {
        const val KEY = "rectime_auth_session"
        const val PENDING_KEY = "rectime_auth_pending"
    }
}
