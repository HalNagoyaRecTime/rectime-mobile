package com.rectime.mobile.core.platform

import java.awt.Desktop
import java.net.URI
import com.rectime.mobile.feature.auth.LoopbackAuthCallbackReceiver
import com.rectime.mobile.feature.auth.startDesktopAuthCallbackReceiver

internal actual suspend fun openPlatformExternalUrl(url: String): Boolean {
    var callbackReceiver: LoopbackAuthCallbackReceiver? = null
    return runCatching {
        if (!Desktop.isDesktopSupported()) return false
        callbackReceiver = startDesktopAuthCallbackReceiver(url)
        Desktop.getDesktop().browse(URI(url))
    }.onFailure { callbackReceiver?.stop() }.isSuccess
}

internal actual suspend fun openPlatformInAppBrowser(url: String): Boolean =
    openPlatformExternalUrl(url)
