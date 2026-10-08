package com.rectime.mobile.feature.notifications

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rectime.mobile.app.navigation.NavigationController
import com.rectime.mobile.app.navigation.Screen
import com.rectime.mobile.ui.component.AppLoadingIndicator
import com.rectime.mobile.ui.component.PressSurface
import com.rectime.mobile.ui.component.RootScreenScaffold
import com.rectime.mobile.ui.modifier.outerShadow
import com.rectime.mobile.ui.theme.AppTheme
import com.woowla.compose.icon.collections.fontawesome.fontawesome.SolidGroup
import com.woowla.compose.icon.collections.fontawesome.fontawesome.solid.ChevronRight
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import org.jetbrains.compose.resources.painterResource
import rectime_mobile.composeapp.generated.resources.Res
import rectime_mobile.composeapp.generated.resources.ic_ic_refresh
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private const val NotificationItemAnimationMillis = 220
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
        val showCenterLoading by viewModel.showCenterLoading.collectAsState()
        val now by produceState(Clock.System.now()) {
            while (true) {
                delay(RelativeTimeTick)
                value = Clock.System.now()
            }
        }

        // 初めに表示する保存済み一覧は即時表示し、それ以降の追加だけフェードする。
        var initialNotificationIds by remember(viewModel) { mutableStateOf<Set<Int>?>(null) }
        SideEffect {
            if (initialNotificationIds == null && !uiState.isLoading && uiState.error == null) {
                initialNotificationIds = uiState.notifications.map { it.id }.toSet()
            }
        }

        val listState = rememberLazyListState()
        LaunchedEffect(listState, viewModel) {
            snapshotFlow {
                uiState.hasMore && !uiState.isUpdating && !uiState.isLoadingMore &&
                    !uiState.isRefreshing && uiState.pageError == null &&
                    (listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1) >= uiState.notifications.size - 3
            }.distinctUntilChanged().collect { nearEnd ->
                if (nearEnd) viewModel.loadMore()
            }
        }

        Box(Modifier.fillMaxSize()) {
            RootScreenScaffold(
                title = "通知",
                lazyListState = listState,
                isRefreshing = uiState.isPullRefreshing,
                refreshEnabled = !uiState.isUpdating && !uiState.isRefreshing && !uiState.isLoadingMore,
                onRefresh = viewModel::refreshFromPull,
                modifier = Modifier.background(AppTheme.colors.notificationBackground),
                onTrailingClick = if (uiState.isUpdating || uiState.isRefreshing || uiState.isLoadingMore) null else viewModel::refresh,
                trailing = {
                    NotificationRefreshIcon(isRefreshing = uiState.isHeaderRefreshing)
                },
            ) {
                when {
                    uiState.isLoading -> Unit

                    uiState.error != null && uiState.notifications.isEmpty() -> item {
                        NotificationMessage(
                            message = requireNotNull(uiState.error),
                            actionLabel = "再読み込み",
                            onAction = viewModel::refresh,
                        )
                    }

                    uiState.notifications.isEmpty() -> item {
                        NotificationMessage(message = "通知はありません")
                    }

                    else -> {
                        items(
                            count = uiState.notifications.size,
                            key = { index -> uiState.notifications[index].id },
                        ) { index ->
                            val notification = uiState.notifications[index]
                            NotificationCard(
                                modifier = Modifier.animateItem(
                                    fadeInSpec = if (initialNotificationIds != null &&
                                        notification.id !in initialNotificationIds.orEmpty()
                                    ) tween(NotificationItemAnimationMillis) else null,
                                    placementSpec = tween(NotificationItemAnimationMillis),
                                    fadeOutSpec = tween(NotificationItemAnimationMillis),
                                ),
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
                        if (uiState.isLoadingMore) {
                            item {
                                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                    AppLoadingIndicator()
                                }
                            }
                        }
                        if (uiState.pageError != null) {
                            item {
                                NotificationMessage(
                                    message = uiState.pageError.orEmpty(),
                                    actionLabel = "再読み込み",
                                    onAction = viewModel::loadMore,
                                )
                            }
                        }
                    }
                }
            }
            if (showCenterLoading) {
                Box(Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
                    AppLoadingIndicator(modifier = Modifier.size(32.dp))
                }
            }
        }
    }
}

@Composable
private fun NotificationRefreshIcon(isRefreshing: Boolean) {
    val rotation = remember { Animatable(0f) }
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            while (true) {
                // A new request can interrupt the final turn of the previous one.
                // Always reach a full-turn boundary before resetting the angle.
                val endAngle = nextRefreshRotationTarget(rotation.value)
                rotation.animateTo(
                    endAngle,
                    tween(refreshRotationDurationMillis(rotation.value, endAngle), easing = LinearEasing),
                )
                rotation.snapTo(0f)
            }
        } else {
            // Finish the current turn before resetting, rather than jumping backwards.
            val endAngle = ceil(rotation.value / 360f) * 360f
            val remainingMillis = refreshRotationDurationMillis(rotation.value, endAngle)
            if (remainingMillis > 0) {
                rotation.animateTo(endAngle, tween(remainingMillis, easing = LinearEasing))
            }
            rotation.snapTo(0f)
        }
    }
    Icon(
        painter = painterResource(Res.drawable.ic_ic_refresh),
        contentDescription = if (isRefreshing) "更新中" else "更新",
        tint = AppTheme.colors.textNavigationInactive,
        modifier = Modifier.size(29.dp).graphicsLayer { rotationZ = rotation.value },
    )
}

internal fun nextRefreshRotationTarget(angle: Float): Float =
    (floor(angle / 360f) + 1f) * 360f

internal fun refreshRotationDurationMillis(startAngle: Float, endAngle: Float): Int =
    ((endAngle - startAngle) / 360f * 600).roundToInt().coerceAtLeast(0)

@Composable
private fun NotificationCard(
    notification: UserNotification,
    now: Instant,
    isRead: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
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
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = AppTheme.spacing.xs)
            .outerShadow(
                shape = cardShape,
                color = AppTheme.colors.dropShadow,
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
