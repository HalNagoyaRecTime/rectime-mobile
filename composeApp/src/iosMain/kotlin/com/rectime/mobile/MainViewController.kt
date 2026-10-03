package com.rectime.mobile

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeUIViewController
import com.rectime.mobile.app.App
import com.rectime.mobile.feature.notifications.createIosNotificationPermissionStartup
import com.rectime.mobile.ui.component.LocalBottomNavigationBottomMargin
import com.rectime.mobile.ui.component.LocalBottomNavigationSafeAreaOverlap

fun MainViewController(
    onNotificationPermissionGranted: () -> Unit,
) = ComposeUIViewController {
    CompositionLocalProvider(
        LocalBottomNavigationBottomMargin provides 0.dp,
        LocalBottomNavigationSafeAreaOverlap provides 8.dp,
    ) {
        App(createIosNotificationPermissionStartup(onNotificationPermissionGranted))
    }
}
