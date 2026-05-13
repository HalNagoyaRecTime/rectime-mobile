package com.rectime.mobile.ui.token

import androidx.compose.runtime.Composable
import com.rectime.mobile.ui.theme.ButtonVisualStyle

data class ButtonStylePolicy(
    val defaultVisualStyle: ButtonVisualStyle,
)

@Composable
expect fun rememberPlatformBtnStylePolicy(): ButtonStylePolicy
