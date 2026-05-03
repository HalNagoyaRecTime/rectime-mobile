package com.rectime.mobile

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

enum class ThemeMode {
    System,
    Light,
    Dark,
}

enum class ThemeId {
    Default,
    Blue2024,
}

object LayoutTokens {
    val headerAction = 44.dp
    val bottomTabMinHeight = 54.dp
    val rootBottomNavigationInset = 112.dp
    val bottomInsetMin = 14.dp
    const val sideMenuRevealRatio = 0.82f
    val sideMenuRevealMin = 280.dp
    val sideMenuRevealMax = 380.dp
    const val sheetDismissProgress = 0.18f

    const val backDismissProgress = 0.45f
    const val backDismissVelocityX = 700f
    const val sheetDismissVelocityY = 1000f
    const val pushDismissDurationMs = 180
    const val menuOpenCloseDamping = 24f
    const val menuOpenCloseStiffness = 220f
}

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

private fun appColors(themeId: ThemeId, dark: Boolean): AppColorTokens {
    return when (themeId) {
        ThemeId.Default -> if (dark) defaultDark else defaultLight
        ThemeId.Blue2024 -> if (dark) blueDark else blueLight
    }
}

class ThemeStateHolder(
    initialMode: ThemeMode = ThemeMode.System,
    initialThemeId: ThemeId = ThemeId.Default,
) {
    private var _mode by mutableStateOf(initialMode)
    val mode: ThemeMode get() = _mode

    private var _themeId by mutableStateOf(initialThemeId)
    val themeId: ThemeId get() = _themeId

    fun setMode(next: ThemeMode) {
        _mode = next
    }

    fun setThemeId(next: ThemeId) {
        _themeId = next
    }
}

private val LocalAppColors = staticCompositionLocalOf<AppColorTokens> {
    error("App colors not provided")
}

@Composable
fun AppTheme(
    themeStateHolder: ThemeStateHolder,
    content: @Composable () -> Unit,
) {
    val dark = when (themeStateHolder.mode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val colors = appColors(themeStateHolder.themeId, dark)
    val material = if (dark) {
        darkColorScheme(
            primary = colors.surfaceAccentStrong,
            onPrimary = colors.textOnAccent,
            secondary = colors.surfaceAccent,
            onSecondary = colors.textPrimary,
            background = colors.surfacePrimary,
            onBackground = colors.textPrimary,
            surface = colors.surfacePrimary,
            onSurface = colors.textPrimary,
            surfaceVariant = colors.surfaceMuted,
            onSurfaceVariant = colors.textSecondary,
            outline = colors.borderStrong,
            scrim = colors.overlayBackdrop,
        )
    } else {
        lightColorScheme(
            primary = colors.surfaceAccentStrong,
            onPrimary = colors.textOnAccent,
            secondary = colors.surfaceAccent,
            onSecondary = colors.textPrimary,
            background = colors.surfacePrimary,
            onBackground = colors.textPrimary,
            surface = colors.surfacePrimary,
            onSurface = colors.textPrimary,
            surfaceVariant = colors.surfaceMuted,
            onSurfaceVariant = colors.textSecondary,
            outline = colors.borderStrong,
            scrim = colors.overlayBackdrop,
        )
    }

    CompositionLocalProvider(LocalAppColors provides colors) {
        MaterialTheme(
            colorScheme = material,
            content = content,
        )
    }
}

object AppTheme {
    val colors: AppColorTokens
        @Composable
        @ReadOnlyComposable
        get() = LocalAppColors.current
}

