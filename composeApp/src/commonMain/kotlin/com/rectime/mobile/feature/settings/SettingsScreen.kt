package com.rectime.mobile.feature.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rectime.mobile.app.navigation.NavigationController
import com.rectime.mobile.app.navigation.Screen
import com.rectime.mobile.ui.component.PushScreenScaffold
import com.rectime.mobile.ui.theme.AppTheme

object SettingsScreen : Screen {
    override val key: String = "settings"

    @Composable
    override fun Content(navigationController: NavigationController) {
        PushScreenScaffold(
            title = "設定",
            onBack = { navigationController.requestPop() },
        ) {
            Text(
                text = "設定画面です。将来的にユーザー設定などを追加します。",
                color = AppTheme.colors.textSecondary,
                modifier = Modifier.padding(vertical = 12.dp),
            )
        }
    }
}
