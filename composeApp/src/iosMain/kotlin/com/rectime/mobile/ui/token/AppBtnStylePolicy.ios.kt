package com.rectime.mobile.ui.token

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.rectime.mobile.ui.theme.ButtonVisualStyle
import platform.UIKit.UIDevice

@Composable
actual fun rememberPlatformBtnStylePolicy(): ButtonStylePolicy {
    val majorVersion = remember {
        UIDevice.currentDevice.systemVersion
            .substringBefore('.')
            .toIntOrNull() ?: 0
    }
    val visualStyle = if (majorVersion >= 26) {
        ButtonVisualStyle.LiquidGlass
    } else {
        ButtonVisualStyle.SimpleBlur
    }
    return remember(visualStyle) {
        ButtonStylePolicy(defaultVisualStyle = visualStyle)
    }
}
