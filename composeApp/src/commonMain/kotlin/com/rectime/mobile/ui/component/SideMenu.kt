package com.rectime.mobile.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.rectime.mobile.feature.auth.AuthSession
import com.rectime.mobile.feature.auth.toUserProfile
import com.rectime.mobile.feature.settings.SettingsScreen
import com.rectime.mobile.feature.theme.ThemeSheet
import com.rectime.mobile.ui.theme.AppTheme
import com.rectime.mobile.ui.theme.ThemeStateHolder
import com.woowla.compose.icon.collections.fontawesome.fontawesome.SolidGroup
import com.woowla.compose.icon.collections.fontawesome.fontawesome.solid.Gear
import com.woowla.compose.icon.collections.fontawesome.fontawesome.solid.Palette
import com.woowla.compose.icon.collections.fontawesome.fontawesome.solid.RightFromBracket

private sealed class SideMenuAction {
    data class Push(val screen: Screen) : SideMenuAction()
    data class Custom(val onClick: () -> Unit) : SideMenuAction()
}

private data class SideMenuItemConfig(
    val title: String,
    val icon: ImageVector,
    val action: SideMenuAction,
)

@Composable
fun SideMenu(
    revealWidthDp: Dp,
    onPushFromMenu: (Screen) -> Unit,
    onPresentThemeSheet: (Screen) -> Unit,
    themeStateHolder: ThemeStateHolder,
    session: AuthSession,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mainItems = listOf(
        SideMenuItemConfig(
            title = "設定",
            icon = SolidGroup.Gear,
            action = SideMenuAction.Push(SettingsScreen),
        ),
        SideMenuItemConfig(
            title = "ログアウト",
            icon = SolidGroup.RightFromBracket,
            action = SideMenuAction.Custom(onLogout),
        ),
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AppTheme.colors.navigationBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(revealWidthDp)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(top = AppTheme.layout.headerSpacing, bottom = 20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val user = session.user.toUserProfile()
                UserAvatar(
                    profile = user,
                    modifier = Modifier.size(48.dp),
                )

                Column {
                    Text(text = user.name, color = AppTheme.colors.textPrimary)
                    Text(text = session.user.email, color = AppTheme.colors.textSecondary)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIconButton(
                    onClick = { onPresentThemeSheet(ThemeSheet(themeStateHolder)) },
                    color = AppTheme.colors.surfacePrimary,
                ) {
                    Icon(
                        imageVector = SolidGroup.Palette,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = AppTheme.colors.textSecondary,
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                Text(
                    text = "rectime",
                    color = AppTheme.colors.textMuted,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun MenuItem(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
) {
    SideMenuBtn(
        icon = icon,
        title = title,
        onClick = onClick,
    )
}
