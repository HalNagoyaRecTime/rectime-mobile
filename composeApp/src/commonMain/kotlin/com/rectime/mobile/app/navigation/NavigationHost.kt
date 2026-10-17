package com.rectime.mobile.app.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
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
    val density = LocalDensity.current.density
    NavigationBackHandler(enabled = navigationController.canHandleSystemBack) {
        navigationController.handleSystemBack()
    }

    // 実際のサイズが変わった時だけ、ジェスチャーとアニメーションの幅を更新する。
    var containerWidthPx by remember { mutableFloatStateOf(0f) }

    CompositionLocalProvider(LocalUserProfile provides userProfile) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AppTheme.colors.surfacePrimary)
                .onSizeChanged { containerWidthPx = it.width.toFloat() }
                .navigationBackGesture(navigationController, state.pushStack.lastOrNull()?.key, density) {
                    containerWidthPx
                },
        ) {
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
                widthPx = { containerWidthPx },
            )
        }
    }
}
