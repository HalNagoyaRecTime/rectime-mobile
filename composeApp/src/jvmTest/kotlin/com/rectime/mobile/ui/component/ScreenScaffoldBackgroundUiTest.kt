package com.rectime.mobile.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.rectime.mobile.ui.theme.AppTheme
import com.rectime.mobile.ui.theme.ThemeStateHolder
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class ScreenScaffoldBackgroundUiTest {
    private fun checkBackground(push: Boolean, custom: Boolean) = runComposeUiTest {
        var expected = Color.Red
        setContent {
            AppTheme(ThemeStateHolder()) {
                if (!custom) expected = AppTheme.colors.commonBackground
                Box(Modifier.requiredSize(320.dp, 600.dp).background(AppTheme.colors.commonBackground)) {
                    val modifier = if (custom) Modifier.background(Color.Red) else Modifier
                    if (push) PushScreenScaffold("詳細", onBack = {}, modifier = modifier) {}
                    else RootScreenScaffold("設定", modifier = modifier) {}
                }
            }
        }
        val pixels = onRoot().captureToImage().toPixelMap()
        assertEquals(expected, pixels[pixels.width / 2, pixels.height * 3 / 4])
    }

    @Test fun rootPreservesScreenBackground() = checkBackground(push = false, custom = true)
    @Test fun pushPreservesScreenBackground() = checkBackground(push = true, custom = true)
    @Test fun rootKeepsDefaultBackground() = checkBackground(push = false, custom = false)
    @Test fun pushKeepsDefaultBackground() = checkBackground(push = true, custom = false)
}
