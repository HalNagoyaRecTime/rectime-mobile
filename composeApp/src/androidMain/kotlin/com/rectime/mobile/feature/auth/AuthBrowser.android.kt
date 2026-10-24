package com.rectime.mobile.feature.auth

import com.rectime.mobile.core.platform.openPlatformExternalUrl

internal actual suspend fun openPlatformAuthBrowser(url: String): Boolean = openPlatformExternalUrl(url)
