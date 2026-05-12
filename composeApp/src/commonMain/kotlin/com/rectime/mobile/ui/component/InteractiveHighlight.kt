package com.rectime.mobile.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class InteractiveHighlight(
    val animationScope: CoroutineScope,
    val position: (size: Size, offset: Offset) -> Offset = { _, offset -> offset }
) {
    private val pressProgressSpec = spring<Float>(0.5f, 300f, 0.001f)
    private val positionSpec = spring(0.5f, 300f, Offset.VisibilityThreshold)

    private val pressProgressAnimation = Animatable(0f, 0.001f)
    private val positionAnimation =
        Animatable(Offset.Zero, Offset.VectorConverter, Offset.VisibilityThreshold)

    private var startPosition = Offset.Zero
    val pressProgress: Float get() = pressProgressAnimation.value
    val offset: Offset get() = positionAnimation.value - startPosition

    val modifier: Modifier = Modifier.drawWithContent {
        val progress = pressProgressAnimation.value
        if (progress > 0f) {
            drawRect(Color.White.copy(0.25f * progress), blendMode = BlendMode.Plus)
        }
        drawContent()
    }

    val gestureModifier: Modifier = Modifier.pointerInput(animationScope) {
        inspectDragGestures(
            onDragStart = { down ->
                startPosition = down.position
                animationScope.launch {
                    launch { pressProgressAnimation.animateTo(1f, pressProgressSpec) }
                    launch { positionAnimation.snapTo(startPosition) }
                }
            },
            onDragEnd = {
                animationScope.launch {
                    launch { pressProgressAnimation.animateTo(0f, pressProgressSpec) }
                    launch { positionAnimation.animateTo(startPosition, positionSpec) }
                }
            },
            onDragCancel = {
                animationScope.launch {
                    launch { pressProgressAnimation.animateTo(0f, pressProgressSpec) }
                    launch { positionAnimation.animateTo(startPosition, positionSpec) }
                }
            }
        ) { change, _ ->
            animationScope.launch { positionAnimation.snapTo(change.position) }
        }
    }
}
