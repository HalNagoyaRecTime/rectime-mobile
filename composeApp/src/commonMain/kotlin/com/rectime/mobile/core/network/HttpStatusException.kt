package com.rectime.mobile.core.network

import io.ktor.http.HttpStatusCode

class HttpStatusException(
    val status: HttpStatusCode,
    detail: String? = null,
) : Exception(detail ?: "HTTP ${status.value}")
