package com.rectime.mobile.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class BounceBackgroundUiTest {
    private fun checkOffset(offset: Float) = runComposeUiTest {
        setContent {
            Box(Modifier.requiredSize(320.dp, 600.dp).background(Color.Magenta)) {
                Box(Modifier.fillMaxSize().graphicsLayer { translationY = offset }) {
                    BounceBackground(Color.Red, Color.Blue)
                }
            }
        }
        val pixels = onRoot().captureToImage().toPixelMap()
        assertEquals(Color.Red, pixels[pixels.width / 2, 20])
        assertEquals(Color.Blue, pixels[pixels.width / 2, pixels.height - 20])
    }

    @Test fun shortTitleBandDoesNotPaintBlueBelowBody() = runComposeUiTest {
        setContent {
            Box(Modifier.requiredSize(320.dp, 600.dp)) {
                BounceBackground(Color.Red, Color.Blue, topHeightPx = 100f)
            }
        }
        val pixels = onRoot().captureToImage().toPixelMap()
        assertEquals(Color.Red, pixels[pixels.width / 2, 20])
        assertEquals(Color.Blue, pixels[pixels.width / 2, 200])
        assertEquals(Color.Blue, pixels[pixels.width / 2, pixels.height - 20])
    }

    @Test fun scrolledAwayTitleBandLeavesOnlyBodyBackground() = runComposeUiTest {
        setContent { BounceBackground(Color.Red, Color.Blue, topHeightPx = 0f) }
        val pixels = onRoot().captureToImage().toPixelMap()
        assertEquals(Color.Blue, pixels[pixels.width / 2, 20])
    }

    @Test fun downwardBounceKeepsTopBackground() = checkOffset(120f)
    @Test fun upwardBounceKeepsBottomBackground() = checkOffset(-120f)
}
