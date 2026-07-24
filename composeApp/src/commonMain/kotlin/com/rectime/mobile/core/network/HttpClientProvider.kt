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
        val token = SessionTokenHolder.accessToken
        if (token != null && isApiUrl(url.buildString())) {
            header("X-Client-Type", "mobile")
            header(HttpHeaders.Authorization, "Bearer $token")
        }
    }
}

internal fun isApiUrl(url: String): Boolean =
    url == normalizedApiBaseUrl || url.startsWith("$normalizedApiBaseUrl/")

internal val normalizedApiBaseUrl: String =
    apiBaseUrl.trimEnd('/')
