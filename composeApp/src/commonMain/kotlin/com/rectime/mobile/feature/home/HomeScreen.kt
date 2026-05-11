package com.rectime.mobile.feature.home

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rectime.mobile.app.navigation.NavigationController
import com.rectime.mobile.app.navigation.Screen
import com.rectime.mobile.core.model.MockUser
import com.rectime.mobile.feature.notifications.NotificationsScreen
import com.rectime.mobile.ui.component.RootScreenScaffold
import com.rectime.mobile.ui.theme.AppTheme
import com.woowla.compose.icon.collections.fontawesome.fontawesome.SolidGroup
import com.woowla.compose.icon.collections.fontawesome.fontawesome.solid.Bell

object HomeScreen : Screen {
    override val key: String = "home"

    @Composable
    override fun Content(navigationController: NavigationController) {
        RootScreenScaffold(
            title = "ホーム",
            profile = MockUser.me,
            onOpenMenu = { navigationController.openMenu() },
            onTrailingClick = { navigationController.push(NotificationsScreen) },
            trailingSfSymbol = "bell",
            trailing = {
                Icon(
                    imageVector = SolidGroup.Bell,
                    contentDescription = "通知",
                    tint = AppTheme.colors.textPrimary,
                    modifier = Modifier.size(20.dp),
                )
            },
        ) {
            // コンテンツアイテムをここに追加
        }
    }
}
