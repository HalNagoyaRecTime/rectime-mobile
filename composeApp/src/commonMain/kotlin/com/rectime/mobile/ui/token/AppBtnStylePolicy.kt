package com.rectime.mobile.ui.token

import androidx.compose.runtime.Composable
import com.rectime.mobile.ui.theme.AppBtnVisualStyle

data class AppBtnStylePolicy(
    val defaultVisualStyle: AppBtnVisualStyle,
)

@Composable
expect fun rememberPlatformBtnStylePolicy(): AppBtnStylePolicy
