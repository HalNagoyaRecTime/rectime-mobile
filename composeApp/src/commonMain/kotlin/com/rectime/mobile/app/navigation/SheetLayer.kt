package com.rectime.mobile.app.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.IntOffset
import com.rectime.mobile.ui.theme.AppTheme
import com.rectime.mobile.ui.token.GestureTokens
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun SheetLayer(
    state: NavigationState,
    navigationController: NavigationController,
    containerHeightPx: Float,
) {
    val sheetEntry = state.sheet ?: return
    var offsetPx by remember(sheetEntry.key) { mutableFloatStateOf(containerHeightPx * 0.35f) }
    var handledDismissRequestId by remember { mutableLongStateOf(state.sheetDismissRequestId) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(sheetEntry.key, containerHeightPx) {
        val animator = Animatable(offsetPx)
        animator.animateTo(
            targetValue = 0f,
            animationSpec = spring(
                dampingRatio = 0.9f,
                stiffness = GestureTokens.menuOpenCloseStiffness,
            ),
        ) {
            offsetPx = value
        }
    }

    LaunchedEffect(state.sheetDismissRequestId, sheetEntry.key, containerHeightPx) {
        if (state.sheetDismissRequestId == handledDismissRequestId) return@LaunchedEffect
        handledDismissRequestId = state.sheetDismissRequestId

        val animator = Animatable(offsetPx)
        animator.animateTo(
            targetValue = containerHeightPx,
            animationSpec = tween(durationMillis = 190),
        ) {
            offsetPx = value
        }
        navigationController.clearSheet(sheetEntry.key)
        offsetPx = 0f
    }

    val openness = (1f - (offsetPx / (containerHeightPx * 0.5f)).coerceIn(0f, 1f))
    val scrimAlpha = openness

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AppTheme.colors.overlayBackdrop.copy(alpha = scrimAlpha * AppTheme.colors.overlayBackdrop.alpha))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    navigationController.requestDismissSheet()
                },
        )

        Box(
            modifier = Modifier
                .align(androidx.compose.ui.Alignment.BottomCenter)
                .fillMaxWidth()
                .then(
                    if (sheetEntry.screen.isFullHeight) Modifier.fillMaxSize(0.92f) 
                    else Modifier.wrapContentHeight()
                )
                .offset { IntOffset(0, offsetPx.roundToInt()) }
                .background(
                    color = AppTheme.colors.sheetBackground,
                    shape = RoundedCornerShape(
                        topStart = AppTheme.radius.sheet,
                        topEnd = AppTheme.radius.sheet
                    ),
                )
                .pointerInput(containerHeightPx) {
                    var sheetVelocityTracker = VelocityTracker()
                    detectVerticalDragGestures(
                        onDragStart = {
                            sheetVelocityTracker = VelocityTracker()
                            navigationController.beginGesture(ActiveGesture.Sheet)
                        },
                        onVerticalDrag = { change, dragAmount ->
                            if (navigationController.state.activeGesture != ActiveGesture.Sheet &&
                                navigationController.state.activeGesture != ActiveGesture.None
                            ) {
                                return@detectVerticalDragGestures
                            }
                            sheetVelocityTracker.addPosition(change.uptimeMillis, change.position)
                            change.consume()
                            offsetPx = (offsetPx + dragAmount).coerceAtLeast(0f)
                        },
                        onDragEnd = {
                            val velocity = sheetVelocityTracker.calculateVelocity().y
                            val dismissThreshold = containerHeightPx * GestureTokens.sheetDismissProgress
                            if (velocity > GestureTokens.sheetDismissVelocityY || offsetPx > dismissThreshold) {
                                navigationController.requestDismissSheet()
                            } else {
                                coroutineScope.launch {
                                    val animator = Animatable(offsetPx)
                                    animator.animateTo(
                                        targetValue = 0f,
                                        animationSpec = spring(
                                            dampingRatio = 0.9f,
                                            stiffness = GestureTokens.menuOpenCloseStiffness,
                                        ),
                                    ) {
                                        offsetPx = value
                                    }
                                }
                            }
                            navigationController.endGesture()
                        },
                        onDragCancel = {
                            navigationController.endGesture()
                        },
                    )
                },
        ) {
            Box(modifier = Modifier.navigationBarsPadding()) {
                ScreenLifecycleWrapper(sheetEntry.screen) {
                    sheetEntry.screen.Content(navigationController)
                }
            }
        }
    }
}
