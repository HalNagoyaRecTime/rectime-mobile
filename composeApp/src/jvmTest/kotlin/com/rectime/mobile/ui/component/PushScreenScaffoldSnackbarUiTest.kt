package com.rectime.mobile.ui.component

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import com.rectime.mobile.ui.theme.AppTheme
import com.rectime.mobile.ui.theme.ThemeStateHolder
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class PushScreenScaffoldSnackbarUiTest {
    @Test fun pushScaffoldShowsSnackbarMessage() = runComposeUiTest {
        setContent {
            AppTheme(ThemeStateHolder()) {
                val snackbarHostState = remember { SnackbarHostState() }
                LaunchedEffect(Unit) { snackbarHostState.showSnackbar("お知らせ") }
                PushScreenScaffold("詳細", onBack = {}, snackbarHostState = snackbarHostState) {}
            }
        }
        waitUntil { onAllNodesWithText("お知らせ").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("お知らせ").assertExists()
    }
}
