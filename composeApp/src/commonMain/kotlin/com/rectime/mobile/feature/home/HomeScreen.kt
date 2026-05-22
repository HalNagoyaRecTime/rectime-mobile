package com.rectime.mobile.feature.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
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
        val vm: HomeViewModel = viewModel(
            factory = viewModelFactory {
                initializer { HomeViewModel() }
            }
        )
        val uiState by vm.uiState.collectAsStateWithLifecycle()

        RootScreenScaffold(
            title = "ホーム",
            profile = MockUser.me,
            onOpenMenu = { navigationController.openMenu() },
            onTrailingClick = { navigationController.push(NotificationsScreen) },
            trailing = {
                Icon(
                    imageVector = SolidGroup.Bell,
                    contentDescription = "通知",
                    tint = AppTheme.colors.textPrimary,
                    modifier = Modifier.size(20.dp),
                )
            },
        ) {
            when {
                uiState.isLoading -> item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
                uiState.response != null -> item {
                    Text(
                        text = uiState.response!!,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
    }
}
