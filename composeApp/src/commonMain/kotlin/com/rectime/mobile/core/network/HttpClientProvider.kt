package com.rectime.mobile.core.network

import com.rectime.mobile.core.config.apiBaseUrl
import com.rectime.mobile.feature.auth.SessionTokenHolder
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.http.HttpHeaders
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
internal val MobileAuthHeadersPlugin = createClientPlugin("MobileAuthHeaders") {
    onRequest { request, _ ->
        if (request.headers.contains(HttpHeaders.Authorization)) return@onRequest
        mobileAuthHeaders(request.url.toString())?.forEach { (name, value) ->
            request.headers.append(name, value)
        }
    }
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
    val normalizedBaseUrl = baseUrl.trimEnd('/')
    return url == normalizedBaseUrl || url.startsWith("$normalizedBaseUrl/")
}

internal val normalizedApiBaseUrl: String
    get() = apiBaseUrl.trimEnd('/')

/**
 * `url` がrectime-apiへのリクエストで、かつログイン済みの場合に付与すべき
 * ヘッダーを返す。Ktor(HttpClientProvider/App.kt)・Coil(UserAvatar.kt)双方の
 * 呼び出し元で同じ判定・同じヘッダー値を使うための唯一の定義箇所。
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
