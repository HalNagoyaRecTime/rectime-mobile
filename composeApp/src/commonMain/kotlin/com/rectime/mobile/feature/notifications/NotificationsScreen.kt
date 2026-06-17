package com.rectime.mobile.feature.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rectime.mobile.app.navigation.NavigationController
import com.rectime.mobile.app.navigation.Screen
import com.rectime.mobile.ui.component.PressSurface
import com.rectime.mobile.ui.component.PushScreenScaffold
import com.rectime.mobile.ui.theme.AppTheme

object NotificationsScreen : Screen {
    override val key: String = "notifications"

    @Composable
    override fun Content(navigationController: NavigationController) {
        val viewModel: NotificationsViewModel = viewModel()
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()

        PushScreenScaffold(
            title = "通知",
            onBack = { navigationController.requestPop() },
        ) {
            item {
                NotificationSummary(
                    unreadCount = uiState.unreadCount,
                    isRefreshing = uiState.isRefreshing,
                    onRefresh = { viewModel.refresh() },
                    onMarkAllRead = { viewModel.markAllRead() },
                    markAllEnabled = uiState.unreadCount > 0 && !uiState.isMarkingAllRead,
                )
            }

            item {
                NotificationFilterRow(
                    selectedFilter = uiState.selectedFilter,
                    onSelectFilter = { viewModel.selectFilter(it) },
                )
            }

            uiState.errorMessage?.let { message ->
                item {
                    NotificationError(message = message, onRetry = { viewModel.refresh(initialLoad = true) })
                }
            }

            if (uiState.isLoading) {
                item {
                    LoadingState()
                }
            } else if (uiState.notifications.isEmpty()) {
                item {
                    EmptyState(filter = uiState.selectedFilter)
                }
            } else {
                items(uiState.notifications, key = { it.id }) { notification ->
                    NotificationRow(
                        notification = notification,
                        isActionInProgress = uiState.actionInProgressIds.contains(notification.id),
                        onMarkRead = { viewModel.markRead(notification.id) },
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun NotificationSummary(
    unreadCount: Int,
    isRefreshing: Boolean,
    markAllEnabled: Boolean,
    onRefresh: () -> Unit,
    onMarkAllRead: () -> Unit,
) {
    PressSurface(
        onClick = onRefresh,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 10.dp),
        color = AppTheme.colors.surfaceMuted,
        shape = RoundedCornerShape(14.dp),
        contentPadding = PaddingValues(14.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (unreadCount == 0) "未読通知はありません" else "未読 $unreadCount 件",
                    color = AppTheme.colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = if (isRefreshing) "更新中" else "タップして再読み込み",
                    color = AppTheme.colors.textSecondary,
                )
            }
            Button(
                onClick = onMarkAllRead,
                enabled = markAllEnabled,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppTheme.colors.surfaceAccentStrong,
                    contentColor = AppTheme.colors.textOnAccent,
                ),
            ) {
                Text(text = "すべて既読")
            }
        }
    }
}

@Composable
private fun NotificationFilterRow(
    selectedFilter: NotificationReadFilter,
    onSelectFilter: (NotificationReadFilter) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
    ) {
        NotificationReadFilter.entries.forEach { filter ->
            val selected = selectedFilter == filter
            PressSurface(
                onClick = { onSelectFilter(filter) },
                modifier = Modifier.weight(1f),
                color = if (selected) AppTheme.colors.surfaceAccentStrong else AppTheme.colors.surfaceMuted,
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(vertical = 10.dp),
            ) {
                Text(
                    text = filter.label,
                    color = if (selected) AppTheme.colors.textOnAccent else AppTheme.colors.textPrimary,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun NotificationRow(
    notification: AppNotification,
    isActionInProgress: Boolean,
    onMarkRead: () -> Unit,
) {
    PressSurface(
        onClick = {
            if (!notification.isRead) onMarkRead()
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp),
        color = if (notification.isRead) AppTheme.colors.surfaceMuted else AppTheme.colors.surfaceAccent,
        shape = RoundedCornerShape(14.dp),
        contentPadding = PaddingValues(14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                ReadDot(isRead = notification.isRead)
                Text(
                    text = notification.title.ifBlank { "通知" },
                    color = AppTheme.colors.textPrimary,
                    fontWeight = if (notification.isRead) FontWeight.Medium else FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                SeverityPill(notification.severity)
            }

            Text(
                text = notification.message,
                color = AppTheme.colors.textSecondary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = notification.createdAt.ifBlank { notification.sentAt.orEmpty() },
                    color = AppTheme.colors.textMuted,
                    modifier = Modifier.weight(1f),
                )
                if (!notification.isRead) {
                    OutlinedButton(
                        onClick = onMarkRead,
                        enabled = !isActionInProgress,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text(text = if (isActionInProgress) "処理中" else "既読")
                    }
                } else {
                    Text(
                        text = notification.readAt?.let { "既読 $it" } ?: "既読",
                        color = AppTheme.colors.textMuted,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReadDot(isRead: Boolean) {
    val color = if (isRead) AppTheme.colors.borderStrong else AppTheme.colors.surfaceAccentStrong
    Box(
        modifier = Modifier
            .size(9.dp)
            .clip(CircleShape)
            .background(color),
    )
}

@Composable
private fun SeverityPill(severity: NotificationSeverity) {
    val (label, color) = when (severity) {
        NotificationSeverity.Info -> "Info" to AppTheme.colors.surfaceMuted
        NotificationSeverity.Success -> "Success" to Color(0xFFDDF7E6)
        NotificationSeverity.Warning -> "Warning" to Color(0xFFFFF0C2)
        NotificationSeverity.Error -> "Error" to Color(0xFFFFD9D9)
    }
    Text(
        text = label,
        color = AppTheme.colors.textPrimary,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
private fun NotificationError(message: String, onRetry: () -> Unit) {
    PressSurface(
        onClick = onRetry,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        color = AppTheme.colors.surfaceMuted,
        shape = RoundedCornerShape(14.dp),
        contentPadding = PaddingValues(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = message,
                color = AppTheme.colors.textSecondary,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRetry) {
                Text(text = "再試行")
            }
        }
    }
}

@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 36.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = AppTheme.colors.surfaceAccentStrong)
    }
}

@Composable
private fun EmptyState(filter: NotificationReadFilter) {
    val text = when (filter) {
        NotificationReadFilter.All -> "通知はありません"
        NotificationReadFilter.Unread -> "未読通知はありません"
        NotificationReadFilter.Read -> "既読通知はありません"
    }
    Text(
        text = text,
        color = AppTheme.colors.textSecondary,
        modifier = Modifier.padding(vertical = 24.dp),
    )
}
