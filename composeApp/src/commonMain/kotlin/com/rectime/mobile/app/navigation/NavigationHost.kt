package com.rectime.mobile.app.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import com.rectime.mobile.feature.auth.AuthSession
import com.rectime.mobile.feature.auth.LocalUserProfile
import com.rectime.mobile.feature.auth.toUserProfile
import com.rectime.mobile.ui.theme.AppTheme
import com.rectime.mobile.ui.token.GestureTokens
import kotlinx.coroutines.launch

@Composable
fun NavigationHost(
    navigationController: NavigationController,
    session: AuthSession,
    onLogout: () -> Unit,
) {
    val state = navigationController.state
    val coroutineScope = rememberCoroutineScope()
    val userProfile = session.user.toUserProfile()

    // BoxWithConstraints 内で計算したサイズをジェスチャーハンドラーと共有する
    var containerWidthPx by remember { mutableFloatStateOf(0f) }
    var containerHeightPx by remember { mutableFloatStateOf(0f) }

    CompositionLocalProvider(LocalUserProfile provides userProfile) {
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
                        if (navigationController.state.activeGesture != ActiveGesture.Back) {
                            return@detectHorizontalDragGestures
                        }
                        change.consume()
                        velocityTracker.addPosition(change.uptimeMillis, change.position)
                        val next = navigationController.state.backDragOffsetPx + dragAmount
                        navigationController.setBackDragOffset(next)
                    },
                    onDragEnd = {
                        if (navigationController.state.activeGesture == ActiveGesture.Back) {
                            val velocity = velocityTracker.calculateVelocity().x
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
                                            stiffness = GestureTokens.layerSettleStiffness,
                                        ),
                                    ) {
                                        navigationController.setBackDragOffset(value)
                                    }
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
        val density = LocalDensity.current

        // コンポジションごとにサイズを更新してジェスチャーハンドラーと共有する
        SideEffect {
            containerWidthPx = maxWidth.value * density.density
            containerHeightPx = maxHeight.value * density.density
        }

        // Layer 1: Root (Home / Calendar)
        RootLayer(
            state = state,
            navigationController = navigationController,
            session = session,
            onLogout = onLogout,
        )

        // Layer 2: Push Layer (above Root+BottomNav, all sources)
        PushLayer(
            state = state,
            navigationController = navigationController,
            containerWidthPx = containerWidthPx,
        )

        // Layer 3: Sheet (Modals)
        SheetLayer(
            state = state,
            navigationController = navigationController,
            containerHeightPx = containerHeightPx,
        )
        }
    }
}
