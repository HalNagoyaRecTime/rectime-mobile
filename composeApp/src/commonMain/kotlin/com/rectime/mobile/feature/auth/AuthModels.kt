package com.rectime.mobile.feature.auth

data class AuthUser(
    val id: String,
    val email: String,
    val displayName: String,
    val avatarUrl: String? = null,
    val avatarUpdatedAt: String? = null,
)

data class AuthSession(
    val accessToken: String,
    val refreshTokenId: String,
    val expiresIn: Long,
    val user: AuthUser,
)

data class PendingAuth(
    val state: String,
    val codeVerifier: String,
)

data class AuthUiState(
    val isLoading: Boolean = false,
    val session: AuthSession? = null,
    val pendingAuth: PendingAuth? = null,
    val message: String = "",
    val error: String? = null,
)
