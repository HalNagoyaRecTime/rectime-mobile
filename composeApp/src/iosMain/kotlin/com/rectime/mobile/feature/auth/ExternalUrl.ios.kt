package com.rectime.mobile.feature.auth

import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import kotlin.coroutines.resume

// openURL(_:) は iOS 10 で非推奨になり、iOS 26 では false を返して何も起きない。
// 置き換え先の open(_:options:completionHandler:) は非同期で、
// completion handler がメインキュー上で呼ばれる。
actual suspend fun openExternalUrl(url: String): Boolean {
    val nsUrl = NSURL.URLWithString(url) ?: return false
    return suspendCancellableCoroutine { continuation ->
        UIApplication.sharedApplication.openURL(
            url = nsUrl,
            options = emptyMap<Any?, Any?>(),
        ) { success ->
            continuation.resume(success)
        }
    }
}
