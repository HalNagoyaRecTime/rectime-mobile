package com.rectime.mobile.feature.auth

import android.content.Context
import com.rectime.mobile.core.platform.getPlatformContext

actual class AuthSessionStore {
    actual suspend fun load(): AuthSession? {
        val context = getPlatformContext() ?: return null
        val value = context
            .getSharedPreferences("rectime_auth", Context.MODE_PRIVATE)
            .getString("session", null)
            ?: return null
        return decodeAuthSession(value)
    }

    actual suspend fun save(session: AuthSession) {
        val context = getPlatformContext() ?: return
        context
            .getSharedPreferences("rectime_auth", Context.MODE_PRIVATE)
            .edit()
            .putString("session", encodeAuthSession(session))
            .apply()
    }

    actual suspend fun clear() {
        val context = getPlatformContext() ?: return
        context
            .getSharedPreferences("rectime_auth", Context.MODE_PRIVATE)
            .edit()
            .remove("session")
            .apply()
    }

    actual suspend fun loadPendingAuth(): PendingAuth? {
        val context = getPlatformContext() ?: return null
        val value = context
            .getSharedPreferences("rectime_auth", Context.MODE_PRIVATE)
            .getString("pending_auth", null)
            ?: return null
        return decodePendingAuth(value)
    }

    actual suspend fun savePendingAuth(pending: PendingAuth) {
        val context = getPlatformContext() ?: return
        context
            .getSharedPreferences("rectime_auth", Context.MODE_PRIVATE)
            .edit()
            .putString("pending_auth", encodePendingAuth(pending))
            .apply()
    }

    actual suspend fun clearPendingAuth() {
        val context = getPlatformContext() ?: return
        context
            .getSharedPreferences("rectime_auth", Context.MODE_PRIVATE)
            .edit()
            .remove("pending_auth")
            .apply()
    }
}
