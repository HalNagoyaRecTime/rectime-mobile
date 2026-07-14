package com.rectime.mobile.feature.notifications

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rectime.mobile.app.navigation.NavigationController
import com.rectime.mobile.app.navigation.Screen
import com.rectime.mobile.feature.detail.DetailScreen
import com.rectime.mobile.feature.schedule.ScheduleDetailScreen
import com.rectime.mobile.ui.component.PressSurface
import com.rectime.mobile.ui.component.PushScreenScaffold
import com.rectime.mobile.ui.theme.AppTheme
import com.woowla.compose.icon.collections.fontawesome.fontawesome.SolidGroup
import com.woowla.compose.icon.collections.fontawesome.fontawesome.solid.ChevronRight

data class NotificationDetailScreen(val id: String) : Screen {
    override val key: String = "notification_detail_$id"

    @Composable
    override fun Content(navigationController: NavigationController) {
        PushScreenScaffold(
            title = "通知詳細",
            onBack = { navigationController.requestPop() },
            bottomContent = {
                Button(
                    onClick = {
                        //既読処理を後程追加
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("既読にする")
                }
            },
        ) {
            item {
                Text(
                    text = "通知一覧 #$id の詳細です。現在はプレースホルダを表示しています。",
                    color = AppTheme.colors.textSecondary,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
                Text(
                    text = "関連情報",
                    color = AppTheme.colors.textSecondary,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                )
            }
            item {
                NotificationRelatedLink(
                    label = "スケジュール詳細",
                    onClick = { navigationController.push(ScheduleDetailScreen(id = id)) },
                )
            }
            item {
                NotificationRelatedLink(
                    label = "競技詳細",
                    onClick = { navigationController.push(DetailScreen(id = id)) },
                )
            }
        }
    }
}

@Composable
private fun NotificationRelatedLink(
    label: String,
    onClick: () -> Unit,
) {
    PressSurface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                color = AppTheme.colors.textPrimary,
            )
            Icon(
                imageVector = SolidGroup.ChevronRight,
                contentDescription = null,
                tint = AppTheme.colors.textSecondary,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}
