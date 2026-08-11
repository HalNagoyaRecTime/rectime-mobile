package com.rectime.mobile.feature.auth

// iOSの open(_:options:completionHandler:) が非同期のため suspend で宣言する。
expect suspend fun openExternalUrl(url: String): Boolean
