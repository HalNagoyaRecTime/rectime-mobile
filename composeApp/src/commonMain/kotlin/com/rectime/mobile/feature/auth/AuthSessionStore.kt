package com.rectime.mobile.feature.auth

expect class AuthSessionStore() {
    suspend fun load(): AuthSession?
    suspend fun save(session: AuthSession)
    suspend fun clear()
    suspend fun loadPendingAuth(): PendingAuth?
    suspend fun savePendingAuth(pending: PendingAuth)
    suspend fun clearPendingAuth()
}

interface AuthSessionStorage {
    suspend fun load(): AuthSession?
    suspend fun save(session: AuthSession)
    suspend fun clear()
    suspend fun loadPendingAuth(): PendingAuth?
    suspend fun savePendingAuth(pending: PendingAuth)
    suspend fun clearPendingAuth()
}

class PlatformAuthSessionStorage(
    private val store: AuthSessionStore = AuthSessionStore(),
) : AuthSessionStorage {
    override suspend fun load(): AuthSession? = store.load()
    override suspend fun save(session: AuthSession) = store.save(session)
    override suspend fun clear() = store.clear()
    override suspend fun loadPendingAuth(): PendingAuth? = store.loadPendingAuth()
    override suspend fun savePendingAuth(pending: PendingAuth) = store.savePendingAuth(pending)
    override suspend fun clearPendingAuth() = store.clearPendingAuth()
}
