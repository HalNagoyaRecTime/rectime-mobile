package com.rectime.mobile.feature.schedule

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import com.rectime.mobile.ui.theme.AppTheme
import com.rectime.mobile.ui.theme.ThemeStateHolder
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class ScheduleScreenUiTest {
    private fun ComposeUiTest.showSchedule(error: String?, eventMinute: Int = -1) = setContent {
        AppTheme(ThemeStateHolder()) {
            ScheduleScreenUI(
                nowMinute = 14 * 60,
                eventMinute = eventMinute,
                onOpenEventDetail = {},
                events = emptyList(),
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
}
