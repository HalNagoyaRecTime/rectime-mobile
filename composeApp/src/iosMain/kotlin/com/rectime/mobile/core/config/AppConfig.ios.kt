package com.rectime.mobile.core.config

import kotlin.experimental.ExperimentalNativeApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import kotlin.native.Platform
import platform.Foundation.NSBundle
import platform.posix.getenv

// Info.plist に DEBUG_BUILD キーが存在せず常に false になっていたため、
// フレームワークのビルドモードから直接判定する。
@OptIn(ExperimentalNativeApi::class)
actual val isDebugBuild: Boolean = Platform.isDebugBinary

@OptIn(ExperimentalForeignApi::class)
actual val apiBaseUrlResult: ApiBaseUrlResult = resolveApiBaseUrl(
    rawValue =
        (NSBundle.mainBundle.objectForInfoDictionaryKey("API_BASE_URL") as? String)
            ?.takeIf { it.isNotBlank() && !it.startsWith("$(") }
            ?: getenv("API_BASE_URL")?.toKString()?.takeIf { isDebugBuild },
    isDebug = isDebugBuild,
)

actual val appVersion: String
    get() = NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String ?: "不明"

actual val appDisplayName: String
    get() = (NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleDisplayName") as? String)
        ?.takeIf { it.isNotBlank() }
        ?: (NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleName") as? String)?.takeIf { it.isNotBlank() }
        ?: DefaultAppDisplayName
