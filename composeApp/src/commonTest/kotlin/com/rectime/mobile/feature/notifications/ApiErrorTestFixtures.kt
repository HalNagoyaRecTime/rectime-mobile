package com.rectime.mobile.feature.notifications

import com.rectime.mobile.core.network.HttpStatusException
import io.ktor.http.HttpStatusCode

internal fun notificationApiError(statusCode: Int): HttpStatusException =
    HttpStatusException(
        status = HttpStatusCode.fromValue(statusCode),
        code = when (statusCode) {
            401 -> "UNAUTHORIZED"
            404 -> "NOTIFICATION_NOT_FOUND"
            500 -> "INTERNAL_SERVER_ERROR"
            else -> "UNKNOWN_API_ERROR"
        },
    )
