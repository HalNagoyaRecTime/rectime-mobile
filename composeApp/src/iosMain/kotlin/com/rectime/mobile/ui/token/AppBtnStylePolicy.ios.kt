package com.rectime.mobile.ui.token

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.rectime.mobile.ui.theme.AppBtnVisualStyle
import platform.UIKit.UIDevice

@Composable
actual fun rememberPlatformBtnStylePolicy(): AppBtnStylePolicy {
    val majorVersion = remember {
        UIDevice.currentDevice.systemVersion
            .substringBefore('.')
            .toIntOrNull() ?: 0
    }
    val visualStyle = if (majorVersion >= 26) {
        AppBtnVisualStyle.LiquidGlass
    } else {
        AppBtnVisualStyle.SimpleBlur
    }
    return remember(visualStyle) {
        AppBtnStylePolicy(defaultVisualStyle = visualStyle)
    }
}
