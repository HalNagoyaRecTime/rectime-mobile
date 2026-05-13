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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.rectime.mobile.ui.theme.AppTheme
import com.rectime.mobile.ui.token.GestureTokens
import com.rectime.mobile.ui.token.rememberDeviceCornerRadius
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun PushLayer(
    state: NavigationState,
    navigationController: NavigationController,
    containerWidthPx: Float,
    revealWidthPx: Float,
    filter: (PushEntry) -> Boolean = { true },
) {
    val entries = state.pushStack.filter(filter)
    if (entries.isEmpty()) return

    val topEntry = state.pushStack.lastOrNull()
    val topKey = topEntry?.key
    val deviceCornerRadius = rememberDeviceCornerRadius()

    var handledDismissRequestId by remember { mutableLongStateOf(state.pushDismissRequestId) }

    LaunchedEffect(state.pushDismissRequestId, topKey, containerWidthPx) {
        if (state.pushDismissRequestId == handledDismissRequestId) return@LaunchedEffect
        handledDismissRequestId = state.pushDismissRequestId
        val topEntryNonNull = topEntry ?: return@LaunchedEffect

        // Only handle dismiss if the top screen matches the filter of this layer
        if (!filter(topEntryNonNull)) return@LaunchedEffect

        try {
            navigationController.setTransitioning(true)
            val animator = Animatable(state.backDragOffsetPx)
            animator.animateTo(
                targetValue = containerWidthPx,
                animationSpec = tween(durationMillis = GestureTokens.pushDismissDurationMs),
            ) {
                navigationController.setBackDragOffset(value)
            }
            navigationController.completePop(topEntryNonNull.key)
        } finally {
            navigationController.setTransitioning(false)
        }
    }

    LaunchedEffect(state.pushTransition.mode, state.pushTransition.routeKey) {
        if (state.pushTransition.mode != PushTransitionMode.Enter) return@LaunchedEffect
        val transitionKey = state.pushTransition.routeKey ?: return@LaunchedEffect

        // Check if the entering screen belongs to this layer
        val entry = state.pushStack.find { it.key == transitionKey } ?: return@LaunchedEffect
        if (!filter(entry)) return@LaunchedEffect

        try {
            navigationController.setTransitioning(true)
            val animator = Animatable(0f)
            animator.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 260),
            ) {
                navigationController.setPushEnterProgress(value)
            }
            navigationController.finishPushEnter(transitionKey)
        } finally {
            navigationController.setTransitioning(false)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        entries.forEach { entry ->
            val indexInFullStack = state.pushStack.indexOf(entry)
            val isTop = indexInFullStack == state.pushStack.lastIndex
            val stackDepth = state.pushStack.lastIndex - indexInFullStack
            val depthOffsetPx = (stackDepth * AppTheme.spacing.gutter.value * LocalDensity.current.density)

            val isMenuEnter = state.pushTransition.mode == PushTransitionMode.Enter &&
                state.pushTransition.routeKey == entry.key

            val initialOffsetPx = if (entry.source == PushTransitionSource.SideMenu) revealWidthPx else containerWidthPx

            val enterOffsetPx = if (isMenuEnter) {
                (1f - state.pushTransition.progress.coerceIn(0f, 1f)) * initialOffsetPx
            } else {
                0f
            }
            val gestureOffsetPx = if (isTop) state.backDragOffsetPx else 0f
            val totalOffsetPx = max(0f, enterOffsetPx + gestureOffsetPx - depthOffsetPx)

            val cornerDp = if (entry.source == PushTransitionSource.SideMenu) deviceCornerRadius else 0.dp
            val cornerShape = RoundedCornerShape(topStart = cornerDp, bottomStart = cornerDp)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset { IntOffset(totalOffsetPx.roundToInt(), 0) }
                    .graphicsLayer {
                        shape = cornerShape
                        clip = cornerDp > 0.dp
                    }
                    .background(color = AppTheme.colors.surfacePrimary),
            ) {
                ScreenLifecycleWrapper(entry.screen) {
                    entry.screen.Content(navigationController)
                }
            }
        }
    }
}
