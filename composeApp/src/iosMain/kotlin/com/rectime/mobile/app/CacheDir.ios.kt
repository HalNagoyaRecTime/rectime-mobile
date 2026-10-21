package com.rectime.mobile.app

import coil3.PlatformContext
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

actual fun getCacheDir(context: PlatformContext): String {
    val paths = NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true)
    return paths.firstOrNull()?.toString() ?: ""
}
