package com.rectime.mobile.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import com.rectime.mobile.ui.theme.AppTheme
import com.rectime.mobile.ui.theme.ButtonVisualStyle

@Composable
fun AppIconButton(
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    color: Color = AppTheme.colors.navigationBackground.copy(alpha = 0.5f),
    visualStyle: ButtonVisualStyle = AppTheme.buttons.defaultVisualStyle,
    content: @Composable (() -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1f,
        animationSpec = spring(stiffness = 500f, dampingRatio = 0.8f)
    )

    Box(
        modifier = modifier
            .size(AppTheme.layout.headerAction)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        onClick = onClick,
                        interactionSource = interactionSource,
                        indication = null
                    )
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    clip = true
                    shape = CircleShape
                }
                .background(color)
        ) {
            // TODO: LiquidGlass — UIKitInteropView で UIGlassEffect を埋め込む（iOS 26+）
            // if (visualStyle == ButtonVisualStyle.LiquidGlass) { ... }
        }
        content?.invoke()
    }
}
