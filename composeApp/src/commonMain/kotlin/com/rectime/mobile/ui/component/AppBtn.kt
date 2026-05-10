package com.rectime.mobile.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rectime.mobile.ui.theme.AppBtnVisualStyle
import com.rectime.mobile.ui.theme.AppTheme

enum class AppBtnSize {
    Sm,
    Md,
    Lg,
}

enum class AppBtnVariant {
    Default,
    Accent,
    Ghost,
}

data class AppBtnMetrics(
    val minHeight: Dp,
    val contentPadding: PaddingValues,
)

@Composable
fun AppBtn(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: AppBtnSize = AppBtnSize.Md,
    visualStyle: AppBtnVisualStyle = AppTheme.buttons.defaultVisualStyle,
    variant: AppBtnVariant = AppBtnVariant.Default,
    containerColor: Color = appBtnContainerColor(visualStyle, variant),
    borderColor: Color = appBtnBorderColor(visualStyle, variant),
    contentColor: Color = appBtnContentColor(variant),
    contentPadding: PaddingValues = appBtnMetrics(size).contentPadding,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.97f else 1f,
        animationSpec = spring(stiffness = 520f, dampingRatio = 0.84f),
        label = "appBtnPressScale",
    )

    val metrics = appBtnMetrics(size)
    val shape = CircleShape
    val effectiveContainerColor = if (enabled) containerColor else containerColor.copy(alpha = 0.6f)
    val effectiveContentColor = if (enabled) contentColor else contentColor.copy(alpha = 0.7f)
    val effectiveBorderColor = if (enabled) borderColor else borderColor.copy(alpha = borderColor.alpha * 0.6f)

    Box(
        modifier = modifier
            .graphicsLayer(scaleX = scale, scaleY = scale, shape = shape, clip = true)
            .defaultMinSize(minHeight = metrics.minHeight)
            .background(color = effectiveContainerColor, shape = shape)
            .border(width = 1.dp, color = effectiveBorderColor, shape = shape)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(contentPadding),
        contentAlignment = Alignment.Center,
    ) {
        if (visualStyle == AppBtnVisualStyle.LiquidGlass) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.24f),
                                Color.White.copy(alpha = 0.08f),
                            ),
                        ),
                    ),
            )
        }

        androidx.compose.material3.ProvideTextStyle(
            value = LocalTextStyle.current.copy(color = effectiveContentColor),
            content = content,
        )
    }
}

@Composable
private fun appBtnContainerColor(
    visualStyle: AppBtnVisualStyle,
    variant: AppBtnVariant,
): Color = when (variant) {
    AppBtnVariant.Default -> when (visualStyle) {
        AppBtnVisualStyle.SimpleBlur -> AppTheme.colors.navigationSurface.copy(alpha = 0.74f)
        AppBtnVisualStyle.LiquidGlass -> AppTheme.colors.navigationSurface.copy(alpha = 0.5f)
    }

    AppBtnVariant.Accent -> when (visualStyle) {
        AppBtnVisualStyle.SimpleBlur -> AppTheme.colors.surfaceAccent.copy(alpha = 0.88f)
        AppBtnVisualStyle.LiquidGlass -> AppTheme.colors.surfaceAccent.copy(alpha = 0.66f)
    }

    AppBtnVariant.Ghost -> Color.Transparent
}

@Composable
private fun appBtnBorderColor(
    visualStyle: AppBtnVisualStyle,
    variant: AppBtnVariant,
): Color = when (variant) {
    AppBtnVariant.Default -> when (visualStyle) {
        AppBtnVisualStyle.SimpleBlur -> AppTheme.colors.borderSubtle.copy(alpha = 0.72f)
        AppBtnVisualStyle.LiquidGlass -> AppTheme.colors.borderStrong.copy(alpha = 0.42f)
    }

    AppBtnVariant.Accent -> when (visualStyle) {
        AppBtnVisualStyle.SimpleBlur -> AppTheme.colors.surfaceAccentStrong.copy(alpha = 0.42f)
        AppBtnVisualStyle.LiquidGlass -> AppTheme.colors.surfaceAccentStrong.copy(alpha = 0.35f)
    }

    AppBtnVariant.Ghost -> AppTheme.colors.borderSubtle.copy(alpha = 0.5f)
}

@Composable
private fun appBtnContentColor(variant: AppBtnVariant): Color = when (variant) {
    AppBtnVariant.Default -> AppTheme.colors.textPrimary
    AppBtnVariant.Accent -> AppTheme.colors.textPrimary
    AppBtnVariant.Ghost -> AppTheme.colors.textSecondary
}

private fun appBtnMetrics(size: AppBtnSize): AppBtnMetrics = when (size) {
    AppBtnSize.Sm -> AppBtnMetrics(
        minHeight = 34.dp,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
    )

    AppBtnSize.Md -> AppBtnMetrics(
        minHeight = 42.dp,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    )

    AppBtnSize.Lg -> AppBtnMetrics(
        minHeight = 50.dp,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    )
}

