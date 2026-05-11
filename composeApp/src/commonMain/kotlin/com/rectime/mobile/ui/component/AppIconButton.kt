package com.rectime.mobile.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
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
import androidx.compose.ui.input.pointer.pointerInput
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
    val isGlass = visualStyle == ButtonVisualStyle.LiquidGlass && isLiquidGlassAvailable
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // iOS 26 glass: 拡大 (critically damped で undershoot なし)、それ以外: 縮小
    val scale by animateFloatAsState(
        targetValue = when {
            isPressed && isGlass -> 1.1f
            isPressed -> 0.88f
            else -> 1.0f
        },
        animationSpec = if (isGlass)
            spring(stiffness = 600f, dampingRatio = 1.0f)
        else
            spring(stiffness = 500f, dampingRatio = 0.8f)
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
                    // detectTapGestures で isPressed を指がどこにあっても維持する
                    Modifier.pointerInput(onClick, interactionSource) {
                        detectTapGestures(
                            onPress = { position ->
                                val press = PressInteraction.Press(position)
                                interactionSource.emit(press)
                                if (tryAwaitRelease()) {
                                    interactionSource.emit(PressInteraction.Release(press))
                                    onClick()
                                } else {
                                    interactionSource.emit(PressInteraction.Cancel(press))
                                }
                            }
                        )
                    }
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
                .background(if (isGlass) Color.Transparent else color)
        ) {
            if (isGlass) {
                GlassBackground(modifier = Modifier.matchParentSize(), isPressed = isPressed)
            }
        }
        content?.invoke()
    }
}
