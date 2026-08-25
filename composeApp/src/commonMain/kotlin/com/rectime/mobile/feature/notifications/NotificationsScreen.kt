package com.rectime.mobile.feature.notifications

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rectime.mobile.app.navigation.NavigationController
import com.rectime.mobile.app.navigation.Screen
import com.rectime.mobile.ui.component.OfflineBanner
import com.rectime.mobile.ui.component.PressSurface
import com.rectime.mobile.ui.component.RootScreenScaffold
import com.rectime.mobile.ui.theme.AppTheme
import com.woowla.compose.icon.collections.fontawesome.fontawesome.SolidGroup
import com.woowla.compose.icon.collections.fontawesome.fontawesome.solid.ArrowsRotate
import com.woowla.compose.icon.collections.fontawesome.fontawesome.solid.ChevronRight

object NotificationsScreen : Screen {
    override val key: String = "notifications"

    @Composable
    override fun Content(navigationController: NavigationController) {
        val viewModel = viewModel(key = key) { NotificationsViewModel() }
        val uiState by viewModel.uiState.collectAsState()

        RootScreenScaffold(
            title = "通知",
            onTrailingClick = viewModel::refresh,
            trailing = {
                if (uiState.isRefreshing) {
                    CircularProgressIndicator(
                        color = AppTheme.colors.textPrimary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp),
                    )
                } else {
                    Icon(
                        imageVector = SolidGroup.ArrowsRotate,
                        contentDescription = "更新",
                        tint = AppTheme.colors.textPrimary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            },
        ) {
            when {
                uiState.isLoading -> item {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }

                uiState.error != null && uiState.notifications.isEmpty() -> item {
                    NotificationMessage(
                        message = requireNotNull(uiState.error),
                        actionLabel = "再読み込み",
                        onAction = viewModel::refresh,
                    )
                }

                uiState.notifications.isEmpty() -> item {
                    Column {
                        if (uiState.isOffline) {
                            OfflineBanner(
                                message = "オフライン: 最新の通知を取得できません。前回取得時の内容を表示しています。",
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                        }
                        NotificationMessage(message = "通知はありません")
                    }
                }

                else -> {
                    if (uiState.isOffline) {
                        item {
                            OfflineBanner(
                                message = "オフライン: 最新の通知を取得できません。前回取得時の内容を表示しています。",
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                        }
                    }
                    uiState.error?.let { error ->
                        item {
                            Text(
                                text = error,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                        }
                    }
                    items(
                        count = uiState.notifications.size,
                        key = { index -> uiState.notifications[index].id },
                    ) { index ->
                        val notification = uiState.notifications[index]
                        NotificationListItem(
                            notification = notification,
                            onClick = {
                                navigationController.push(
                                    NotificationDetailScreen(id = notification.id),
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationListItem(
    notification: UserNotification,
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
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = notification.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = AppTheme.colors.textPrimary,
                )
                Text(
                    text = notification.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppTheme.colors.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = notification.scheduledAt.toNotificationDateTime(),
                    style = MaterialTheme.typography.labelMedium,
                    color = AppTheme.colors.textSecondary,
                )
            }
            Icon(
                imageVector = SolidGroup.ChevronRight,
                contentDescription = null,
                tint = AppTheme.colors.textSecondary,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
private fun NotificationMessage(
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = message,
            color = AppTheme.colors.textSecondary,
        )
        if (actionLabel != null && onAction != null) {
            Button(onClick = onAction) {
                Text(actionLabel)
            }
        }
    }
}
