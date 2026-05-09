package com.rectime.mobile.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf

enum class AppBtnVisualStyle {
    SimpleBlur,
    LiquidGlass,
}

data class AppBtnTokens(
    val defaultVisualStyle: AppBtnVisualStyle = AppBtnVisualStyle.SimpleBlur,
)

val LocalAppBtnTokens = staticCompositionLocalOf { AppBtnTokens() }
