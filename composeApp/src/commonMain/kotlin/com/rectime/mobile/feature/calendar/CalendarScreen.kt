package com.rectime.mobile.feature.calendar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rectime.mobile.app.navigation.NavigationController
import com.rectime.mobile.app.navigation.Screen
import com.rectime.mobile.feature.schedule.CompetitionScheduleDetailScreen
import com.rectime.mobile.ui.component.PressSurface
import com.rectime.mobile.ui.component.RootScreenScaffold
import com.rectime.mobile.ui.theme.AppTheme


object CalendarScreen : Screen {
    override val key: String = "calendar"

    @Composable
    override fun Content(navigationController: NavigationController) {
        val viewModel = viewModel { CalendarViewModel() }
        val nowMinute by viewModel.nowMinute.collectAsStateWithLifecycle()
        val events by viewModel.events

        LaunchedEffect(Unit) {
            viewModel.fetchEvents()
        }

        CalendarScreenUI(
            nowMinute = nowMinute,
            onOpenEventDetail = { eventId -> navigationController.push(CompetitionScheduleDetailScreen(eventId)) },
            events = events,
            isLoading = viewModel.isLoading,
            error = viewModel.error,
        )
    }
}

@Composable
private fun CalendarScreenUI(
    nowMinute: Int,
    onOpenEventDetail: (Int) -> Unit,
    events: List<TimelineEvent>,
    isLoading: Boolean,
    error: String?,
) {
    val hourStart = 8
    val hourEnd = 22
    val hourHeight = 72.dp
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(error) {
        if (error != null) {
            snackbarHostState.showSnackbar(error)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        RootScreenScaffold(
            title = "カレンダー",
            horizontalPadding = false,
            snackbarHostState = snackbarHostState,
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
                            onClick = { onOpenEventDetail(event.eventId) },
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
                                Text(
                                    text = "${event.startTimeLabel}〜${event.endTimeLabel}",
                                    color = AppTheme.colors.textSecondary,
                                )
                                Text(text = event.venue, color = AppTheme.colors.textSecondary)
                            }
                        }
                    }

                    PressSurface(
                        onClick = {/* 後で実装 */},
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp),
                        color = AppTheme.colors.surfacePrimary,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Text(text = "+2", color = AppTheme.colors.textSecondary)
                    }

                        if (nowMinute in (hourStart * 60)..(hourEnd * 60)) {
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
        if (isLoading){
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ){
                CircularProgressIndicator(
                    modifier = Modifier.size(56.dp),
                    strokeWidth = 5.dp,
                )
            }
        }
    }
}
