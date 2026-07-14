package com.rectime.mobile.app

import coil3.PlatformContext
import java.io.File

actual fun getCacheDir(context: PlatformContext): String {
    val userHome = System.getProperty("user.home")
    val cacheDir = File(userHome, ".rectime/cache")
    if (!cacheDir.exists()) {
        cacheDir.mkdirs()
    }
    return cacheDir.absolutePath
}
