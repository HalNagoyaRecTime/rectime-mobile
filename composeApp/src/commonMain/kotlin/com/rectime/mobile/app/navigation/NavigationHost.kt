package com.rectime.mobile.app.navigation

import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalDensity
import com.rectime.mobile.feature.auth.AuthSession
import com.rectime.mobile.feature.auth.LocalUserProfile
import com.rectime.mobile.feature.auth.toUserProfile
import com.rectime.mobile.feature.notifications.NotificationPermissionStartup
import com.rectime.mobile.ui.theme.AppTheme

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
    NavigationBackHandler(enabled = state.pushStack.isNotEmpty()) {
        navigationController.requestPop()
    }

    // BoxWithConstraints 内で計算したサイズをジェスチャーハンドラーと共有する
    var containerWidthPx by remember { mutableFloatStateOf(0f) }

    CompositionLocalProvider(LocalUserProfile provides userProfile) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(AppTheme.colors.surfacePrimary)
                .navigationBackGesture(navigationController, state.pushStack.lastOrNull()?.key) {
                    containerWidthPx
                },
        ) {
            val density = LocalDensity.current

            // コンポジションごとにサイズを更新してジェスチャーハンドラーと共有する
            SideEffect {
                containerWidthPx = maxWidth.value * density.density
            }

            // タブ画面とボトムバー。
            RootLayer(
                state = state,
                navigationController = navigationController,
                session = session,
                onLogout = onLogout,
                hasUnreadNotifications = hasUnreadNotifications,
                notificationPermissionStartup = notificationPermissionStartup,
            )

            // 詳細画面はボトムバーを含む土台の上に表示する。
            PushLayer(
                state = state,
                navigationController = navigationController,
                containerWidthPx = containerWidthPx,
            )
        }
    }
}
