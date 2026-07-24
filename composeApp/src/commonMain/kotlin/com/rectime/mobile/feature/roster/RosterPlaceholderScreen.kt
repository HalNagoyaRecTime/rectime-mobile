package com.rectime.mobile.feature.roster

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rectime.mobile.app.navigation.NavigationController
import com.rectime.mobile.app.navigation.Screen
import com.rectime.mobile.ui.component.PushScreenScaffold
import com.rectime.mobile.ui.theme.AppTheme

object RosterPlaceholderScreen : Screen {
    override val key: String = "roster_placeholder"

    @Composable
    override fun Content(navigationController: NavigationController) {
        PushScreenScaffold(
            title = "出場メンバー割り当て",
            onBack = { navigationController.requestPop() },
        ) {
            item {
                Text(
                    text = "出場メンバー割り当て機能は準備中です。",
                    color = AppTheme.colors.textSecondary,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }
        }
    }
}
