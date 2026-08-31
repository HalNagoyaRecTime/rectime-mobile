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
 * 共通エラーレスポンスをまだ返せないエンドポイントに対しても、
 * HTTPステータスから取得できる最低限の意味情報を保持する。
 *
 * 404の機能固有の意味付けは呼び出し側で行う。例えば通知画面では、
 * NOT_FOUNDを通知が存在しない場合として扱う。これによりパーサーは
 * 特定機能に依存しない。
 */
private fun defaultApiErrorCode(status: HttpStatusCode): String = when (status) {
    HttpStatusCode.Unauthorized -> "UNAUTHORIZED"
    HttpStatusCode.NotFound -> "NOT_FOUND"
    else -> UNKNOWN_API_ERROR_CODE
}
