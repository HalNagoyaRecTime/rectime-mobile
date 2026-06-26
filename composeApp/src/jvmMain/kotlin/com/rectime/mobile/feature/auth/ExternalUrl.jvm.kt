package com.rectime.mobile.feature.auth

import java.awt.Desktop
import java.net.URI

actual fun openExternalUrl(url: String): Boolean =
    runCatching {
        if (!Desktop.isDesktopSupported()) return false
        Desktop.getDesktop().browse(URI(url))
    }.isSuccess
