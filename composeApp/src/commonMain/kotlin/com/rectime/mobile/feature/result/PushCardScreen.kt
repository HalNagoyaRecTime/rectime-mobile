package com.rectime.mobile.feature.result

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rectime.mobile.app.navigation.NavigationController
import com.rectime.mobile.app.navigation.Screen
import com.rectime.mobile.ui.component.PressSurface
import com.rectime.mobile.ui.theme.AppTheme
import com.woowla.compose.icon.collections.fontawesome.fontawesome.SolidGroup
import com.woowla.compose.icon.collections.fontawesome.fontawesome.solid.ArrowLeft

/**
 * Specialized Screen Objects for Push Stack
 */

data class DetailScreen(val id: String) : Screen {
    override val key: String = "detail_$id"
    @Composable
    override fun Content(navigationController: NavigationController) {
        PushCardScreenUI(
            title = "Match Detail",
            description = "試合詳細のワイヤー画面 (ID: $id)",
            navigationController = navigationController
        )
    }
}

object NotificationsScreen : Screen {
    override val key: String = "notifications"
    @Composable
    override fun Content(navigationController: NavigationController) {
        PushCardScreenUI(
            title = "Notifications",
            description = "通知一覧のワイヤー画面",
            navigationController = navigationController
        )
    }
}

object SettingsScreen : Screen {
    override val key: String = "settings"
    @Composable
    override fun Content(navigationController: NavigationController) {
        PushCardScreenUI(
            title = "Settings",
            description = "設定画面のワイヤー画面",
            navigationController = navigationController
        )
    }
}

object HelpCenterScreen : Screen {
    override val key: String = "help_center"
    @Composable
    override fun Content(navigationController: NavigationController) {
        PushCardScreenUI(
            title = "Help Center",
            description = "ヘルプセンターのワイヤー画面",
            navigationController = navigationController
        )
    }
}

object MatchInfoScreen : Screen {
    override val key: String = "match_info"
    @Composable
    override fun Content(navigationController: NavigationController) {
        PushCardScreenUI(
            title = "Match Info",
            description = "大会情報のワイヤー画面",
            navigationController = navigationController
        )
    }
}

object OperatorMenuScreen : Screen {
    override val key: String = "operator_menu"
    @Composable
    override fun Content(navigationController: NavigationController) {
        PushCardScreenUI(
            title = "Operator Menu",
            description = "運営メニューのワイヤー画面",
            navigationController = navigationController
        )
    }
}

object DevScreen : Screen {
    override val key: String = "dev"
    @Composable
    override fun Content(navigationController: NavigationController) {
        PushCardScreenUI(
            title = "Developer Tools",
            description = "開発者向けワイヤー画面",
            navigationController = navigationController
        )
    }
}

/**
 * Shared UI for placeholder push cards
 */
@Composable
private fun PushCardScreenUI(
    title: String,
    description: String,
    navigationController: NavigationController,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PressSurface(
                onClick = { navigationController.requestPop() },
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = SolidGroup.ArrowLeft,
                        contentDescription = null,
                        tint = AppTheme.colors.textPrimary,
                        modifier = Modifier.size(14.dp),
                    )
                    Text("戻る", color = AppTheme.colors.textPrimary)
                }
            }
            PressSurface(
                onClick = { navigationController.presentSheet(SampleSheet) },
                modifier = Modifier.weight(1f)
            ) {
                Text("SampleSheet", color = AppTheme.colors.textPrimary)
            }
        }

        Text(text = title, color = AppTheme.colors.textPrimary, fontWeight = FontWeight.Bold)
        Text(text = description, color = AppTheme.colors.textSecondary)
    }
}
