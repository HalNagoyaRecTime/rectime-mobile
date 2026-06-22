package com.rectime.mobile.core.network

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout

expect fun createHttpClient(): HttpClient

fun createAppHttpClient(): HttpClient = createHttpClient().config {
    install(HttpTimeout) {
        requestTimeoutMillis = 10_000
        connectTimeoutMillis = 5_000
    }
}
