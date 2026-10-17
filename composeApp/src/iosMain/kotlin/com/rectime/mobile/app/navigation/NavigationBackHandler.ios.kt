package com.rectime.mobile.app.navigation

import androidx.compose.runtime.Composable

// iOSの戻る操作は共通のスワイプ処理が担当する。
@Composable
internal actual fun NavigationBackHandler(enabled: Boolean, onBack: () -> Unit) {}
