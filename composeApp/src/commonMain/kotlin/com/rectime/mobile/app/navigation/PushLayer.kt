package com.rectime.mobile.app.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.rectime.mobile.ui.theme.AppTheme
import com.rectime.mobile.ui.token.GestureTokens
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun PushLayer(
    state: NavigationState,
    navigationController: NavigationController,
    containerWidthPx: Float,
) {
    val topEntry = state.pushStack.lastOrNull() ?: return
    val topKey = topEntry.key

    var handledDismissRequestId by remember { mutableLongStateOf(state.pushDismissRequestId) }

    LaunchedEffect(state.pushDismissRequestId, topKey, containerWidthPx) {
        if (state.pushDismissRequestId == handledDismissRequestId) return@LaunchedEffect
        handledDismissRequestId = state.pushDismissRequestId

        val animator = Animatable(state.backDragOffsetPx)
        animator.animateTo(
            targetValue = containerWidthPx,
            animationSpec = tween(durationMillis = GestureTokens.pushDismissDurationMs),
        ) {
            navigationController.setBackDragOffset(value)
        }
        navigationController.completePop(topKey)
    }

    LaunchedEffect(state.pushTransition.mode, state.pushTransition.routeKey) {
        if (state.pushTransition.mode != PushTransitionMode.Enter) return@LaunchedEffect
        if (state.pushTransition.routeKey != topKey) return@LaunchedEffect

        val animator = Animatable(0f)
        animator.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 260),
        ) {
            navigationController.setPushEnterProgress(value)
        }
        navigationController.finishPushEnter(topKey)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        state.pushStack.forEachIndexed { index, entry ->
            val isTop = index == state.pushStack.lastIndex
            val stackDepth = state.pushStack.lastIndex - index
            val depthOffsetPx = (stackDepth * AppTheme.spacing.gutter.value * LocalDensity.current.density)

            val isMenuEnter = state.pushTransition.mode == PushTransitionMode.Enter &&
                state.pushTransition.routeKey == entry.key
            val enterOffsetPx = if (isMenuEnter) {
                (1f - state.pushTransition.progress.coerceIn(0f, 1f)) * containerWidthPx
            } else {
                0f
            }
            val gestureOffsetPx = if (isTop) state.backDragOffsetPx else 0f
            val totalOffsetPx = max(0f, enterOffsetPx + gestureOffsetPx - depthOffsetPx)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset { IntOffset(totalOffsetPx.roundToInt(), 0) }
                    .background(
                        color = AppTheme.colors.surfacePrimary,
                        shape = RoundedCornerShape(
                            topStart = if (isMenuEnter) AppTheme.radius.sheet else 0.dp,
                            bottomStart = if (isMenuEnter) AppTheme.radius.sheet else 0.dp,
                        ),
                    ),
            ) {
                // Render Screen Object with lifecycle
                ScreenLifecycleWrapper(entry.screen) {
                    entry.screen.Content(navigationController)
                }
            }
        }
    }
}
