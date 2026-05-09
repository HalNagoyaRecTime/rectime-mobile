package com.rectime.mobile.ui.token

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.rectime.mobile.ui.theme.AppBtnVisualStyle

@Composable
actual fun rememberPlatformBtnStylePolicy(): AppBtnStylePolicy {
    return remember {
        AppBtnStylePolicy(defaultVisualStyle = AppBtnVisualStyle.SimpleBlur)
    }
}
