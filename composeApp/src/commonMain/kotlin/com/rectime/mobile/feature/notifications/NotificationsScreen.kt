package com.rectime.mobile.feature.notifications

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rectime.mobile.app.navigation.NavigationController
import com.rectime.mobile.app.navigation.Screen
import com.rectime.mobile.ui.component.PushScreenHeader
import com.rectime.mobile.ui.theme.AppTheme
import com.rectime.mobile.ui.theme.AppTheme.layout

object NotificationsScreen : Screen {
    override val key: String = "notifications"

    @Composable
    override fun Content(navigationController: NavigationController) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            PushScreenHeader(
                title = "通知",
                onBack = { navigationController.requestPop() },
                modifier = Modifier.padding(horizontal = AppTheme.layout.screenHorizontalPadding),
            )

            Text(
                text = "通知一覧の画面です。現在はプレースホルダを表示しています。",
                color = AppTheme.colors.textSecondary,
                modifier = Modifier.padding(
                    horizontal = AppTheme.layout.screenHorizontalPadding,
                    vertical = 12.dp,
                ),
            )
        }
    }
}
