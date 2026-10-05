package com.rectime.mobile.app.navigation

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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import com.rectime.mobile.feature.auth.AuthSession
import com.rectime.mobile.feature.auth.LocalUserProfile
import com.rectime.mobile.feature.auth.toUserProfile
import com.rectime.mobile.feature.notifications.NotificationPermissionStartup
import com.rectime.mobile.ui.theme.AppTheme
import com.rectime.mobile.ui.token.GestureTokens

@Composable
fun NavigationHost(
    navigationController: NavigationController,
    session: AuthSession,
    onLogout: () -> Unit,
    hasUnreadNotifications: Boolean,
    notificationPermissionStartup: NotificationPermissionStartup?,
) {
    val state = navigationController.state
    val userProfile = session.user.toUserProfile()

    // BoxWithConstraints 内で計算したサイズをジェスチャーハンドラーと共有する
    var containerWidthPx by remember { mutableFloatStateOf(0f) }
    var containerHeightPx by remember { mutableFloatStateOf(0f) }

    CompositionLocalProvider(LocalUserProfile provides userProfile) {
        BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(AppTheme.colors.surfacePrimary)
            .pointerInput(navigationController) {
                awaitPointerEventScope {
                    var primaryPointer: PointerId? = null
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (primaryPointer == null) primaryPointer = event.changes.firstOrNull { it.pressed }?.id
                        // 戻る操作中の二本目の指で、表示中のボタンを操作させない。
                        if (navigationController.state.activeGesture == ActiveGesture.Back) {
                            event.changes.filter { it.id != primaryPointer }.forEach { it.consume() }
                        }
                        if (event.changes.none { it.pressed }) primaryPointer = null
                    }
                }
            }
            // ジェスチャー中に遷移状態が変わってもハンドラーを破棄しない。
            .pointerInput(
                state.pushStack.lastOrNull()?.key,
                state.sheet?.key,
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
                        navigationController.setBackDragOffset(next.coerceIn(0f, containerWidthPx.coerceAtLeast(0f)))
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
                                navigationController.returnFromBackGesture()
                            }
                        }
                        navigationController.endGesture()
                    },
                    onDragCancel = {
                        navigationController.returnFromBackGesture()
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
            hasUnreadNotifications = hasUnreadNotifications,
            notificationPermissionStartup = notificationPermissionStartup,
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
