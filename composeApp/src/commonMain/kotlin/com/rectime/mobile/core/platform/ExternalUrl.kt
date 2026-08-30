package com.rectime.mobile.core.platform

import io.ktor.http.URLProtocol
import io.ktor.http.Url

/**
 * Opens a public HTTPS URL outside the app.
 *
 * URL validation lives in common code so every target applies the same policy.
 * Platform implementations must not log the URL because it may contain query
 * parameters from an authentication flow.
 */
suspend fun openExternalUrl(url: String): Boolean {
    val normalizedUrl = normalizeExternalHttpsUrl(url) ?: return false
    return openPlatformExternalUrl(normalizedUrl)
}

internal fun isValidExternalHttpsUrl(url: String): Boolean = normalizeExternalHttpsUrl(url) != null

internal fun normalizeExternalHttpsUrl(url: String): String? {
    if (!url.startsWith("https://")) return null
    if (url.any { it.isWhitespace() || it.isISOControl() }) return null
    if (url.any { it in "\"<>\\^`{|}" }) return null
    val authority = url.removePrefix("https://").substringBefore('/').substringBefore('?').substringBefore('#')
    if (authority.isBlank()) return null
    val parsed = runCatching { Url(url) }.getOrNull() ?: return null
    if (parsed.protocol != URLProtocol.HTTPS || parsed.host.isBlank()) return null
    return parsed.toString()
}

internal expect suspend fun openPlatformExternalUrl(url: String): Boolean
