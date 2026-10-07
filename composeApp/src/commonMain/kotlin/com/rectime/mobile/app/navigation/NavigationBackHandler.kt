package com.rectime.mobile.app.navigation

import androidx.compose.runtime.Composable

/** OSの戻る操作を、画面内の戻るボタンと同じ経路に接続する。 */
@Composable
internal expect fun NavigationBackHandler(enabled: Boolean, onBack: () -> Unit)
