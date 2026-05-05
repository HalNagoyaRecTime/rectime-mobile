package com.rectime.mobile.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rectime.mobile.app.navigation.NavigationController
import com.rectime.mobile.app.navigation.Screen
import com.rectime.mobile.feature.notifications.NotificationsScreen
import com.rectime.mobile.ui.component.HeaderActionButton
import com.rectime.mobile.ui.component.ScreenHeader
import com.woowla.compose.icon.collections.fontawesome.fontawesome.SolidGroup
import com.woowla.compose.icon.collections.fontawesome.fontawesome.solid.Bars
import com.woowla.compose.icon.collections.fontawesome.fontawesome.solid.Bell

/**
 * HomeScreen as a self-contained Navigation Box
 */
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
    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ScreenHeader(
                title = "Home",
                modifier = Modifier.padding(top = 12.dp),
                leading = {
                    HeaderActionButton(
                        icon = SolidGroup.Bars,
                        contentDescription = "メニュー",
                        onClick = onOpenMenu,
                    )
                },
                trailing = {
                    HeaderActionButton(
                        icon = SolidGroup.Bell,
                        contentDescription = "通知",
                        onClick = onOpenNotifications,
                    )
                },
            )
        }
    }
}
