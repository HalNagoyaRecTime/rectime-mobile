package com.rectime.mobile.core.network

import com.rectime.mobile.core.config.apiBaseUrl
import com.rectime.mobile.feature.auth.SessionTokenHolder
import com.rectime.mobile.feature.auth.AuthSessionInvalidationHandler
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.api.Send
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

expect fun createHttpClient(): HttpClient

// defaultRequest{} のurlは、per-callのURLがマージされる前の空のビルダーを指しており
// 実際のリクエスト先を反映しない(常に isApiUrl=false になり、ヘッダーが一切付与されない)。
// per-call URLが確定した後に発火するonRequestフックを使う必要がある。
//
// 呼び出し元が既にAuthorizationヘッダーをセット済みの場合は上書きしない。
// SessionTokenHolder(現在のログインセッション用のグローバル状態)と異なる
// トークンをリクエスト単位で使いたいケース(FirebaseTokenApi.register()など)
// を、グローバル状態を書き換えずに実現できるようにするため。
internal class MobileAuthHeadersConfig {
    var baseUrl: String = apiBaseUrl
    var refreshToken: suspend (String) -> String? = AuthSessionInvalidationHandler::refreshToken
}

internal val MobileAuthHeadersPlugin = createClientPlugin(
    "MobileAuthHeaders",
    ::MobileAuthHeadersConfig,
) {
    val baseUrl = pluginConfig.baseUrl
    val refreshToken = pluginConfig.refreshToken
    onRequest { request, _ ->
        if (request.headers.contains(HttpHeaders.Authorization)) return@onRequest
        val token = SessionTokenHolder.accessToken?.takeIf(String::isNotBlank) ?: return@onRequest
        mobileAuthHeaders(request.url.toString(), token, baseUrl)?.forEach { (name, value) ->
            request.headers.append(name, value)
        }
    }
    on(Send) { request ->
        val targetsApi = isApiUrl(request.url.toString(), baseUrl)
        val originalCall = proceed(request)
        val url = originalCall.request.url.toString()
        val requestToken = originalCall.request.headers[HttpHeaders.Authorization]
            ?.takeIf { it.startsWith("Bearer ") }
            ?.removePrefix("Bearer ")
            ?.takeIf(String::isNotBlank)
        // 別ホストや認証APIには更新・再試行を適用しない。
        if (originalCall.response.status.value != 401 || requestToken == null ||
            !targetsApi || !isApiUrl(url, baseUrl) || isAuthApiPath(url, baseUrl)
        ) return@on originalCall

        val refreshed = refreshToken(requestToken)
            ?.takeIf { it.isNotBlank() && it != requestToken }
            ?: return@on originalCall
        request.headers.remove(HttpHeaders.Authorization)
        request.headers.append(HttpHeaders.Authorization, "Bearer $refreshed")
        // 再試行した結果が401でも、元のリクエストにつき一度だけ。
        proceed(request)
    }
}

internal fun isAuthApiPath(url: String): Boolean =
    isAuthApiPath(url, apiBaseUrl)

internal fun isAuthApiPath(url: String, baseUrl: String): Boolean {
    if (!isApiUrl(url, baseUrl)) return false
    val path = runCatching { Url(url).encodedPath }.getOrNull() ?: return false
    return path.startsWith("/api/v1/auth/")
}

fun createAppHttpClient(): HttpClient = createHttpClient().config {
    install(HttpTimeout) {
        requestTimeoutMillis = 10_000
        connectTimeoutMillis = 5_000
    }
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
        })
    }
    install(MobileAuthHeadersPlugin)
}

internal fun isApiUrl(url: String): Boolean = isApiUrl(url, apiBaseUrl)

internal fun isApiUrl(url: String, baseUrl: String): Boolean {
    val target = runCatching { Url(url) }.getOrNull() ?: return false
    val base = runCatching { Url(baseUrl) }.getOrNull() ?: return false
    if (!target.protocol.name.equals(base.protocol.name, ignoreCase = true)) return false
    if (!target.host.equals(base.host, ignoreCase = true)) return false
    if (target.port != base.port) return false

    val basePath = base.encodedPath.trimEnd('/')
    return basePath.isEmpty() ||
        target.encodedPath == basePath ||
        target.encodedPath.startsWith("$basePath/")
}

internal val normalizedApiBaseUrl: String
    get() = apiBaseUrl.trimEnd('/')

/**
 * `url` がrectime-apiへのリクエストで、かつログイン済みの場合に付与すべき
 * ヘッダーを返す。Ktor(HttpClientProvider)・Coil(App.kt)双方の呼び出し元で
 * 同じ判定・同じヘッダー値を使うための唯一の定義箇所。
 */
internal fun mobileAuthHeaders(url: String): Map<String, String>? {
    val token = SessionTokenHolder.accessToken ?: return null
    return mobileAuthHeaders(url, token)
}

/**
 * `token` を明示的に指定するオーバーロード。SessionTokenHolder(現在のログイン
 * セッション)とは異なるトークンでリクエストしたい場合に、グローバル状態を
 * 書き換えずに使う。
 */
internal fun mobileAuthHeaders(url: String, token: String): Map<String, String>? {
    return mobileAuthHeaders(url, token, apiBaseUrl)
}

internal fun mobileAuthHeaders(url: String, token: String, baseUrl: String): Map<String, String>? {
    if (!isApiUrl(url, baseUrl)) return null
    return mapOf(
        "X-Client-Type" to "mobile",
        HttpHeaders.Authorization to "Bearer $token",
    )
}
