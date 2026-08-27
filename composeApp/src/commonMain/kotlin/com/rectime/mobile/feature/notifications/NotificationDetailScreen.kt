package com.rectime.mobile.feature.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rectime.mobile.app.navigation.NavigationController
import com.rectime.mobile.app.navigation.Screen
import com.rectime.mobile.feature.competition.CompetitionDetailScreen
import com.rectime.mobile.ui.component.AppDivider
import com.rectime.mobile.ui.component.EventCard
import com.rectime.mobile.ui.component.OfflineBanner
import com.rectime.mobile.ui.component.PressSurface
import com.rectime.mobile.ui.component.PushScreenScaffold
import com.rectime.mobile.ui.theme.AppTheme
import com.woowla.compose.icon.collections.fontawesome.fontawesome.SolidGroup
import com.woowla.compose.icon.collections.fontawesome.fontawesome.solid.ChevronRight
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

private val LoadingIndicatorPadding = 24.dp
private val ErrorContentPadding = 32.dp
private val ErrorContentSpacing = 12.dp
private val NotificationContentHorizontalPadding = 10.dp
private val NotificationContentVerticalPadding = 12.dp
private val NotificationContentSpacing = 12.dp
private val NotificationTitleTopPadding = 16.dp
private val NotificationDateTimeOffsetY = (-8).dp
private val NotificationBodyLineHeight = 30.sp
private val RelatedEventSpacing = 6.dp
private val RelatedEventTitleTopPadding = 8.dp
private val EventCardHeight = 80.dp

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
                            modifier = Modifier.padding(vertical = LoadingIndicatorPadding),
                        )
                    }

                    uiState.error != null -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = ErrorContentPadding),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(ErrorContentSpacing),
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
                        Column(verticalArrangement = Arrangement.spacedBy(ErrorContentSpacing)) {
                            if (uiState.isOffline) {
                                OfflineBanner(
                                    message = "オフライン: 最新の通知を取得できません。前回取得時の内容を表示しています。",
                                )
                            }
                            NotificationDetailContent(
                                notification = requireNotNull(uiState.notification),
                                isParticipatingInRelatedEvent = uiState.isParticipatingInRelatedEvent,
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
    isParticipatingInRelatedEvent: Boolean,
    onRelatedEventClick: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = NotificationContentHorizontalPadding, vertical = NotificationContentVerticalPadding),
        verticalArrangement = Arrangement.spacedBy(NotificationContentSpacing),
    ) {
        Text(
            text = notification.title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            color = AppTheme.colors.textDetailsScreenTitle,
            modifier = Modifier.padding(top = NotificationTitleTopPadding),
        )
        Text(
            text = notification.scheduledAt.toNotificationDateTime(),
            style = MaterialTheme.typography.labelLarge,
            color = AppTheme.colors.textDetailsScreenTime,
            modifier = Modifier.offset(y = NotificationDateTimeOffsetY),
        )
        AppDivider()
        Text(
            text = notification.body,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = AppTheme.colors.textDetailsScreenBody,
            lineHeight = NotificationBodyLineHeight,
        )
        AppDivider()

        notification.relatedEvent?.let { event ->
            Column(verticalArrangement = Arrangement.spacedBy(RelatedEventSpacing)) {
                Text(
                    text = "関連イベント",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = AppTheme.colors.textRelationEvent,
                    modifier = Modifier.padding(top = RelatedEventTitleTopPadding),
                )
                NotificationRelatedEventLink(
                    event = event,
                    isParticipating = isParticipatingInRelatedEvent,
                    onClick = { onRelatedEventClick(event.id) },
                )
            }
        }
    }
}

@Composable
private fun NotificationRelatedEventLink(
    event: NotificationRelatedEvent,
    isParticipating: Boolean,
    onClick: () -> Unit,
) {
    EventCard(
        time = "${event.startTime.toTimeOnly()}-${event.endTime.toTimeOnly()}",
        title = event.name,
        court = event.venue,
        isLive = isEventLive(event.startTime, event.endTime),
        isParticipating = isParticipating,
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp),
    )
}
private fun String.toIsoDateTime(): String =
    this.replace(" ", "T")
        .let { if (it.contains("+") || it.endsWith("Z")) it else "${it}Z" }

internal fun String.toNotificationDateTime(
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): String = runCatching {
    val dateTime = Instant.parse(this.toIsoDateTime()).toLocalDateTime(timeZone)
    "${dateTime.year}/${(dateTime.month.ordinal + 1).toTwoDigits()}/${dateTime.day.toTwoDigits()} " +
            "${dateTime.hour}:${dateTime.minute.toTwoDigits()}"
}.getOrElse { this }

private fun Int.toTwoDigits(): String = toString().padStart(2, '0')

internal fun String.toTimeOnly(
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): String = runCatching {
    val dateTime = Instant.parse(this.toIsoDateTime()).toLocalDateTime(timeZone)
    "${dateTime.hour}:${dateTime.minute.toTwoDigits()}"
}.getOrElse { this }

internal fun isEventLive(
    startTime: String,
    endTime: String,
    now: Instant = Clock.System.now(),
): Boolean = runCatching {
    val start = Instant.parse(startTime.toIsoDateTime())
    val end = Instant.parse(endTime.toIsoDateTime())
    now in start..end
}.getOrElse { false }
