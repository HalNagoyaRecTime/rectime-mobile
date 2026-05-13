package com.rectime.mobile.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class AppRadius(
    val none: Dp = 0.dp,
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 20.dp,
    val xxl: Dp = 24.dp,
    val card: Dp = 16.dp,
    val sheet: Dp = 22.dp,
    val full: Dp = 9999.dp,
)

val LocalAppRadius = staticCompositionLocalOf { AppRadius() }
