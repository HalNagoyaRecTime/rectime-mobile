package com.rectime.mobile.core.config

sealed interface ApiBaseUrlResult {
    data class Valid(val url: String) : ApiBaseUrlResult
    data class Invalid(val reason: String) : ApiBaseUrlResult
}

internal fun resolveApiBaseUrl(rawValue: String?, isDebug: Boolean): ApiBaseUrlResult {
    val url = rawValue?.trim()?.trimEnd('/').orEmpty()

    if (url.isEmpty()) return ApiBaseUrlResult.Invalid("API_BASE_URL is not configured.")
    if (url.contains("$(")) return ApiBaseUrlResult.Invalid("API_BASE_URL contains an unresolved build setting.")
    if (!url.startsWith("http://") && !url.startsWith("https://")) {
        return ApiBaseUrlResult.Invalid("API_BASE_URL must use http or https.")
    }
    if (url.apiHost().isEmpty()) return ApiBaseUrlResult.Invalid("API_BASE_URL must include a host.")

    if (!isDebug) {
        if (!url.startsWith("https://")) {
            return ApiBaseUrlResult.Invalid("Release builds require an HTTPS API_BASE_URL.")
        }
        if (url.isLocalDevelopmentUrl()) {
            return ApiBaseUrlResult.Invalid("Release builds cannot use a local API_BASE_URL.")
        }
    }

    return ApiBaseUrlResult.Valid(url)
}

private fun String.isLocalDevelopmentUrl(): Boolean {
    val host = apiHost()
    return host == "localhost" ||
        host == "127.0.0.1" ||
        host == "0.0.0.0" ||
        host == "10.0.2.2" ||
        host == "::1"
}

private fun String.apiHost(): String {
    val authority = substringAfter("://").substringBefore('/')
    return if (authority.startsWith("[")) {
        authority.substringAfter('[').substringBefore(']')
    } else {
        authority.substringBefore(':')
    }.lowercase()
}
