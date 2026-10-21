package com.rectime.mobile.ui.component

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.rectime.mobile.ui.theme.AppTheme
import com.rectime.mobile.ui.theme.ThemeStateHolder
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class PushHeaderTitleUiTest {
    @Test
    fun hiddenTitleCanAppearAndDisappearWithoutRemovingBackButton() = runComposeUiTest {
        val visible = mutableStateOf(false)
        var backs = 0
        setContent {
            AppTheme(ThemeStateHolder()) {
                PushHeader("集合場所変更のお知らせ", onBack = { backs++ }, titleVisible = visible.value)
            }
        }
        onNodeWithText("集合場所変更のお知らせ").assertDoesNotExist()
        onNodeWithContentDescription("戻る").assertIsDisplayed().performClick()
        assertEquals(1, backs)
        runOnIdle { visible.value = true }
        onNodeWithText("集合場所変更のお知らせ").assertIsDisplayed()
        runOnIdle { visible.value = false }
        onNodeWithText("集合場所変更のお知らせ").assertDoesNotExist()
        onNodeWithContentDescription("戻る").assertIsDisplayed()
    }

    @Test
    fun existingScreensKeepTheirTitleByDefault() = runComposeUiTest {
        setContent {
            AppTheme(ThemeStateHolder()) { PushHeader("既存画面", onBack = {}) }
        }
        onNodeWithText("既存画面").assertIsDisplayed()
        onNodeWithContentDescription("戻る").assertIsDisplayed()
    }
}
