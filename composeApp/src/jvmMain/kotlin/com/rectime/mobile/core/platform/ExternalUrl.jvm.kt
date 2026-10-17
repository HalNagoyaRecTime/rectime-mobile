package com.rectime.mobile.core.platform

import java.awt.Desktop
import java.net.URI

internal actual suspend fun openPlatformExternalUrl(url: String): Boolean {
    return runCatching {
        if (!Desktop.isDesktopSupported()) return false
        Desktop.getDesktop().browse(URI(url))
    }.isSuccess
}

internal actual suspend fun openPlatformInAppBrowser(url: String): Boolean =
    openPlatformExternalUrl(url)
