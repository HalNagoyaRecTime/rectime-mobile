package com.rectime.mobile.ui.theme

import androidx.compose.ui.graphics.Color

data class AppColorTokens(
    val navigationBackground: Color,
    val navigationActive: Color,
    val navigationInactive: Color,
    val navigationSurface: Color,
    val navigationScrim: Color,
    val navigationShadow: Color,
    val sheetBackground: Color,
    val sheetHandle: Color,
    val surfacePrimary: Color,
    val surfaceMuted: Color,
    val surfaceAccent: Color,
    val surfaceAccentStrong: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val textOnAccent: Color,
    val borderSubtle: Color,
    val borderStrong: Color,
    val overlayBackdrop: Color,
)

private val defaultLight = AppColorTokens(
    navigationBackground = Color(0xFFFFFFFF),
    navigationActive = Color(0xFF1C1D22),
    navigationInactive = Color(0xFF8A8F9C),
    navigationSurface = Color(0xFFFDFDFF),
    navigationScrim = Color(0xFF000000).copy(alpha = 0.12f),
    navigationShadow = Color(0x33000000),
    sheetBackground = Color(0xFFFFFFFF),
    sheetHandle = Color(0xFFCED1DA),
    surfacePrimary = Color(0xFFE0E1E5),
    surfaceMuted = Color(0xFFECEEFA),
    surfaceAccent = Color(0xFFE0EAFF),
    surfaceAccentStrong = Color(0xFF4169E1),
    textPrimary = Color(0xFF20222A),
    textSecondary = Color(0xFF4E5565),
    textMuted = Color(0xFF6F7687),
    textOnAccent = Color(0xFFFFFFFF),
    borderSubtle = Color(0xFFE2E5ED),
    borderStrong = Color(0xFFB8BECA),
    overlayBackdrop = Color(0xCC0D1018),
)

internal fun appColors(themeId: ThemeId): AppColorTokens = when (themeId) {
    ThemeId.Default -> defaultLight
    ThemeId.Blue2024 -> defaultLight
}
