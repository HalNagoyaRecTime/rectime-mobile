package com.rectime.mobile.feature.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rectime.mobile.app.navigation.NavigationController
import com.rectime.mobile.app.navigation.Screen
import com.rectime.mobile.core.model.MockUser
import com.rectime.mobile.feature.notifications.NotificationsScreen
import com.rectime.mobile.ui.component.RootScreenScaffold
import com.rectime.mobile.ui.theme.AppTheme
import com.woowla.compose.icon.collections.fontawesome.fontawesome.SolidGroup
import com.woowla.compose.icon.collections.fontawesome.fontawesome.solid.Bell

object HomeScreen : Screen {
    override val key: String = "home"

    @Composable
    override fun Content(navigationController: NavigationController) {
        val viewModel = viewModel { HomeViewModel() }
        val uiState by viewModel.uiState.collectAsState()

        RootScreenScaffold(
            title = "ホーム",
            profile = MockUser.me,
            onOpenMenu = { navigationController.openMenu() },
        ) {
            item {
                when {
                    uiState.isLoading -> Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                    uiState.error != null -> Text(
                        text = "API接続エラー: ${uiState.error}",
                        color = AppTheme.colors.textSecondary,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                    uiState.isHealthy == true -> Text(
                        text = "API接続OK",
                        color = AppTheme.colors.textSecondary,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                    uiState.isHealthy == false -> Text(
                        text = "APIが応答しませんでした",
                        color = AppTheme.colors.textSecondary,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }
            }
        }
    }
}
