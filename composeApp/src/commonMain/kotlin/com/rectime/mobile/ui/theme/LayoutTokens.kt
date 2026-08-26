package com.rectime.mobile.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class AppLayout(
    val screenHorizontalPadding: Dp = screenHorizontalPaddingFor(0.dp),
    val headerAction: Dp = 44.dp,
    val headerDetailAction: Dp = 50.dp,
    val headerEdgeFade: Dp = 70.dp,
    val headerSpacing: Dp = 12.dp,
    val bottomTabMinHeight: Dp = 44.dp,
    val rootBottomNavigationInset: Dp = 90.dp,
    val bottomInsetMin: Dp = 14.dp,
)

fun screenHorizontalPaddingFor(widthDp: Dp): Dp = when {
    widthDp >= 840.dp -> 32.dp
    widthDp >= 600.dp -> 24.dp
    else -> 16.dp
}

val LocalAppLayout = staticCompositionLocalOf { AppLayout() }
