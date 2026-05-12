package com.rectime.mobile.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf

enum class ButtonVisualStyle {
    SimpleBlur,
    LiquidGlass,
    KMPGlass,
}

data class ButtonTokens(
    val defaultVisualStyle: ButtonVisualStyle = ButtonVisualStyle.SimpleBlur,
)

val LocalButtonTokens = staticCompositionLocalOf { ButtonTokens() }
