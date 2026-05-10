package com.rectime.mobile.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rectime.mobile.app.navigation.NavigationController
import com.rectime.mobile.app.navigation.Screen
import com.rectime.mobile.ui.component.PushScreenHeader
import com.rectime.mobile.ui.theme.AppTheme

object SettingsScreen : Screen {
    override val key: String = "settings"

    @Composable
    override fun Content(navigationController: NavigationController) {
        val screenHorizontalPadding = AppTheme.layout.screenHorizontalPadding
        val topPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + AppTheme.layout.headerAction

        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = topPadding, start = screenHorizontalPadding, end = screenHorizontalPadding),
            ) {
                Text(
                    text = "設定画面です。将来的にユーザー設定などを追加します。",
                    color = AppTheme.colors.textSecondary,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }

            PushScreenHeader(
                title = "設定",
                onBack = { navigationController.requestPop() },
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(horizontal = screenHorizontalPadding),
            )
        }
    }
}
