package com.rectime.mobile.ui.component

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.swipe
import androidx.compose.ui.geometry.Offset
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class ImageViewerUiTest {
    @Test
    fun closeButtonDismissesViewer() = runComposeUiTest {
        var closed = 0
        setContent { ImageViewerContent(ColorPainter(Color.Blue), "画像", { closed++ }) }
        onNodeWithContentDescription("画像").assertIsDisplayed()
        onNodeWithContentDescription("閉じる").performClick()
        waitUntil { closed == 1 }
        assertEquals(1, closed)
    }

    @Test
    fun downwardSwipeDismissesAfterTheImageLeavesTheViewport() = runComposeUiTest {
        var closed = 0
        setContent { ImageViewerContent(ColorPainter(Color.Blue), "画像", { closed++ }) }
        onNodeWithTag("image-viewer").performTouchInput {
            swipe(center, Offset(center.x, height.toFloat() - 1f), 300)
        }
        waitUntil { closed == 1 }
        assertEquals(1, closed)
    }

    @Test
    fun shortSlowSwipeReturnsWithoutDismissing() = runComposeUiTest {
        var closed = 0
        setContent { ImageViewerContent(ColorPainter(Color.Blue), "画像", { closed++ }) }
        onNodeWithTag("image-viewer").performTouchInput {
            swipe(center, center + Offset(0f, 40f), 1_000)
        }
        waitForIdle()
        onNodeWithContentDescription("画像").assertIsDisplayed()
        assertEquals(0, closed)
    }

    @Test
    fun tappingImageTogglesControlsInsteadOfClosing() = runComposeUiTest {
        var closed = 0
        setContent { ImageViewerContent(ColorPainter(Color.Blue), "画像", { closed++ }) }
        onNodeWithTag("image-viewer").performTouchInput { click() }
        waitUntil { onAllNodesWithContentDescription("閉じる").fetchSemanticsNodes(atLeastOneRootRequired = false).isEmpty() }
        onNodeWithContentDescription("閉じる").assertDoesNotExist()
        onNodeWithTag("image-viewer").performTouchInput { click() }
        waitUntil { onAllNodesWithContentDescription("閉じる").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
        onNodeWithContentDescription("閉じる").assertIsDisplayed()
        assertEquals(0, closed)
    }

    @Test
    fun doubleTapKeepsImageAndControlsVisible() = runComposeUiTest {
        var closed = 0
        setContent { ImageViewerContent(ColorPainter(Color.Blue), "画像", { closed++ }) }
        onNodeWithTag("image-viewer").performTouchInput { doubleClick() }
        onNodeWithContentDescription("画像").assertIsDisplayed()
        onNodeWithContentDescription("閉じる").assertIsDisplayed()
        onNodeWithTag("image-viewer").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "拡大率 2.5"))
        onNodeWithTag("image-viewer").performTouchInput { doubleClick() }
        onNodeWithTag("image-viewer").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "拡大率 1.0"))
        assertEquals(0, closed)
    }
}
