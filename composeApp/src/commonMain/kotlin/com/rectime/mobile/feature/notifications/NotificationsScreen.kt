package com.rectime.mobile.feature.notifications

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rectime.mobile.app.navigation.NavigationController
import com.rectime.mobile.app.navigation.Screen
import com.rectime.mobile.ui.component.PushScreenScaffold
import com.rectime.mobile.ui.theme.AppTheme

object NotificationsScreen : Screen {
    override val key: String = "notifications"

    @Composable
    override fun Content(navigationController: NavigationController) {
        PushScreenScaffold(
            title = "通知",
            onBack = { navigationController.requestPop() },
        ) {
            item {
                Text(
                    text = "通知一覧の画面です。現在はプレースホルダを表示しています。\n" +
                            "通知一覧\n".repeat(40).trimEnd(),
                    color = AppTheme.colors.textSecondary,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }
        }
    }
}
