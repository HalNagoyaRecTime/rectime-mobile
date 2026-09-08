package com.rectime.mobile.core.config

expect val apiBaseUrlResult: ApiBaseUrlResult

val apiBaseUrl: String
    get() = (apiBaseUrlResult as? ApiBaseUrlResult.Valid)?.url.orEmpty()

val apiBaseUrlConfigurationError: String?
    get() = (apiBaseUrlResult as? ApiBaseUrlResult.Invalid)?.reason

expect val isDebugBuild: Boolean
