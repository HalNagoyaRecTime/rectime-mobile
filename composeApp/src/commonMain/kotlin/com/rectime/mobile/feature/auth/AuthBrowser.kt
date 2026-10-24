package com.rectime.mobile.feature.auth

import com.rectime.mobile.core.platform.normalizeExternalHttpsUrl

/**
 * Microsoftのログイン画面を開く。
 * iOSはアプリ内のシートで開き、結果をAuthDeepLinkHandlerへ渡す。
 * Android/Desktopは外部ブラウザで開き、ディープリンクやループバックで戻る。
 */
internal suspend fun openAuthBrowser(url: String): Boolean {
    val normalizedUrl = normalizeExternalHttpsUrl(url) ?: return false
    return openPlatformAuthBrowser(normalizedUrl)
}

internal expect suspend fun openPlatformAuthBrowser(url: String): Boolean
