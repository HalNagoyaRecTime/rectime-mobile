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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rectime.mobile.app.navigation.NavigationController
import com.rectime.mobile.app.navigation.Screen
import com.rectime.mobile.feature.competition.CompetitionDetailScreen
import com.rectime.mobile.ui.component.OfflineBanner
import com.rectime.mobile.ui.component.PressSurface
import com.rectime.mobile.ui.component.PushScreenScaffold
import com.rectime.mobile.ui.theme.AppTheme
import com.woowla.compose.icon.collections.fontawesome.fontawesome.SolidGroup
import com.woowla.compose.icon.collections.fontawesome.fontawesome.solid.ChevronRight
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

data class NotificationDetailScreen(val id: Int) : Screen {
    override val key: String = "notification_detail_$id"

    @Composable
    override fun Content(navigationController: NavigationController) {
        val viewModel = viewModel(key = key) { NotificationDetailViewModel(id) }
        val uiState by viewModel.uiState.collectAsState()

        PushScreenScaffold(
            title = "通知詳細",
            onBack = { navigationController.requestPop() },
        ) {
            item {
                when {
                    uiState.isLoading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(vertical = 24.dp),
                        )
                    }

                    uiState.error != null -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                text = requireNotNull(uiState.error),
                                color = MaterialTheme.colorScheme.error,
                            )
                            Button(onClick = viewModel::retry) {
                                Text("再読み込み")
                            }
                        }
                    }

                    uiState.notification != null -> {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (uiState.isOffline) {
                                OfflineBanner(
                                    message = "オフライン: 最新の通知を取得できません。前回取得時の内容を表示しています。",
                                )
                            }
                            NotificationDetailContent(
                                notification = requireNotNull(uiState.notification),
                                onRelatedEventClick = { eventId ->
                                    navigationController.push(
                                        CompetitionDetailScreen(eventId = eventId),
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationDetailContent(
    notification: UserNotification,
    onRelatedEventClick: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = notification.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = AppTheme.colors.textPrimary,
        )
        Text(
            text = notification.scheduledAt.toNotificationDateTime(),
            style = MaterialTheme.typography.labelLarge,
            color = AppTheme.colors.textSecondary,
        )
        Text(
            text = notification.body,
            style = MaterialTheme.typography.bodyLarge,
            color = AppTheme.colors.textPrimary,
        )

        notification.relatedEvent?.let { event ->
            Text(
                text = "関連競技",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = AppTheme.colors.textSecondary,
                modifier = Modifier.padding(top = 8.dp),
            )
            NotificationRelatedEventLink(
                event = event,
                onClick = { onRelatedEventClick(event.id) },
            )
        }
    }
}

@Composable
private fun NotificationRelatedEventLink(
    event: NotificationRelatedEvent,
    onClick: () -> Unit,
) {
    PressSurface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = event.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = AppTheme.colors.textPrimary,
                )
                Text(
                    text = event.venue,
                    style = MaterialTheme.typography.bodyMedium,
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

internal fun String.toNotificationDateTime(
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): String = runCatching {
    val dateTime = Instant.parse(this).toLocalDateTime(timeZone)
    "${dateTime.month.ordinal + 1}月${dateTime.day}日 " +
        "${dateTime.hour.toTwoDigits()}:${dateTime.minute.toTwoDigits()}"
}.getOrElse { this }

private fun Int.toTwoDigits(): String = toString().padStart(2, '0')
