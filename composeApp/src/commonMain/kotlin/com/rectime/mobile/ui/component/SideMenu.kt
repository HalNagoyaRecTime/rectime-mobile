package com.rectime.mobile.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rectime.mobile.app.navigation.PushRoute
import com.rectime.mobile.ui.theme.AppTheme
import com.woowla.compose.icon.collections.fontawesome.fontawesome.SolidGroup
import com.woowla.compose.icon.collections.fontawesome.fontawesome.solid.CircleQuestion
import com.woowla.compose.icon.collections.fontawesome.fontawesome.solid.Code
import com.woowla.compose.icon.collections.fontawesome.fontawesome.solid.Gear
import com.woowla.compose.icon.collections.fontawesome.fontawesome.solid.Palette
import com.woowla.compose.icon.collections.fontawesome.fontawesome.solid.Trophy
import com.woowla.compose.icon.collections.fontawesome.fontawesome.solid.Wrench

private data class SideMenuItemConfig(
    val route: PushRoute?,
    val title: String,
    val icon: ImageVector,
    val onClickOverride: (() -> Unit)? = null,
)

@Composable
fun SideMenu(
    revealWidthDp: Dp,
    onPushFromMenu: (PushRoute) -> Unit,
    onPresentThemeSheet: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mainItems = listOf(
        SideMenuItemConfig(PushRoute.OperatorMenu, "運営メニュー", SolidGroup.Wrench),
        SideMenuItemConfig(PushRoute.MatchInfo, "対戦情報", SolidGroup.Trophy),
        SideMenuItemConfig(PushRoute.Settings, "設定", SolidGroup.Gear),
        SideMenuItemConfig(PushRoute.HelpCenter, "ヘルプセンター", SolidGroup.CircleQuestion),
        SideMenuItemConfig(PushRoute.Dev, "開発メニュー", SolidGroup.Code),
    )

    val footerItems = listOf(
        SideMenuItemConfig(null, "テーマ変更", SolidGroup.Palette, onClickOverride = onPresentThemeSheet),
    )

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(revealWidthDp)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 20.dp),
    ) {
        // Profile Section
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            UserAvatar(initials = "RT")
            Text(text = "Rectime Operator", color = AppTheme.colors.textPrimary)
            Text(text = "operator@rectime.app", color = AppTheme.colors.textSecondary)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Navigation Items
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            mainItems.forEach { item ->
                MenuItem(
                    icon = item.icon,
                    title = item.title,
                    onClick = { item.route?.let { onPushFromMenu(it) } },
                )
            }
        }

        // Footer Section
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            footerItems.forEach { item ->
                MenuItem(
                    icon = item.icon,
                    title = item.title,
                    onClick = item.onClickOverride ?: {},
                )
            }
            Text(
                text = "rectime",
                color = AppTheme.colors.textMuted,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp),
            )
        }
    }
}

@Composable
private fun MenuItem(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
) {
    PressSurface(
        onClick = onClick,
        color = AppTheme.colors.navigationSurface,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = AppTheme.colors.textSecondary,
                modifier = Modifier.size(16.dp),
            )
            Text(text = title, color = AppTheme.colors.textPrimary)
        }
    }
}
