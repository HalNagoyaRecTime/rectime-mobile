package com.rectime.mobile.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceAtMost
import androidx.compose.ui.util.lerp
import com.rectime.mobile.ui.theme.AppTheme
import com.rectime.mobile.ui.theme.ButtonVisualStyle
import com.kashif_e.backdrop.drawBackdrop
import com.kashif_e.backdrop.effects.blur
import com.kashif_e.backdrop.effects.lens
import com.kashif_e.backdrop.effects.vibrancy
import com.kashif_e.backdrop.highlight.Highlight
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh

@Composable
fun AppIconButton(
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    color: Color = AppTheme.colors.navigationBackground.copy(alpha = 0.5f),
    visualStyle: ButtonVisualStyle = AppTheme.buttons.defaultVisualStyle,
    sfSymbol: String? = null,
    content: @Composable (() -> Unit)? = null
) {
    val isGlass = visualStyle == ButtonVisualStyle.LiquidGlass && isLiquidGlassAvailable

    if (isGlass && sfSymbol != null) {
        val glassBleed = 40.dp
        Box(
            modifier = modifier.size(AppTheme.layout.headerAction),
            contentAlignment = Alignment.Center,
        ) {
            GlassNativeButton(
                sfSymbol = sfSymbol,
                onClick = onClick,
                modifier = Modifier.requiredSize(AppTheme.layout.headerAction + glassBleed * 2),
            )
        }
    } else {
        val backdrop = LocalScreenBackdrop.current
        val isKmpGlass = visualStyle == ButtonVisualStyle.KMPGlass && backdrop != null

        val interactionSource = remember { MutableInteractionSource() }
        val isPressed by interactionSource.collectIsPressedAsState()
        val scale by animateFloatAsState(
            targetValue = if (isPressed) 0.88f else 1.0f,
            animationSpec = spring(stiffness = 500f, dampingRatio = 0.8f),
        )

        val tapModifier = if (onClick != null) {
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

        if (isKmpGlass) {
            val animationScope = rememberCoroutineScope()
            val highlight = remember(animationScope) { InteractiveHighlight(animationScope) }

            Box(
                modifier = modifier
                    .size(AppTheme.layout.headerAction)
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { CircleShape },
                        effects = {
                            vibrancy()
                            blur(2.dp.toPx())
                            lens(12.dp.toPx(), 24.dp.toPx())
                        },
                        highlight = { Highlight.Ambient },
                        layerBlock = {
                            val progress = highlight.pressProgress
                            // タップで約20%拡大
                            val baseScale = lerp(1f, 1.2f, progress)
                            // 最大移動量をボタン径の30%に制限
                            val maxOffset = size.minDimension * 0.3f
                            val offset = highlight.offset
                            // initialDerivative を小さくして序盤の抵抗を強める
                            translationX = maxOffset * tanh(0.02f * offset.x / maxOffset)
                            translationY = maxOffset * tanh(0.02f * offset.y / maxOffset)
                            // 引っ張った方向にだけ膨らむ
                            val maxDragScale = 4f.dp.toPx() / size.height
                            val angle = atan2(offset.y, offset.x)
                            scaleX = baseScale +
                                maxDragScale * abs(cos(angle) * offset.x / size.maxDimension) *
                                (size.width / size.height).fastCoerceAtMost(1f)
                            scaleY = baseScale +
                                maxDragScale * abs(sin(angle) * offset.y / size.maxDimension) *
                                (size.height / size.width).fastCoerceAtMost(1f)
                        },
                        onDrawSurface = {
                            drawCircle(color = Color.White.copy(alpha = 0.05f))
                        }
                    )
                    .then(highlight.modifier)
                    .then(highlight.gestureModifier)
                    .then(tapModifier),
                contentAlignment = Alignment.Center,
            ) {
                content?.invoke()
            }
        } else {
            Box(
                modifier = modifier
                    .size(AppTheme.layout.headerAction)
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .then(tapModifier),
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
}
