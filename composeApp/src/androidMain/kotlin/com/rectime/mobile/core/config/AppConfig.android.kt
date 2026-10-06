package com.rectime.mobile.core.config

import com.rectime.mobile.BuildConfig
import com.rectime.mobile.R
import com.rectime.mobile.core.platform.getPlatformContext

actual val isDebugBuild: Boolean = BuildConfig.DEBUG
actual val apiBaseUrlResult: ApiBaseUrlResult = resolveApiBaseUrl(BuildConfig.API_BASE_URL, isDebugBuild)

actual val appVersion: String = BuildConfig.VERSION_NAME

actual val appDisplayName: String
    get() = getPlatformContext()?.getString(R.string.app_name) ?: DefaultAppDisplayName
