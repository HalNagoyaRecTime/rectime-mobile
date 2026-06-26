package com.rectime.mobile.app

import coil3.PlatformContext

actual fun getCacheDir(context: PlatformContext): String {
    return context.cacheDir.absolutePath
}
