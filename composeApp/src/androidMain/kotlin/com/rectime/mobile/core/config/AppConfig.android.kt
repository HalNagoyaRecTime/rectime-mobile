package com.rectime.mobile.core.config

import com.rectime.mobile.BuildConfig

actual val isDebugBuild: Boolean = BuildConfig.DEBUG
actual val apiBaseUrlResult: ApiBaseUrlResult = resolveApiBaseUrl(BuildConfig.API_BASE_URL, isDebugBuild)
