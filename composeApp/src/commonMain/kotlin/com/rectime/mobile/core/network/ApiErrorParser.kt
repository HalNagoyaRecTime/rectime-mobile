package com.rectime.mobile.core.network

import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json

private val apiErrorJson = Json {
    ignoreUnknownKeys = true
}

fun apiErrorException(
    status: HttpStatusCode,
    body: String,
    fallbackMessage: String? = null,
): HttpStatusException {
    val error = runCatching {
        apiErrorJson.decodeFromString<ApiErrorResponse>(body).error
    }.getOrNull()

    if (error == null || error.code.isBlank() || error.message.isBlank()) {
        return HttpStatusException(
            status = status,
            detail = fallbackMessage,
        )
    }

    return HttpStatusException(
        status = status,
        code = error.code,
        detail = error.message,
        details = error.details,
    )
}
