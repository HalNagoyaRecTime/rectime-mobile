package com.rectime.mobile.core.config

expect val apiBaseUrlResult: ApiBaseUrlResult

val apiBaseUrl: String
    get() = (apiBaseUrlResult as? ApiBaseUrlResult.Valid)?.url.orEmpty()

val apiBaseUrlConfigurationError: String?
    get() = (apiBaseUrlResult as? ApiBaseUrlResult.Invalid)?.reason

expect val isDebugBuild: Boolean

expect val appVersion: String

// アプリ内の表示は各OSが持つ表示名を参照する。識別子・実行ファイル名とは分離する。
internal const val DefaultAppDisplayName = "RE:CREATION"
expect val appDisplayName: String
