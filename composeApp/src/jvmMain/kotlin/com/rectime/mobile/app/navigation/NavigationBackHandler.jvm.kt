package com.rectime.mobile.app.navigation

import androidx.compose.runtime.Composable

// デスクトップのOS戻る操作は今回の対象外。
@Composable
internal actual fun NavigationBackHandler(enabled: Boolean, onBack: () -> Unit) {}
