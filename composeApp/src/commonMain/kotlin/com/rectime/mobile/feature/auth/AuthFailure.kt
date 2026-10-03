package com.rectime.mobile.feature.auth

import kotlinx.serialization.json.JsonElement

class AuthApiException(
    val statusCode: Int,
    val errorCode: String? = null,
    message: String = "Auth API request failed: HTTP $statusCode",
    val details: JsonElement? = null,
) : IllegalStateException(message)

/** 認証付き通信から、現在の認証管理へ更新完了を待つ窓口。 */
internal object AuthSessionInvalidationHandler {
    private var owner: Any? = null
    private var refresh: (suspend (String) -> String?)? = null

    fun register(owner: Any, refresh: suspend (String) -> String?) {
        this.owner = owner
        this.refresh = refresh
    }

    fun unregister(owner: Any) {
        if (this.owner !== owner) return
        this.owner = null
        refresh = null
    }

    suspend fun refreshToken(accessToken: String): String? {
        val currentOwner = owner
        val handler = refresh ?: return null
        val token = handler(accessToken)
        return token.takeIf { owner === currentOwner }
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
