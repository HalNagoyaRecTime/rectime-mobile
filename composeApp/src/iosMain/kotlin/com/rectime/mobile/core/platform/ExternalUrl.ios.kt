package com.rectime.mobile.core.platform

import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import kotlin.coroutines.resume

internal actual suspend fun openPlatformExternalUrl(url: String): Boolean {
    val nsUrl = NSURL.URLWithString(url) ?: return false
    return suspendCancellableCoroutine { continuation ->
        UIApplication.sharedApplication.openURL(
            url = nsUrl,
            options = emptyMap<Any?, Any?>(),
        ) { success ->
            if (continuation.isActive) continuation.resume(success)
        }
    }
}
