package com.rectime.mobile.core.config

actual val apiBaseUrl: String = JvmAppConfig.API_BASE_URL

actual val isDebugBuild: Boolean =
    System.getenv("DEBUG_BUILD")?.toBoolean() ?: System.getProperty("DEBUG_BUILD")?.toBoolean() ?: true
