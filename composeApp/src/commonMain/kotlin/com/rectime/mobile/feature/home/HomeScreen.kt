package com.rectime.mobile.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rectime.mobile.app.navigation.NavigationController
import com.rectime.mobile.app.navigation.Screen
import com.rectime.mobile.core.model.MockUser
import com.rectime.mobile.feature.notifications.NotificationsScreen
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import com.rectime.mobile.ui.component.RootScreenHeader
import com.rectime.mobile.ui.theme.AppTheme
import com.woowla.compose.icon.collections.fontawesome.fontawesome.SolidGroup
import com.woowla.compose.icon.collections.fontawesome.fontawesome.solid.Bell

object HomeScreen : Screen {
    override val key: String = "home"

    @Composable
    override fun Content(navigationController: NavigationController) {
        HomeScreenUI(
            onOpenMenu = { navigationController.openMenu() },
            onOpenNotifications = { navigationController.push(NotificationsScreen) },
        )
    }
}

@Composable
private fun HomeScreenUI(
    onOpenMenu: () -> Unit,
    onOpenNotifications: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val topPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 12.dp + AppTheme.layout.headerAction + 12.dp

    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(
                top = topPadding,
                bottom = 12.dp,
                start = AppTheme.layout.screenHorizontalPadding,
                end = AppTheme.layout.screenHorizontalPadding,
            ),
        ) {
            // コンテンツアイテムをここに追加
        }

        RootScreenHeader(
            title = "ホーム",
            profile = MockUser.me,
            onOpenMenu = onOpenMenu,
            modifier = Modifier
                .statusBarsPadding()
                .padding(horizontal = AppTheme.layout.screenHorizontalPadding)
                .padding(top = 12.dp),
            onTrailingClick = onOpenNotifications,
            trailing = {
                Icon(
                    imageVector = SolidGroup.Bell,
                    contentDescription = "通知",
                    tint = AppTheme.colors.textPrimary,
                    modifier = Modifier.size(20.dp),
                )
            },
        )
    }
}
