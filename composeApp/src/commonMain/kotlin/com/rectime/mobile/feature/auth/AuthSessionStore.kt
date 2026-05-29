package com.rectime.mobile.feature.auth

expect class AuthSessionStore() {
    suspend fun load(): AuthSession?
    suspend fun save(session: AuthSession)
    suspend fun clear()
}
