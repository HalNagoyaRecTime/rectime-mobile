package com.rectime.mobile.app.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import com.rectime.mobile.feature.calendar.CalendarScreen
import com.rectime.mobile.feature.home.HomeScreen
import com.rectime.mobile.ui.component.BottomNavigationBar
import com.rectime.mobile.ui.theme.AppTheme
import com.rectime.mobile.ui.token.GestureTokens
import kotlin.math.roundToInt

@Composable
fun RootLayer(
    state: NavigationState,
    navigationController: NavigationController,
    revealWidthPx: Float,
) {
    val rootScreen = state.rootScreen ?: return
    val canDragMenu = state.sheet == null &&
        state.pushStack.isEmpty() &&
        state.pushTransition.mode == PushTransitionMode.Idle

    val menuAnimatable = remember { Animatable(state.menuProgress) }
    LaunchedEffect(state.menuProgress, state.activeGesture) {
        if (state.activeGesture == ActiveGesture.Menu) {
            menuAnimatable.snapTo(state.menuProgress)
        } else {
            menuAnimatable.animateTo(
                targetValue = state.menuProgress,
                animationSpec = spring(
                    dampingRatio = 0.86f,
                    stiffness = GestureTokens.menuOpenCloseStiffness,
                ),
            )
        }
    }

    val renderedMenuProgress = when {
        state.pushTransition.mode == PushTransitionMode.Enter -> {
            val source = state.pushTransition.sourceProgress.coerceIn(0f, 1f)
            source + (0f - source) * state.pushTransition.progress.coerceIn(0f, 1f)
        }
        state.activeGesture == ActiveGesture.Menu -> state.menuProgress
        else -> menuAnimatable.value
    }
    val safeProgress = renderedMenuProgress.coerceIn(0f, 1f)
    val offsetX = (safeProgress * revealWidthPx).roundToInt()
    val cornerDp = AppTheme.radius.xxl * safeProgress

    Box(
        modifier = Modifier
            .fillMaxSize()
            .offset { IntOffset(offsetX, 0) }
            .graphicsLayer {
                shadowElevation = 24f * renderedMenuProgress
            }
            .clip(RoundedCornerShape(topStart = cornerDp, bottomStart = cornerDp))
            .background(AppTheme.colors.surfacePrimary)
            .pointerInput(canDragMenu, revealWidthPx) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        if (!canDragMenu) return@detectHorizontalDragGestures
                        navigationController.beginGesture(ActiveGesture.Menu)
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        if (!canDragMenu) return@detectHorizontalDragGestures
                        if (navigationController.state.activeGesture != ActiveGesture.Menu &&
                            navigationController.state.activeGesture != ActiveGesture.None
                        ) {
                            return@detectHorizontalDragGestures
                        }
                        change.consume()
                        val next = navigationController.state.menuProgress + (dragAmount / revealWidthPx)
                        navigationController.setMenuProgress(next)
                    },
                    onDragEnd = {
                        if (!canDragMenu || navigationController.state.activeGesture != ActiveGesture.Menu) {
                            return@detectHorizontalDragGestures
                        }
                        if (navigationController.state.menuProgress > GestureTokens.backDismissProgress) {
                            navigationController.openMenu()
                        } else {
                            navigationController.closeMenu()
                        }
                        navigationController.endGesture()
                    },
                    onDragCancel = {
                        if (!canDragMenu) return@detectHorizontalDragGestures
                        navigationController.endGesture()
                    },
                )
            },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            ScreenLifecycleWrapper(rootScreen) {
                rootScreen.Content(navigationController)
            }

            BottomNavigationBar(
                currentScreen = rootScreen,
                onSelectRoot = { screen -> 
                    navigationController.setRoot(screen)
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
            )
        }

        if (renderedMenuProgress > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(AppTheme.colors.navigationScrim.copy(alpha = 0.18f * renderedMenuProgress))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        navigationController.closeMenu()
                    },
            )
        }
    }
}
