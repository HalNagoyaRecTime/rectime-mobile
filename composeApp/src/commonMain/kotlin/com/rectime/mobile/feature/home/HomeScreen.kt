package com.rectime.mobile.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rectime.mobile.app.navigation.NavigationController
import com.rectime.mobile.app.navigation.Screen
import com.rectime.mobile.core.model.MockUser
import com.rectime.mobile.feature.notifications.NotificationsScreen
import com.rectime.mobile.ui.component.HeaderActionButton
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
    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = AppTheme.layout.screenHorizontalPadding),
    ) {
        RootScreenHeader(
            title = "Home",
            profile = MockUser.me,
            onOpenMenu = onOpenMenu,
            modifier = Modifier.padding(top = 12.dp),
            trailing = {
                HeaderActionButton(
                    icon = SolidGroup.Bell,
                    contentDescription = "通知",
                    onClick = onOpenNotifications,
                )
            },
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 12.dp),
        ) {
            // コンテンツアイテムをここに追加
        }
    }
}
