package com.rectime.mobile.ui.token

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.rectime.mobile.ui.theme.ButtonVisualStyle

@Composable
actual fun rememberPlatformBtnStylePolicy(): ButtonStylePolicy {
    return remember {
        ButtonStylePolicy(defaultVisualStyle = ButtonVisualStyle.KMPGlass)
    }
}
