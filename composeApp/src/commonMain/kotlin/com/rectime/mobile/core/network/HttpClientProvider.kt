package com.rectime.mobile.core.network

import com.rectime.mobile.core.config.apiBaseUrl
import com.rectime.mobile.feature.auth.SessionTokenHolder
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

expect fun createHttpClient(): HttpClient

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
    defaultRequest {
        mobileAuthHeaders(url.buildString())?.forEach { (name, value) -> header(name, value) }
    }
}

internal fun isApiUrl(url: String): Boolean =
    url == normalizedApiBaseUrl || url.startsWith("$normalizedApiBaseUrl/")

internal val normalizedApiBaseUrl: String =
    apiBaseUrl.trimEnd('/')

/**
 * `url` がrectime-apiへのリクエストで、かつログイン済みの場合に付与すべき
 * ヘッダーを返す。Ktor(HttpClientProvider/App.kt)・Coil(UserAvatar.kt)双方の
 * 呼び出し元で同じ判定・同じヘッダー値を使うための唯一の定義箇所。
 */
internal fun mobileAuthHeaders(url: String): Map<String, String>? {
    val token = SessionTokenHolder.accessToken ?: return null
    if (!isApiUrl(url)) return null
    return mapOf(
        "X-Client-Type" to "mobile",
        HttpHeaders.Authorization to "Bearer $token",
    )
}
