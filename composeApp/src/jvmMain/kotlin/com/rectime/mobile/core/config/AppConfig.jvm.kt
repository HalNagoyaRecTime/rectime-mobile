package com.rectime.mobile.core.config

actual val isDebugBuild: Boolean =
    System.getenv("DEBUG_BUILD")?.toBoolean() ?: System.getProperty("DEBUG_BUILD")?.toBoolean() ?: true

actual val apiBaseUrlResult: ApiBaseUrlResult = resolveApiBaseUrl(
    System.getenv("API_BASE_URL") ?: System.getProperty("API_BASE_URL"),
    isDebugBuild,
)
