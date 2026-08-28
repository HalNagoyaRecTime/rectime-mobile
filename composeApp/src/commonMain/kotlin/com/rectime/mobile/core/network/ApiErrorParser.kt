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
            code = defaultApiErrorCode(status),
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

/**
 * Preserve the small amount of semantic information that is available even
 * when an endpoint has not returned the common error body yet.
 *
 * Feature-specific 404 meanings are filled in by the caller (for example,
 * the notifications UI treats NOT_FOUND as a missing notification), while
 * the parser remains independent of any feature.
 */
private fun defaultApiErrorCode(status: HttpStatusCode): String = when (status) {
    HttpStatusCode.Unauthorized -> "UNAUTHORIZED"
    HttpStatusCode.NotFound -> "NOT_FOUND"
    else -> UNKNOWN_API_ERROR_CODE
}
