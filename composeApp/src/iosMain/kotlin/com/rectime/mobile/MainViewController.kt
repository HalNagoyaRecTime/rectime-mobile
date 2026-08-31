package com.rectime.mobile

import androidx.compose.ui.window.ComposeUIViewController
import com.rectime.mobile.app.App
import com.rectime.mobile.feature.notifications.createIosNotificationPermissionStartup

fun MainViewController() = ComposeUIViewController {
    App(createIosNotificationPermissionStartup())
}
