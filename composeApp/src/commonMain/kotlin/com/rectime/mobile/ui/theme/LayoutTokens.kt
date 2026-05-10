package com.rectime.mobile.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class AppLayout(
    val screenHorizontalPadding: Dp = screenHorizontalPaddingFor(0.dp),
    val headerAction: Dp = 44.dp,
    val bottomTabMinHeight: Dp = 54.dp,
    val rootBottomNavigationInset: Dp = 112.dp,
    val bottomInsetMin: Dp = 14.dp,
    val sideMenuRevealMin: Dp = 280.dp,
    val sideMenuRevealMax: Dp = 380.dp,
)

fun screenHorizontalPaddingFor(widthDp: Dp): Dp = when {
    widthDp >= 840.dp -> 32.dp
    widthDp >= 600.dp -> 24.dp
    else -> 16.dp
}

val LocalAppLayout = staticCompositionLocalOf { AppLayout() }
