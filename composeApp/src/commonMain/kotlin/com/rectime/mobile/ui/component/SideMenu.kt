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
import com.rectime.mobile.app.navigation.Screen
import com.rectime.mobile.feature.settings.SettingsScreen
import com.rectime.mobile.feature.theme.ThemeSheet
import com.rectime.mobile.ui.theme.AppTheme
import com.rectime.mobile.ui.theme.ThemeStateHolder
import com.woowla.compose.icon.collections.fontawesome.fontawesome.SolidGroup
import com.woowla.compose.icon.collections.fontawesome.fontawesome.solid.Gear
import com.woowla.compose.icon.collections.fontawesome.fontawesome.solid.Palette

private sealed class SideMenuAction {
    data class Push(val screen: Screen) : SideMenuAction()
    data class Custom(val onClick: () -> Unit) : SideMenuAction()
}

private data class SideMenuItemConfig(
    val title: String,
    val icon: ImageVector,
    val action: SideMenuAction
)

@Composable
fun SideMenu(
    revealWidthDp: Dp,
    onPushFromMenu: (Screen) -> Unit,
    onPresentThemeSheet: (Screen) -> Unit,
    themeStateHolder: ThemeStateHolder,
    modifier: Modifier = Modifier,
) {
    val mainItems = listOf(
        SideMenuItemConfig(
            title = "設定",
            icon = SolidGroup.Gear,
            action = SideMenuAction.Push(SettingsScreen)
        ),
    )

    val footerItems = listOf(
        SideMenuItemConfig(
            title = "テーマ変更",
            icon = SolidGroup.Palette,
            action = SideMenuAction.Custom {
                onPresentThemeSheet(ThemeSheet(themeStateHolder))
            }
        ),
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UserAvatar(initials = "RT")

            Column {
                Text(text = "HAL 太郎", color = AppTheme.colors.textPrimary)
                Row {
                    Text(text = "IA12B", color = AppTheme.colors.textSecondary)
                    Text(text = "/", color = AppTheme.colors.textSecondary)
                    Text(text = "99", color = AppTheme.colors.textSecondary)
                    Text(text = "/", color = AppTheme.colors.textSecondary)
                    Text(text = "12345", color = AppTheme.colors.textSecondary)
                }
            }
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
                    onClick = {
                        when (val action = item.action) {
                            is SideMenuAction.Push -> onPushFromMenu(action.screen)
                            is SideMenuAction.Custom -> action.onClick()
                        }
                    },
                )
            }
        }

        // Footer Section
        Row (
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            footerItems.forEach { item ->
                MenuItem(
                    icon = item.icon,
                    title = item.title,
                    onClick = {
                        when (val action = item.action) {
                            is SideMenuAction.Push -> onPushFromMenu(action.screen)
                            is SideMenuAction.Custom -> action.onClick()
                        }
                    },
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
