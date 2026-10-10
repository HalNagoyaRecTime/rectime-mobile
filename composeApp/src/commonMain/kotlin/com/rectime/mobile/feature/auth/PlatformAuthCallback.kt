package com.rectime.mobile.feature.auth

// Desktopのみブラウザを開く前にループバックの受信を開始する。
internal expect fun preparePlatformAuthCallback(state: String): String?
internal expect fun cancelPlatformAuthCallback(state: String)
