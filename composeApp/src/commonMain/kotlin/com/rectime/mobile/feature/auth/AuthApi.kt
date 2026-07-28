package com.rectime.mobile.feature.auth

import com.rectime.mobile.core.config.apiBaseUrl
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy

class AuthApi(
    private val client: HttpClient = HttpClient(),
) {
    suspend fun requestAuthUrl(state: String, codeChallenge: String): String {
        val response = client.get("$apiBaseUrl/api/v1/auth/microsoft/login") {
            header("X-Client-Type", "mobile")
            header("X-State", state)
            header("X-PKCE-Code-Challenge", codeChallenge)
        }
        val body = response.bodyAsText()
        if (response.status.value !in 200..299) {
            throw IllegalStateException(readErrorMessage(body) ?: "認証 URL の取得に失敗しました")
        }
        return decodeBody<AuthUrlResponse>(body)?.authUrl
            ?: throw IllegalStateException("認証 URL のレスポンスが不正です")
    }

    suspend fun exchangeCode(code: String, state: String, codeVerifier: String): AuthSession {
        val response = client.post("$apiBaseUrl/api/v1/auth/microsoft/token") {
            header("X-Client-Type", "mobile")
            contentType(ContentType.Application.Json)
            setBody(
                json.encodeToString(
                    TokenExchangeRequest(code = code, state = state, codeVerifier = codeVerifier),
                ),
            )
        }
        val body = response.bodyAsText()
        if (response.status.value !in 200..299) {
            throw IllegalStateException(readErrorMessage(body) ?: "トークン交換に失敗しました")
        }

        val parsed = decodeBody<AuthSessionResponse>(body)
        return AuthSession(
            accessToken = parsed?.accessToken
                ?: throw IllegalStateException("access_token がありません"),
            refreshTokenId = parsed.refreshTokenId
                ?: throw IllegalStateException("refresh_token_id がありません"),
            expiresIn = parsed.expiresIn ?: 0L,
            user = (parsed.user ?: throw IllegalStateException("ユーザー情報がありません")).toAuthUser(),
        )
    }

    suspend fun currentUser(accessToken: String): AuthUser {
        val response = client.get("$apiBaseUrl/api/v1/auth/me") {
            header("X-Client-Type", "mobile")
            header(HttpHeaders.Authorization, "Bearer $accessToken")
        }
        val body = response.bodyAsText()
        if (response.status.value !in 200..299) {
            throw IllegalStateException(readErrorMessage(body) ?: "セッション確認に失敗しました")
        }

        val user = decodeBody<UserEnvelope>(body)?.user
            ?: throw IllegalStateException("ユーザー情報のレスポンスが不正です")
        return user.toAuthUser()
    }

    suspend fun refresh(session: AuthSession): AuthSession {
        val response = client.post("$apiBaseUrl/api/v1/auth/refresh") {
            header("X-Client-Type", "mobile")
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(RefreshRequest(refreshTokenId = session.refreshTokenId)))
        }
        val body = response.bodyAsText()
        if (response.status.value !in 200..299) {
            throw IllegalStateException(readErrorMessage(body) ?: "セッション更新に失敗しました")
        }

        val parsed = decodeBody<AuthSessionResponse>(body)
        return session.copy(
            accessToken = parsed?.accessToken
                ?: throw IllegalStateException("access_token がありません"),
            refreshTokenId = parsed.refreshTokenId ?: session.refreshTokenId,
            expiresIn = parsed.expiresIn ?: session.expiresIn,
        )
    }

    suspend fun logout(session: AuthSession) {
        val response = client.post("$apiBaseUrl/api/v1/auth/logout") {
            header("X-Client-Type", "mobile")
            header(HttpHeaders.Authorization, "Bearer ${session.accessToken}")
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(RefreshRequest(refreshTokenId = session.refreshTokenId)))
        }
        val body = response.bodyAsText()
        if (response.status.value !in 200..299) {
            throw IllegalStateException(readErrorMessage(body) ?: "ログアウトに失敗しました")
        }
    }

    fun close() {
        client.close()
    }
}

@OptIn(ExperimentalSerializationApi::class)
private val json = Json {
    ignoreUnknownKeys = true
    namingStrategy = JsonNamingStrategy.SnakeCase
}

private inline fun <reified T> decodeBody(body: String): T? =
    runCatching { json.decodeFromString<T>(body) }.getOrNull()

private fun readErrorMessage(body: String): String? =
    decodeBody<ApiErrorResponse>(body)?.let { it.error?.message ?: it.message }

private fun AuthUserResponse.toAuthUser(): AuthUser {
    return AuthUser(
        id = id,
        email = email,
        displayName = displayName,
        avatarUrl = avatarUrl?.let {
            val base = if (it.startsWith("http")) it else "$apiBaseUrl$it"
            if (!avatarUpdatedAt.isNullOrBlank()) "$base?v=$avatarUpdatedAt" else base
        },
        avatarUpdatedAt = avatarUpdatedAt,
        role = Role.fromCategories(isStudent = isStudent, isStaff = isStaff, isTeacher = isTeacher),
    )
}

@Serializable
private data class AuthUrlResponse(
    val authUrl: String? = null,
)

@Serializable
private data class TokenExchangeRequest(
    val code: String,
    val state: String,
    val codeVerifier: String,
)

@Serializable
private data class RefreshRequest(
    val refreshTokenId: String,
)

@Serializable
private data class AuthSessionResponse(
    val accessToken: String? = null,
    val refreshTokenId: String? = null,
    val expiresIn: Long? = null,
    val user: AuthUserResponse? = null,
)

@Serializable
private data class UserEnvelope(
    val user: AuthUserResponse? = null,
)

@Serializable
private data class AuthUserResponse(
    val id: String = "",
    val email: String = "",
    val displayName: String = "",
    val avatarUrl: String? = null,
    val avatarUpdatedAt: String? = null,
    val isStudent: Boolean = false,
    val isStaff: Boolean = false,
    val isTeacher: Boolean = false,
)

@Serializable
private data class ApiErrorResponse(
    val message: String? = null,
    val error: ApiErrorDetail? = null,
)

@Serializable
private data class ApiErrorDetail(
    val message: String? = null,
)
