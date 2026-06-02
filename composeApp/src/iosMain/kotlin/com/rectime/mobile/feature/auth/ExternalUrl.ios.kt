package com.rectime.mobile.feature.auth

import platform.Foundation.NSURL
import platform.UIKit.UIApplication

actual fun openExternalUrl(url: String): Boolean {
    val nsUrl = NSURL.URLWithString(url) ?: return false
    UIApplication.sharedApplication.openURL(nsUrl)
    return true
}
