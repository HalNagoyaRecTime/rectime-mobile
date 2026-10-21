package com.rectime.mobile.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import com.rectime.mobile.ui.token.rememberPlatformBtnStylePolicy

enum class ThemeId {
    Default,
    Blue2024,
}

class ThemeStateHolder(
    initialThemeId: ThemeId = ThemeId.Default,
) {
    private var _themeId by mutableStateOf(initialThemeId)
    val themeId: ThemeId get() = _themeId

    fun setThemeId(next: ThemeId) {
        _themeId = next
    }
}

private val LocalAppColors = staticCompositionLocalOf<AppColorTokens> {
    error("App colors not provided")
}

@Composable
private fun appTypography(): Typography {
    val fontFamily = notoSansJpFontFamily()
    return remember(fontFamily) {
        val base = Typography()
        base.copy(
            displayLarge = base.displayLarge.copy(fontFamily = fontFamily),
            displayMedium = base.displayMedium.copy(fontFamily = fontFamily),
            displaySmall = base.displaySmall.copy(fontFamily = fontFamily),
            headlineLarge = base.headlineLarge.copy(fontFamily = fontFamily),
            headlineMedium = base.headlineMedium.copy(fontFamily = fontFamily),
            headlineSmall = base.headlineSmall.copy(fontFamily = fontFamily),
            titleLarge = base.titleLarge.copy(fontFamily = fontFamily),
            titleMedium = base.titleMedium.copy(fontFamily = fontFamily),
            titleSmall = base.titleSmall.copy(fontFamily = fontFamily),
            bodyLarge = base.bodyLarge.copy(fontFamily = fontFamily),
            bodyMedium = base.bodyMedium.copy(fontFamily = fontFamily),
            bodySmall = base.bodySmall.copy(fontFamily = fontFamily),
            labelLarge = base.labelLarge.copy(fontFamily = fontFamily),
            labelMedium = base.labelMedium.copy(fontFamily = fontFamily),
            labelSmall = base.labelSmall.copy(fontFamily = fontFamily),
        )
    }
}

@Composable
fun AppTheme(
    themeStateHolder: ThemeStateHolder,
    content: @Composable () -> Unit,
) {
    val colors = appColors(themeStateHolder.themeId)
    val material = lightColorScheme(
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
    val buttonStylePolicy = rememberPlatformBtnStylePolicy()
    val buttonTokens = remember(buttonStylePolicy.defaultVisualStyle) {
        ButtonTokens(defaultVisualStyle = buttonStylePolicy.defaultVisualStyle)
    }
    val windowInfo = LocalWindowInfo.current
    val density = LocalDensity.current
    val screenHorizontalPadding = remember(windowInfo.containerSize.width) {
        screenHorizontalPaddingFor(with(density) { windowInfo.containerSize.width.toDp() })
    }

    CompositionLocalProvider(
        LocalAppColors provides colors,
        LocalAppSpacing provides AppSpacing(),
        LocalAppRadius provides AppRadius(),
        LocalAppLayout provides AppLayout(screenHorizontalPadding = screenHorizontalPadding),
        LocalButtonTokens provides buttonTokens,
    ) {
        MaterialTheme(
            colorScheme = material,
            typography = appTypography(),
            content = content,
        )
    }
}

object AppTheme {
    val colors: AppColorTokens
        @Composable
        @ReadOnlyComposable
        get() = LocalAppColors.current

    val spacing: AppSpacing
        @Composable
        @ReadOnlyComposable
        get() = LocalAppSpacing.current

    val radius: AppRadius
        @Composable
        @ReadOnlyComposable
        get() = LocalAppRadius.current

    val layout: AppLayout
        @Composable
        @ReadOnlyComposable
        get() = LocalAppLayout.current

    val buttons: ButtonTokens
        @Composable
        @ReadOnlyComposable
        get() = LocalButtonTokens.current
}
