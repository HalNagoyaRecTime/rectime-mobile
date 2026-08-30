package com.rectime.mobile.feature.auth

// clear系がBooleanを返すのは、端末から消せたことを確認せずに
// ログアウト成功として扱うと、再起動で前のアカウントへ戻ってしまうため。
expect class AuthSessionStore() {
    suspend fun load(): AuthSession?
    suspend fun save(session: AuthSession)
    suspend fun clear(): Boolean
    suspend fun loadPendingAuth(): PendingAuth?
    suspend fun savePendingAuth(pending: PendingAuth)
    suspend fun clearPendingAuth(): Boolean
}

interface AuthSessionStorage {
    suspend fun load(): AuthSession?
    suspend fun save(session: AuthSession)
    suspend fun clear(): Boolean
    suspend fun loadPendingAuth(): PendingAuth?
    suspend fun savePendingAuth(pending: PendingAuth)
    suspend fun clearPendingAuth(): Boolean
}

class PlatformAuthSessionStorage(
    private val store: AuthSessionStore = AuthSessionStore(),
) : AuthSessionStorage {
    override suspend fun load(): AuthSession? = store.load()
    override suspend fun save(session: AuthSession) = store.save(session)
    override suspend fun clear(): Boolean = store.clear()
    override suspend fun loadPendingAuth(): PendingAuth? = store.loadPendingAuth()
    override suspend fun savePendingAuth(pending: PendingAuth) = store.savePendingAuth(pending)
    override suspend fun clearPendingAuth(): Boolean = store.clearPendingAuth()
}
