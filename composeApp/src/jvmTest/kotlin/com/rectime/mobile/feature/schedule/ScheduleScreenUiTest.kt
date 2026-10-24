package com.rectime.mobile.feature.schedule

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import com.rectime.mobile.ui.theme.AppTheme
import com.rectime.mobile.ui.theme.ThemeStateHolder
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class ScheduleScreenUiTest {
    private fun ComposeUiTest.showSchedule(error: String?) = setContent {
        AppTheme(ThemeStateHolder()) {
            ScheduleScreenUI(
                nowMinute = -1,
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
    }

    @Test fun noErrorShowsTimeline() = runComposeUiTest {
        showSchedule(error = null)
        onNodeWithText("9:00").assertExists()
    }
}
