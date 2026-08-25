package com.rectime.mobile.core.platform

import java.awt.Desktop
import java.net.URI

internal actual suspend fun openPlatformExternalUrl(url: String): Boolean =
    runCatching {
        if (!Desktop.isDesktopSupported()) return false
        Desktop.getDesktop().browse(URI(url))
    }.isSuccess
