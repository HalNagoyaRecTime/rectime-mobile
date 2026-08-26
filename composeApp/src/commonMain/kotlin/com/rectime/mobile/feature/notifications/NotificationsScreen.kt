package com.rectime.mobile.feature.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rectime.mobile.app.navigation.NavigationController
import com.rectime.mobile.app.navigation.Screen
import com.rectime.mobile.ui.component.OfflineBanner
import com.rectime.mobile.ui.component.PressSurface
import com.rectime.mobile.ui.component.RootScreenScaffold
import com.rectime.mobile.ui.modifier.outerShadow
import com.rectime.mobile.ui.theme.AppTheme
import com.woowla.compose.icon.collections.fontawesome.fontawesome.SolidGroup
import com.woowla.compose.icon.collections.fontawesome.fontawesome.solid.ChevronRight
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import rectime_mobile.composeapp.generated.resources.Res
import rectime_mobile.composeapp.generated.resources.ic_ic_refresh
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private val RelativeTimeTick = 30.seconds
private val UnreadDotSize = 12.dp
private val ChevronSize = 14.dp
// ドットとシェブロンを1行目の文字の高さに合わせるためのオフセット
private val UnreadDotTopPadding = 8.dp
private val ChevronTopPadding = 8.dp
private val CardShadowBlur = 8.dp
private val CardShadowOffsetY = 2.dp
private val CardTextOffsetY = (-1).dp
private val CardBodyOffsetY = (-3).dp
private val UnreadDotStartPadding = 10.dp

object NotificationsScreen : Screen {
    override val key: String = "notifications"

    @Composable
    override fun Content(navigationController: NavigationController) {
        val viewModel = viewModel(key = key) { NotificationsViewModel() }
        val uiState by viewModel.uiState.collectAsState()
        val now by produceState(Clock.System.now()) {
            while (true) {
                delay(RelativeTimeTick)
                value = Clock.System.now()
            }
        }

        RootScreenScaffold(
            title = "通知一覧",
            modifier = Modifier.background(AppTheme.colors.notificationBackground),
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
                        painter = painterResource(Res.drawable.ic_ic_refresh),
                        contentDescription = "更新",
                        tint = AppTheme.colors.textNavigationInactive,
                        modifier = Modifier.size(29.dp),
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
                        NotificationCard(
                            notification = notification,
                            now = now,
                            isRead = notification.id in uiState.readIds,
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
private fun NotificationCard(
    notification: UserNotification,
    now: Instant,
    isRead: Boolean,
    onClick: () -> Unit,
) {
    val titleColor = if (isRead) {
        AppTheme.colors.textReadNotificationTitle
    } else {
        AppTheme.colors.textUnreadNotificationTitle
    }
    val bodyColor = if (isRead) {
        AppTheme.colors.textReadNotificationBody
    } else {
        AppTheme.colors.textUnreadNotificationBody
    }
    val timeColor = if (isRead) {
        AppTheme.colors.textReadNotificationTime
    } else {
        AppTheme.colors.textUnreadNotificationTime
    }
    val titleWeight = if (isRead) FontWeight.Normal else FontWeight.Bold
    val bodyWeight = if (isRead) FontWeight.Normal else FontWeight.Medium
    val chevronColor = if (isRead) {
        AppTheme.colors.readNotificationChevron
    } else {
        AppTheme.colors.unreadNotificationChevron
    }

    val cardShape = RoundedCornerShape(AppTheme.radius.md)

    PressSurface(
        onClick = onClick,
        color = AppTheme.colors.commonBackground,
        shape = cardShape,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AppTheme.spacing.xs)
            .outerShadow(
                shape = cardShape,
                color = AppTheme.colors.dropShadowDark,
                blurRadius = CardShadowBlur,
                offsetX = 0.dp,
                offsetY = CardShadowOffsetY,
            ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.Top)
                    .padding(start = UnreadDotStartPadding, top = UnreadDotTopPadding)
                    .size(UnreadDotSize)
                    .background(
                        color = if (isRead) Color.Transparent else AppTheme.colors.themeColorFirst,
                        shape = CircleShape,
                    ),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .offset(y = CardTextOffsetY),
                verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.xs),
            ) {
                Text(
                    text = notification.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = titleWeight,
                    color = titleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    modifier = Modifier.offset(y = CardBodyOffsetY),
                    text = notification.body,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = bodyWeight,
                    color = bodyColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = notification.scheduledAt.toNotificationListDateTime(now),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = timeColor,
                )
            }
            Icon(
                imageVector = SolidGroup.ChevronRight,
                contentDescription = null,
                tint = chevronColor,
                modifier = Modifier
                    .align(Alignment.Top)
                    .padding(top = ChevronTopPadding)
                    .size(ChevronSize),
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
