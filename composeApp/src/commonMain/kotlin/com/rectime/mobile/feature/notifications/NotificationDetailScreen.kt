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

data class NotificationDetailScreen(val id: String) : Screen {
    override val key: String = "notification_detail_$id"

    @Composable
    override fun Content(navigationController: NavigationController) {
        PushScreenScaffold(
            title = "通知詳細",
            onBack = { navigationController.requestPop() },
        ) {
            item {
                Text(
                    text = "通知一覧 #$id の詳細です。現在はプレースホルダを表示しています。",
                    color = AppTheme.colors.textSecondary,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }
        }
    }
}
