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
import androidx.compose.ui.unit.dp
import com.rectime.mobile.ui.theme.AppTheme
import com.rectime.mobile.ui.theme.ButtonVisualStyle

@Composable
fun AppIconButton(
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    color: Color = AppTheme.colors.navigationBackground.copy(alpha = 0.5f),
    visualStyle: ButtonVisualStyle = AppTheme.buttons.defaultVisualStyle,
    sfSymbol: String? = null,
    content: @Composable (() -> Unit)? = null
) {
    val isNativeGlassEnabled = LocalNativeGlassEnabled.current
    val isGlass = visualStyle == ButtonVisualStyle.LiquidGlass && isLiquidGlassAvailable && isNativeGlassEnabled

    if (isGlass && sfSymbol != null) {
        // iOS 26: 完全ネイティブ UIKit ボタン (glass + icon + touch 全て UIKit)
        // UIKit view を Compose ノードと同サイズにし CMP の z-order スライスが正確に機能するようにする
        GlassNativeButton(
            sfSymbol = sfSymbol,
            onClick = onClick,
            modifier = modifier.size(AppTheme.layout.headerAction),
        )
    } else {
        // 非 iOS 26: Compose 実装
        val interactionSource = remember { MutableInteractionSource() }
        val isPressed by interactionSource.collectIsPressedAsState()
        val scale by animateFloatAsState(
            targetValue = if (isPressed) 0.88f else 1.0f,
            animationSpec = spring(stiffness = 500f, dampingRatio = 0.8f),
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
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        clip = true
                        shape = CircleShape
                    }
                    .background(color)
            )
            content?.invoke()
        }
    }
}
