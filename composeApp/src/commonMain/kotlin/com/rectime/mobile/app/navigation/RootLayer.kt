package com.rectime.mobile.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.rectime.mobile.feature.auth.AuthSession
import com.rectime.mobile.ui.component.BottomNavigationBar

/**
 * RootLayer（土台レイヤー）
 *
 * アプリの最も背面に位置する「箱」です。
 * 中身（HomeやCalendar）が何であるかは気にせず、指示された画面をただ描画します。
 */
@Composable
fun RootLayer(
    state: NavigationState,
    navigationController: NavigationController,
    session: AuthSession,
    onLogout: () -> Unit,
) {
    val rootScreen = state.rootScreen ?: return

    Box(modifier = Modifier.fillMaxSize()) {
        ScreenLifecycleWrapper(rootScreen) {
            rootScreen.Content(navigationController)
        }

        BottomNavigationBar(
            currentScreen = rootScreen,
            onSelectRoot = { screen: Screen ->
                navigationController.setRoot(screen)
            },
            session = session,
            onLogout = onLogout,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
        )
    }
}
