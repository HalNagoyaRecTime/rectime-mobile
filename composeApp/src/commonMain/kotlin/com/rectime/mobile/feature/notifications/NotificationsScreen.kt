package com.rectime.mobile.feature.notifications

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rectime.mobile.app.navigation.NavigationController
import com.rectime.mobile.app.navigation.Screen
import com.rectime.mobile.ui.component.PressSurface
import com.rectime.mobile.ui.component.RootScreenScaffold
import com.rectime.mobile.ui.theme.AppTheme
import com.woowla.compose.icon.collections.fontawesome.fontawesome.SolidGroup
import com.woowla.compose.icon.collections.fontawesome.fontawesome.solid.ArrowsRotate

object NotificationsScreen : Screen {
    override val key: String = "notifications"

    @Composable
    override fun Content(navigationController: NavigationController) {
        RootScreenScaffold(
            title = "通知",
            onTrailingClick = { /* TODO: 通知一覧の再取得処理を実装予定 */ },
            trailing = {
                Icon(
                    imageVector = SolidGroup.ArrowsRotate,
                    contentDescription = "更新",
                    tint = AppTheme.colors.textPrimary,
                    modifier = Modifier.size(18.dp),
                )
            },
        ) {
            item {
                Text(
                    text = "通知一覧の画面です。現在はプレースホルダを表示しています。",
                    color = AppTheme.colors.textSecondary,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }
            items(40) { index ->
                PressSurface(
                    onClick = { navigationController.push(NotificationDetailScreen(id = index.toString())) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                ) {
                    Text(
                        text = "通知 #${index+1}",
                        color = AppTheme.colors.textPrimary,
                    )
                }
            }
        }
    }
}
