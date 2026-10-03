package com.rectime.mobile.core.platform

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.SafariServices.SFSafariViewController
import platform.UIKit.UISceneActivationStateForegroundActive
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.UIKit.UIModalPresentationPageSheet
import platform.UIKit.UIModalTransitionStyleCoverVertical
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

internal actual suspend fun openPlatformInAppBrowser(url: String): Boolean = withContext(Dispatchers.Main) {
    val nsUrl = NSURL.URLWithString(url) ?: return@withContext false
    val window = UIApplication.sharedApplication.connectedScenes
        .filterIsInstance<UIWindowScene>()
        .filter { it.activationState == UISceneActivationStateForegroundActive }
        .flatMap { it.windows.filterIsInstance<UIWindow>() }
        .firstOrNull { it.isKeyWindow() }
        ?: return@withContext false
    var presenter = window.rootViewController ?: return@withContext false
    while (presenter.presentedViewController != null) {
        presenter = presenter.presentedViewController ?: break
    }
    if (presenter is SFSafariViewController) return@withContext false
    val browser = SFSafariViewController(nsUrl).apply {
        modalPresentationStyle = UIModalPresentationPageSheet
        modalTransitionStyle = UIModalTransitionStyleCoverVertical
    }
    presenter.presentViewController(browser, animated = true, completion = null)
    true
}
