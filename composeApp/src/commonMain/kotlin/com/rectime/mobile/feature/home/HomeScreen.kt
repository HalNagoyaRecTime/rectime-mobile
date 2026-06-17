package com.rectime.mobile.feature.home

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rectime.mobile.app.navigation.NavigationController
import com.rectime.mobile.app.navigation.Screen
import com.rectime.mobile.core.model.MockUser
import com.rectime.mobile.feature.notifications.NotificationBellBadge
import com.rectime.mobile.feature.notifications.NotificationsScreen
import com.rectime.mobile.ui.component.RootScreenScaffold

object HomeScreen : Screen {
    override val key: String = "home"

    @Composable
    override fun Content(navigationController: NavigationController) {
        RootScreenScaffold(
            title = "ホーム",
            profile = MockUser.me,
            onOpenMenu = { navigationController.openMenu() },
            onTrailingClick = { navigationController.push(NotificationsScreen) },
            trailing = {
                NotificationBellBadge(
                    modifier = Modifier.size(20.dp),
                )
            },
        ) {
            // コンテンツアイテムをここに追加
        }
    }
}
