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
    navigationBackground = Color(0xFFF7F7FA),
    navigationActive = Color(0xFF1C1D22),
    navigationInactive = Color(0xFF8A8F9C),
    navigationSurface = Color(0xFFFDFDFF),
    navigationScrim = Color(0xFF000000).copy(alpha = 0.12f),
    navigationShadow = Color(0x33000000),
    sheetBackground = Color(0xFFFFFFFF),
    sheetHandle = Color(0xFFCED1DA),
    surfacePrimary = Color(0xFFF5F6FA),
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

private val defaultDark = AppColorTokens(
    navigationBackground = Color(0xFF101217),
    navigationActive = Color(0xFFE6E9F2),
    navigationInactive = Color(0xFF9097AA),
    navigationSurface = Color(0xFF181B23),
    navigationScrim = Color(0xFF000000).copy(alpha = 0.22f),
    navigationShadow = Color(0x66000000),
    sheetBackground = Color(0xFF171A21),
    sheetHandle = Color(0xFF5A6273),
    surfacePrimary = Color(0xFF0F1218),
    surfaceMuted = Color(0xFF161B25),
    surfaceAccent = Color(0xFF1D2A46),
    surfaceAccentStrong = Color(0xFF6C8DFF),
    textPrimary = Color(0xFFE8EBF3),
    textSecondary = Color(0xFFBEC5D5),
    textMuted = Color(0xFF8E96A7),
    textOnAccent = Color(0xFF081027),
    borderSubtle = Color(0xFF252B38),
    borderStrong = Color(0xFF4B5468),
    overlayBackdrop = Color(0xE6080A11),
)

private val blueLight = AppColorTokens(
    navigationBackground = Color(0xFFF2F7FF),
    navigationActive = Color(0xFF0F325D),
    navigationInactive = Color(0xFF6D86A2),
    navigationSurface = Color(0xFFF8FBFF),
    navigationScrim = Color(0xFF0A2F57).copy(alpha = 0.12f),
    navigationShadow = Color(0x330A2F57),
    sheetBackground = Color(0xFFFDFEFF),
    sheetHandle = Color(0xFFC4D5EB),
    surfacePrimary = Color(0xFFF1F6FF),
    surfaceMuted = Color(0xFFE5EFFC),
    surfaceAccent = Color(0xFFD6E8FF),
    surfaceAccentStrong = Color(0xFF0067D7),
    textPrimary = Color(0xFF163A64),
    textSecondary = Color(0xFF375D88),
    textMuted = Color(0xFF5B7EA3),
    textOnAccent = Color(0xFFFFFFFF),
    borderSubtle = Color(0xFFD8E5F6),
    borderStrong = Color(0xFFA8C4E6),
    overlayBackdrop = Color(0xCC04162B),
)

private val blueDark = AppColorTokens(
    navigationBackground = Color(0xFF09111E),
    navigationActive = Color(0xFFCAE2FF),
    navigationInactive = Color(0xFF809BC1),
    navigationSurface = Color(0xFF0F1A2A),
    navigationScrim = Color(0xFF000000).copy(alpha = 0.24f),
    navigationShadow = Color(0x66000000),
    sheetBackground = Color(0xFF0E1828),
    sheetHandle = Color(0xFF4E6482),
    surfacePrimary = Color(0xFF070F1B),
    surfaceMuted = Color(0xFF0D1625),
    surfaceAccent = Color(0xFF123054),
    surfaceAccentStrong = Color(0xFF1E8BFF),
    textPrimary = Color(0xFFE2EEFF),
    textSecondary = Color(0xFFB5CAE8),
    textMuted = Color(0xFF7D97B7),
    textOnAccent = Color(0xFF00162C),
    borderSubtle = Color(0xFF172840),
    borderStrong = Color(0xFF355374),
    overlayBackdrop = Color(0xDE01060E),
)

internal fun appColors(themeId: ThemeId, dark: Boolean): AppColorTokens = when (themeId) {
    ThemeId.Default -> if (dark) defaultDark else defaultLight
    ThemeId.Blue2024 -> if (dark) blueDark else blueLight
}
