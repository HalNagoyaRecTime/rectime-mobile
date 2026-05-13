package com.rectime.mobile.app.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import com.rectime.mobile.ui.component.SideMenu
import com.rectime.mobile.ui.theme.AppTheme
import com.rectime.mobile.ui.theme.ThemeStateHolder
import com.rectime.mobile.ui.token.GestureTokens
import kotlinx.coroutines.launch

@Composable
fun NavigationHost(
    navigationController: NavigationController,
    themeStateHolder: ThemeStateHolder,
) {
    val state = navigationController.state
    val coroutineScope = rememberCoroutineScope()

    // BoxWithConstraints 内で計算したサイズをジェスチャーハンドラーと共有する
    var revealWidthPx by remember { mutableFloatStateOf(0f) }
    var containerWidthPx by remember { mutableFloatStateOf(0f) }
    var containerHeightPx by remember { mutableFloatStateOf(0f) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(AppTheme.colors.surfacePrimary)
            // 親（BoxWithConstraints）にジェスチャーを付けることで、
            // 子（各Layer）のボタンタップをブロックしない。
            // Composeのイベント伝播はMainパスで子が先・親が後のため。
            .pointerInput(
                state.pushStack.size,
                state.sheet,
                state.pushTransition.mode,
            ) {
                var velocityTracker = VelocityTracker()
                detectHorizontalDragGestures(
                    onDragStart = {
                        val gesture = navigationController.resolveHorizontalGesture()
                        if (gesture == ActiveGesture.None) return@detectHorizontalDragGestures
                        velocityTracker = VelocityTracker()
                        navigationController.beginGesture(gesture)
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        val gesture = navigationController.state.activeGesture
                        if (gesture != ActiveGesture.Menu && gesture != ActiveGesture.Back) {
                            return@detectHorizontalDragGestures
                        }
                        change.consume()
                        velocityTracker.addPosition(change.uptimeMillis, change.position)
                        when (gesture) {
                            ActiveGesture.Menu -> {
                                val rw = revealWidthPx
                                if (rw > 0f) {
                                    val next = navigationController.state.menuProgress + (dragAmount / rw)
                                    navigationController.setMenuProgress(next)
                                }
                            }
                            ActiveGesture.Back -> {
                                val next = navigationController.state.backDragOffsetPx + dragAmount
                                navigationController.setBackDragOffset(next)
                            }
                        }
                    },
                    onDragEnd = {
                        val gesture = navigationController.state.activeGesture
                        val velocity = velocityTracker.calculateVelocity().x
                        when (gesture) {
                            ActiveGesture.Menu -> {
                                val progress = navigationController.state.menuProgress
                                val velProgress = if (revealWidthPx > 0f) velocity / revealWidthPx else 0f
                                when {
                                    velocity > GestureTokens.menuFlingVelocityX -> navigationController.openMenu(velProgress)
                                    velocity < -GestureTokens.menuFlingVelocityX -> navigationController.closeMenu(velProgress)
                                    progress > GestureTokens.menuSettleProgress -> navigationController.openMenu()
                                    else -> navigationController.closeMenu()
                                }
                            }
                            ActiveGesture.Back -> {
                                val cw = containerWidthPx
                                val progress = if (cw > 0f) {
                                    (navigationController.state.backDragOffsetPx / cw).coerceIn(0f, 1f)
                                } else 0f
                                if (velocity > GestureTokens.backDismissVelocityX || progress > GestureTokens.backDismissProgress) {
                                    navigationController.requestPop()
                                } else {
                                    coroutineScope.launch {
                                        val animator = Animatable(navigationController.state.backDragOffsetPx)
                                        animator.animateTo(
                                            targetValue = 0f,
                                            animationSpec = spring(
                                                dampingRatio = 0.9f,
                                                stiffness = GestureTokens.menuOpenCloseStiffness,
                                            ),
                                        ) {
                                            navigationController.setBackDragOffset(value)
                                        }
                                    }
                                }
                            }
                            else -> Unit
                        }
                        navigationController.endGesture()
                    },
                    onDragCancel = {
                        navigationController.endGesture()
                    },
                )
            },
    ) {
        val layout = AppTheme.layout
        val density = LocalDensity.current
        val revealWidthDp = maxOf(
            layout.sideMenuRevealMin,
            minOf(maxWidth * GestureTokens.sideMenuRevealRatio, layout.sideMenuRevealMax),
        )

        // コンポジションごとにサイズを更新してジェスチャーハンドラーと共有する
        SideEffect {
            revealWidthPx = revealWidthDp.value * density.density
            containerWidthPx = maxWidth.value * density.density
            containerHeightPx = maxHeight.value * density.density
        }

        // Background Side Menu
        SideMenu(
            revealWidthDp = revealWidthDp,
            onPushFromMenu = { screen ->
                navigationController.push(screen, PushTransitionSource.SideMenu)
            },
            onPresentThemeSheet = { sheet ->
                navigationController.presentSheet(sheet)
            },
            themeStateHolder = themeStateHolder,
        )

        // Layer 1: Root (Home / Calendar)
        RootLayer(
            state = state,
            navigationController = navigationController,
            revealWidthPx = revealWidthPx,
        )

        // Layer 2: Push Layer (above Root+BottomNav, all sources)
        PushLayer(
            state = state,
            navigationController = navigationController,
            containerWidthPx = containerWidthPx,
            revealWidthPx = revealWidthPx,
        )

        // Layer 3: Sheet (Modals)
        SheetLayer(
            state = state,
            navigationController = navigationController,
            containerHeightPx = containerHeightPx,
        )
    }
}
