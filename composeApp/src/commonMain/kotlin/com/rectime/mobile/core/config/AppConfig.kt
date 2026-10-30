package com.rectime.mobile.core.config

expect val apiBaseUrlResult: ApiBaseUrlResult

internal val nagoyaApiBaseUrl: String
    get() = (apiBaseUrlResult as? ApiBaseUrlResult.Valid)?.url.orEmpty()

val apiBaseUrl: String
    get() = campusApiBaseUrl(CurrentCampus.campus, appEnvironment, nagoyaApiBaseUrl).orEmpty()

val apiBaseUrlConfigurationError: String?
    get() = (apiBaseUrlResult as? ApiBaseUrlResult.Invalid)?.reason

expect val isDebugBuild: Boolean

enum class AppEnvironment { Development, Staging, Production }

expect val appEnvironment: AppEnvironment

expect val appVersion: String

// アプリ内の表示は各OSが持つ表示名を参照する。識別子・実行ファイル名とは分離する。
internal const val DefaultAppDisplayName = "RE:CREATION"
expect val appDisplayName: String

// フォームの転送先はAPI側で管理する。ブラウザで開く公開エンドポイント。
// apiBaseUrl にすると他校の問い合わせ先を開くため、学校によらず名古屋の API を使う。
internal val contactFormUrl: String
    get() = "${nagoyaApiBaseUrl.trimEnd('/')}/app/contact"
