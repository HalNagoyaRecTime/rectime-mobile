package com.rectime.mobile.feature.calendar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.rectime.mobile.app.navigation.NavigationController
import com.rectime.mobile.app.navigation.Screen
import com.rectime.mobile.core.model.MockUser
import com.rectime.mobile.feature.detail.DetailScreen
import com.rectime.mobile.feature.notifications.NotificationsScreen
import com.rectime.mobile.ui.component.PressSurface
import com.rectime.mobile.ui.component.RootScreenScaffold
import com.rectime.mobile.ui.theme.AppTheme
import com.woowla.compose.icon.collections.fontawesome.fontawesome.SolidGroup
import com.woowla.compose.icon.collections.fontawesome.fontawesome.solid.Bell

private data class TimelineEvent(
    val title: String,
    val venue: String,
    val startMinuteOfDay: Int,
    val durationMinutes: Int,
    val lane: Int,
    val laneCount: Int,
)

object CalendarScreen : Screen {
    override val key: String = "calendar"

    @Composable
    override fun Content(navigationController: NavigationController) {
        CalendarScreenUI(
            onOpenMenu = { navigationController.openMenu() },
            onOpenNotifications = { navigationController.push(NotificationsScreen) },
            onOpenEventDetail = { navigationController.push(DetailScreen("calendar-event")) },
        )
    }
}

@Composable
private fun CalendarScreenUI(
    onOpenMenu: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenEventDetail: () -> Unit,
) {
    val events = listOf(
        TimelineEvent("U18 準決勝", "Aコート", startMinuteOfDay = 10 * 60, durationMinutes = 80, lane = 0, laneCount = 2),
        TimelineEvent("トップリーグ", "Bコート", startMinuteOfDay = 10 * 60 + 10, durationMinutes = 70, lane = 1, laneCount = 2),
        TimelineEvent("運営MTG", "会議室", startMinuteOfDay = 13 * 60 + 30, durationMinutes = 45, lane = 0, laneCount = 1),
    )
    val hourStart = 8
    val hourEnd = 22
    val hourHeight = 72.dp
    val nowMinute = 13 * 60 + 20

    RootScreenScaffold(
        title = "カレンダー",
        profile = MockUser.me,
        onOpenMenu = onOpenMenu,
        horizontalPadding = false,
        onTrailingClick = onOpenNotifications,
        trailing = {
            Icon(
                imageVector = SolidGroup.Bell,
                contentDescription = "通知",
                tint = AppTheme.colors.textPrimary,
                modifier = Modifier.size(20.dp),
            )
        },
    ) {
        item {
            val hPad = AppTheme.layout.screenHorizontalPadding
            Text(
                text = "4月28日・火曜日",
                color = AppTheme.colors.textSecondary,
                modifier = Modifier.padding(start = hPad, top = 12.dp, bottom = 10.dp),
            )

            Row(modifier = Modifier.fillMaxWidth().padding(start = hPad)) {
                Column(modifier = Modifier.width(56.dp)) {
                    for (hour in hourStart until hourEnd) {
                        Text(
                            text = "${hour.toString().padStart(2, '0')}:00",
                            color = AppTheme.colors.textMuted,
                            modifier = Modifier.height(hourHeight),
                        )
                    }
                }

                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .height(hourHeight * (hourEnd - hourStart))
                        .clip(RoundedCornerShape(topStart = 14.dp))
                        .background(AppTheme.colors.surfaceMuted),
                ) {
                    val borderSubtle = AppTheme.colors.borderSubtle
                    Canvas(modifier = Modifier.matchParentSize()) {
                        val totalHours = hourEnd - hourStart
                        val step = size.height / totalHours
                        repeat(totalHours + 1) { index ->
                            val y = index * step
                            drawLine(
                                color = borderSubtle,
                                start = Offset(0f, y),
                                end = Offset(size.width, y),
                                strokeWidth = 1f,
                            )
                        }
                    }

                    val containerWidth = maxWidth
                    events.forEach { event ->
                        val laneWidth = containerWidth / event.laneCount
                        val xOffset = laneWidth * event.lane
                        val startMinutes = event.startMinuteOfDay - hourStart * 60
                        val yOffset = hourHeight * (startMinutes / 60f)
                        val eventHeight = hourHeight * (event.durationMinutes / 60f)

                        PressSurface(
                            onClick = onOpenEventDetail,
                            modifier = Modifier
                                .width(laneWidth - 8.dp)
                                .height(eventHeight - 6.dp)
                                .padding(top = 4.dp)
                                .align(Alignment.TopStart)
                                .offset(x = xOffset + 4.dp, y = yOffset + 4.dp),
                            color = AppTheme.colors.surfaceAccent,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
                        ) {
                            Column {
                                Text(text = event.title, color = AppTheme.colors.textPrimary)
                                Text(text = event.venue, color = AppTheme.colors.textSecondary)
                            }
                        }
                    }

                    PressSurface(
                        onClick = onOpenEventDetail,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp),
                        color = AppTheme.colors.surfacePrimary,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Text(text = "+2", color = AppTheme.colors.textSecondary)
                    }

                    val nowOffset = hourHeight * ((nowMinute - hourStart * 60) / 60f)
                    val accentStrong = AppTheme.colors.surfaceAccentStrong
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .align(Alignment.TopStart)
                            .offset(y = nowOffset),
                    ) {
                        drawLine(
                            color = accentStrong,
                            start = Offset(0f, 0f),
                            end = Offset(size.width, 0f),
                            strokeWidth = 4f,
                            cap = StrokeCap.Round,
                        )
                    }
                }
            }
        }
    }
}
