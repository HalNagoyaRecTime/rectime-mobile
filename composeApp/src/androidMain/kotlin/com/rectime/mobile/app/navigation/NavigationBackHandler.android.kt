package com.rectime.mobile.app.navigation

import androidx.compose.runtime.Composable
import androidx.activity.compose.BackHandler

@Composable
internal actual fun NavigationBackHandler(enabled: Boolean, onBack: () -> Unit) {
    BackHandler(enabled = enabled, onBack = onBack)
}
