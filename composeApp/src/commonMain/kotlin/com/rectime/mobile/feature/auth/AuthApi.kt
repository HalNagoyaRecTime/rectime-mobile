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
        return readJsonString(body, "auth_url")
            ?: throw IllegalStateException("認証 URL のレスポンスが不正です")
    }

    suspend fun exchangeCode(code: String, state: String, codeVerifier: String): AuthSession {
        val response = client.post("$apiBaseUrl/api/v1/auth/microsoft/token") {
            header("X-Client-Type", "mobile")
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "code": "${escapeJson(code)}",
                  "state": "${escapeJson(state)}",
                  "code_verifier": "${escapeJson(codeVerifier)}"
                }
                """.trimIndent(),
            )
        }
        val body = response.bodyAsText()
        if (response.status.value !in 200..299) {
            throw IllegalStateException(readErrorMessage(body) ?: "トークン交換に失敗しました")
        }

        return AuthSession(
            accessToken = readJsonString(body, "access_token")
                ?: throw IllegalStateException("access_token がありません"),
            refreshTokenId = readJsonString(body, "refresh_token_id")
                ?: throw IllegalStateException("refresh_token_id がありません"),
            expiresIn = readJsonNumber(body, "expires_in") ?: 0L,
            user = AuthUser(
                id = readNestedJsonString(body, "user", "id") ?: "",
                email = readNestedJsonString(body, "user", "email") ?: "",
                displayName = readNestedJsonString(body, "user", "display_name") ?: "",
                avatarUrl = readNestedJsonString(body, "user", "avatar_url")?.let {
                    val base = if (it.startsWith("http")) it else "$apiBaseUrl$it"
                    val updatedAt = readNestedJsonString(body, "user", "avatar_updated_at")
                    if (!updatedAt.isNullOrBlank()) "$base?v=$updatedAt" else base
                },
                avatarUpdatedAt = readNestedJsonString(body, "user", "avatar_updated_at"),
            ),
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

        return AuthUser(
            id = readNestedJsonString(body, "user", "id") ?: "",
            email = readNestedJsonString(body, "user", "email") ?: "",
            displayName = readNestedJsonString(body, "user", "display_name") ?: "",
            avatarUrl = readNestedJsonString(body, "user", "avatar_url")?.let {
                val base = if (it.startsWith("http")) it else "$apiBaseUrl$it"
                val updatedAt = readNestedJsonString(body, "user", "avatar_updated_at")
                if (!updatedAt.isNullOrBlank()) "$base?v=$updatedAt" else base
            },
            avatarUpdatedAt = readNestedJsonString(body, "user", "avatar_updated_at"),
        )
    }

    suspend fun refresh(session: AuthSession): AuthSession {
        val response = client.post("$apiBaseUrl/api/v1/auth/refresh") {
            header("X-Client-Type", "mobile")
            contentType(ContentType.Application.Json)
            setBody("""{"refresh_token_id":"${escapeJson(session.refreshTokenId)}"}""")
        }
        val body = response.bodyAsText()
        if (response.status.value !in 200..299) {
            throw IllegalStateException(readErrorMessage(body) ?: "セッション更新に失敗しました")
        }

        return session.copy(
            accessToken = readJsonString(body, "access_token")
                ?: throw IllegalStateException("access_token がありません"),
            refreshTokenId = readJsonString(body, "refresh_token_id") ?: session.refreshTokenId,
            expiresIn = readJsonNumber(body, "expires_in") ?: session.expiresIn,
        )
    }

    suspend fun logout(session: AuthSession) {
        val response = client.post("$apiBaseUrl/api/v1/auth/logout") {
            header("X-Client-Type", "mobile")
            header(HttpHeaders.Authorization, "Bearer ${session.accessToken}")
            contentType(ContentType.Application.Json)
            setBody("""{"refresh_token_id":"${escapeJson(session.refreshTokenId)}"}""")
        }
        val body = response.bodyAsText()
        if (response.status.value !in 200..299) {
            throw IllegalStateException(readErrorMessage(body) ?: "ログアウトに失敗しました")
        }
    }
}

private fun readJsonString(json: String, key: String): String? {
    val match = Regex(""""${Regex.escape(key)}"\s*:\s*"((?:\\.|[^"\\])*)"""")
        .find(json) ?: return null
    return unescapeJson(match.groupValues[1])
}

private fun readJsonNumber(json: String, key: String): Long? {
    return Regex(""""${Regex.escape(key)}"\s*:\s*(\d+)"""")
        .find(json)
        ?.groupValues
        ?.get(1)
        ?.toLongOrNull()
}

private fun readNestedJsonString(json: String, objectKey: String, key: String): String? {
    val objectBody = readObjectBody(json, objectKey) ?: return null
    return readJsonString(objectBody, key)
}

private fun readErrorMessage(json: String): String? =
    readNestedJsonString(json, "error", "message") ?: readJsonString(json, "message")

private fun readObjectBody(json: String, key: String): String? {
    val keyMatch = Regex(""""${Regex.escape(key)}"\s*:\s*\{""").find(json) ?: return null
    var depth = 0
    var index = keyMatch.range.last
    val start = index + 1
    while (index < json.length) {
        when (json[index]) {
            '{' -> depth += 1
            '}' -> {
                depth -= 1
                if (depth == 0) {
                    return json.substring(start, index)
                }
            }
        }
        index += 1
    }
    return null
}

private fun escapeJson(value: String): String =
    buildString {
        value.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(char)
            }
        }
    }

private fun unescapeJson(value: String): String =
    buildString {
        var index = 0
        while (index < value.length) {
            val char = value[index]
            if (char == '\\' && index + 1 < value.length) {
                when (val next = value[index + 1]) {
                    '"' -> append('"')
                    '\\' -> append('\\')
                    '/' -> append('/')
                    'b' -> append('\b')
                    'f' -> append('\u000C')
                    'n' -> append('\n')
                    'r' -> append('\r')
                    't' -> append('\t')
                    else -> append(next)
                }
                index += 2
            } else {
                append(char)
                index += 1
            }
        }
    }
