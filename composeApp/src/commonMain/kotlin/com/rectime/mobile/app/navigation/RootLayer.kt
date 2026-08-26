package com.rectime.mobile.app.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.rectime.mobile.feature.auth.AuthSession
import com.rectime.mobile.ui.component.BottomNavigationBar
import com.rectime.mobile.ui.theme.AppTheme

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

        // コンテンツを画面下端に向かって滑らかにフェードさせたい
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(120.dp)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            AppTheme.colors.edgeFadeColor.copy(alpha = 0f),
                            AppTheme.colors.edgeFadeColor,
                        ),
                    ),
                ),
        )

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
