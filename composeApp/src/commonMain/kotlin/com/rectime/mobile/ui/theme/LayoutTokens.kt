package com.rectime.mobile.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class AppLayout(
    val headerAction: Dp = 44.dp,
    val bottomTabMinHeight: Dp = 54.dp,
    val rootBottomNavigationInset: Dp = 112.dp,
    val bottomInsetMin: Dp = 14.dp,
    val sideMenuRevealMin: Dp = 280.dp,
    val sideMenuRevealMax: Dp = 380.dp,
)

val LocalAppLayout = staticCompositionLocalOf { AppLayout() }
