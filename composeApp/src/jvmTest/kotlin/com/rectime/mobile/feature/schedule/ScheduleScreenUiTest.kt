package com.rectime.mobile.feature.schedule

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import com.rectime.mobile.ui.theme.AppTheme
import com.rectime.mobile.ui.theme.ThemeStateHolder
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class ScheduleScreenUiTest {
    private fun ComposeUiTest.showSchedule(
        error: String?,
        eventMinute: Int = -1,
        nowMinute: Int = 14 * 60,
        events: List<TimelineEvent> = emptyList(),
        onAccentColor: (Color) -> Unit = {},
    ) = setContent {
        AppTheme(ThemeStateHolder()) {
            onAccentColor(AppTheme.colors.themeColorFirst)
            ScheduleScreenUI(
                nowMinute = nowMinute,
                eventMinute = eventMinute,
                onOpenEventDetail = {},
                events = events,
                isLoading = false,
                isRefreshing = false,
                isUpdating = false,
                onRefresh = {},
                error = error,
            )
        }
    }

    @Test fun errorWithoutEventsHidesTimeline() = runComposeUiTest {
        showSchedule(error = "通信に失敗しました")
        onNodeWithText("通信に失敗しました").assertExists()
        onNodeWithText("9:00").assertDoesNotExist()
        onNodeWithTag("current-time-bar").assertDoesNotExist()
    }

    @Test fun noErrorShowsTimeline() = runComposeUiTest {
        showSchedule(error = null)
        onNodeWithText("9:00").assertExists()
    }

    @Test fun currentTimeBarShowsBeforeEventDay() = runComposeUiTest {
        showSchedule(error = null, eventMinute = -1)
        onNodeWithTag("current-time-bar").assertExists()
    }

    @Test fun currentTimeBarShowsAfterEventDay() = runComposeUiTest {
        showSchedule(error = null, eventMinute = 24 * 60 + 1)
        onNodeWithTag("current-time-bar").assertExists()
    }

    @Test fun currentTimeBarIsDrawnOverEventCard() = runComposeUiTest {
        var accentColor = Color.Unspecified
        val event = TimelineEvent(
            eventId = 1,
            title = "ドッジボール",
            venues = emptyList(),
            startMinuteOfDay = 9 * 60,
            durationMinutes = 60,
            lane = 0,
            laneCount = 1,
            startTimeLabel = "9:00",
            endTimeLabel = "10:00",
        )
        showSchedule(
            error = null,
            nowMinute = 9 * 60 + 30,
            events = listOf(event),
            onAccentColor = { accentColor = it },
        )

        val barBounds = onNodeWithTag("current-time-bar").getBoundsInRoot()
        val barCenterY = with(density) { ((barBounds.top + barBounds.bottom) / 2).toPx() }
        val pixels = onRoot().captureToImage().toPixelMap()
        assertEquals(accentColor, pixels[pixels.width / 2, barCenterY.toInt()])
    }
}
