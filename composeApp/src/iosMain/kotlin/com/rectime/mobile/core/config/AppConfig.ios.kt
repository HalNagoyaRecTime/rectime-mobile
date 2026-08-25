package com.rectime.mobile.core.config

import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.Platform
import platform.Foundation.NSBundle

actual val apiBaseUrl: String =
    (NSBundle.mainBundle.objectForInfoDictionaryKey("API_BASE_URL") as? String)
        ?.takeIf { it.isNotBlank() && !it.startsWith("$(") }
        ?: "http://localhost:8787"

// Info.plist に DEBUG_BUILD キーが存在せず常に false になっていたため、
// フレームワークのビルドモードから直接判定する。
@OptIn(ExperimentalNativeApi::class)
actual val isDebugBuild: Boolean = Platform.isDebugBinary
