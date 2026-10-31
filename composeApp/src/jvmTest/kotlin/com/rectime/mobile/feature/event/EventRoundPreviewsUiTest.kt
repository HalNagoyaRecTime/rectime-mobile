package com.rectime.mobile.feature.event

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.rectime.mobile.core.model.EventDetail
import com.rectime.mobile.core.model.Gathering
import com.rectime.mobile.ui.theme.AppTheme
import com.rectime.mobile.ui.theme.ThemeStateHolder
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class EventRoundPreviewsUiTest {
    private val event = EventDetail(1, "競技", emptyList(), "0900", "1000", "説明")

    @Test fun noGatheringsHidesEveryRoundSection() = runComposeUiTest {
        setContent { AppTheme(ThemeStateHolder()) { EventRoundPreviews(event, emptyList(), setOf(1)) } }
        onNodeWithText("出場予定").assertDoesNotExist()
        onNodeWithText("ラウンド").assertDoesNotExist()
        onNodeWithText("ラウンド別の集合情報").assertDoesNotExist()
    }

    @Test fun nonParticipantStillSeesRoundsWithoutPersonalCards() = runComposeUiTest {
        val gathering = Gathering(1, 1, 1, "09:00", 1, "競技", "体育館", "0910")
        setContent { AppTheme(ThemeStateHolder()) { EventRoundPreviews(event, listOf(gathering), emptySet()) } }
        onNodeWithText("出場予定").assertDoesNotExist()
        onNodeWithText("ラウンド").assertExists()
        onNodeWithText("全1回").assertExists()
        onNodeWithText("ラウンド別の集合情報").assertExists()
    }

    @Test fun missingGatheringSpotImageNotifiesInsteadOfOpeningViewer() = runComposeUiTest {
        val gathering = Gathering(1, 1, 1, "09:00", 1, "競技", "体育館", "0910", gatheringSpotImageUrl = null)
        var notified = 0
        setContent {
            AppTheme(ThemeStateHolder()) {
                EventRoundPreviews(event, listOf(gathering), setOf(1), onPlaceImageUnavailable = { notified++ })
            }
        }
        onAllNodesWithText("集合場所").onFirst().performClick()
        waitForIdle()
        assertEquals(1, notified)
        onNodeWithTag("image-viewer").assertDoesNotExist()
    }
}
