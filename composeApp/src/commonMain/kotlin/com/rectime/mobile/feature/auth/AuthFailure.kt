package com.rectime.mobile.feature.auth

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class AuthApiException(
    val statusCode: Int,
    val errorCode: String? = null,
    message: String = "Auth API request failed: HTTP $statusCode",
) : IllegalStateException(message)

internal object AuthSessionInvalidationHandler {
    private val mutableEvents = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events = mutableEvents.asSharedFlow()

    fun notifyUnauthorized(accessToken: String) {
        mutableEvents.tryEmit(accessToken)
    }
}

internal const val AUTH_FAILED_MESSAGE = "認証できませんでした。"
internal const val AUTH_NETWORK_ERROR_MESSAGE =
    "通信に失敗しました。ネットワーク接続を確認して、もう一度お試しください。"
internal const val AUTH_EXPIRED_MESSAGE =
    "ログイン情報の有効期限が切れました。もう一度ログインしてください。"
internal const val AUTH_CANCELED_MESSAGE = "ログインをキャンセルしました。"

internal fun authErrorMessage(error: Throwable, debugDetailsEnabled: Boolean): String = when {
    error is AuthApiException -> debugAuthMessage(
        detail = "HTTP ${error.statusCode}${error.errorCode?.let { " / $it" }.orEmpty()}",
        debugDetailsEnabled = debugDetailsEnabled,
    )
    else -> if (debugDetailsEnabled) {
        "$AUTH_NETWORK_ERROR_MESSAGE (${error.safeTypeName()})"
    } else {
        AUTH_NETWORK_ERROR_MESSAGE
    }
}

internal fun debugAuthMessage(detail: String?, debugDetailsEnabled: Boolean): String =
    if (debugDetailsEnabled && !detail.isNullOrBlank()) "$AUTH_FAILED_MESSAGE ($detail)"
    else AUTH_FAILED_MESSAGE

private fun Throwable.safeTypeName(): String = this::class.simpleName ?: "NetworkError"
