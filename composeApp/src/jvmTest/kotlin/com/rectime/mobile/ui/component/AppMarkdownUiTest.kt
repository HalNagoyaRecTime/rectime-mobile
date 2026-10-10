package com.rectime.mobile.ui.component

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.runComposeUiTest
import com.rectime.mobile.ui.theme.AppTheme
import com.rectime.mobile.ui.theme.ThemeStateHolder
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class AppMarkdownUiTest {
    // 見出しが解釈済みになるまで待ち、loadingの生テキストを誤検証しない。
    private fun assertBody(source: String, expected: String) = runComposeUiTest {
        setContent { AppTheme(ThemeStateHolder()) { AppMarkdown("# 見出し\n\n$source") } }
        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithText("見出し").fetchSemanticsNodes().isNotEmpty() &&
                onAllNodesWithText(expected).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test fun plainTextUsesSoftBreak() = runComposeUiTest {
        setContent { AppTheme(ThemeStateHolder()) { AppMarkdown("first\nsecond") } }
        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithText("first second").fetchSemanticsNodes().isNotEmpty()
        }
    }
    @Test fun twoSpacesKeepHardBreak() = assertBody("first  \nsecond", "first\nsecond")
    @Test fun backslashKeepsHardBreak() = assertBody("first\\\nsecond", "first\nsecond")
    @Test fun blankLineSeparatesParagraphs() = assertBody("first\n\nsecond", "second")
    @Test fun indentationRemainsCode() = assertBody("    code", "code")
}
