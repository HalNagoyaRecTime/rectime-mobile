package com.rectime.mobile.core.config

internal fun resolveApiBaseUrl(rawValue: String?, isDebug: Boolean): String {
    val url = rawValue?.trim()?.trimEnd('/').orEmpty()

    require(url.isNotEmpty()) { "API_BASE_URL is not configured." }
    require(!url.contains("$(")) { "API_BASE_URL contains an unresolved build setting." }
    require(url.startsWith("http://") || url.startsWith("https://")) {
        "API_BASE_URL must use http or https."
    }

    if (!isDebug) {
        require(url.startsWith("https://")) { "Release builds require an HTTPS API_BASE_URL." }
        require(!url.isLocalDevelopmentUrl()) {
            "Release builds cannot use a local API_BASE_URL."
        }
    }

    return url
}

private fun String.isLocalDevelopmentUrl(): Boolean {
    val host = substringAfter("://").substringBefore('/').substringBefore(':').lowercase()
    return host == "localhost" ||
        host == "127.0.0.1" ||
        host == "0.0.0.0" ||
        host == "10.0.2.2" ||
        host == "::1"
}
