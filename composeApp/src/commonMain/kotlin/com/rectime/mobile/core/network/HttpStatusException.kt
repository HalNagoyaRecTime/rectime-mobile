package com.rectime.mobile.core.network

import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.JsonElement

class HttpStatusException(
    val status: HttpStatusCode,
    val code: String = UNKNOWN_API_ERROR_CODE,
    detail: String? = null,
    val details: JsonElement? = null,
) : Exception(detail ?: "HTTP ${status.value}")

const val UNKNOWN_API_ERROR_CODE = "UNKNOWN_API_ERROR"
