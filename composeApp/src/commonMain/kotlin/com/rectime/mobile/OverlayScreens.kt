package com.rectime.mobile

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.woowla.compose.icon.collections.fontawesome.fontawesome.SolidGroup
import com.woowla.compose.icon.collections.fontawesome.fontawesome.solid.ArrowLeft
import com.woowla.compose.icon.collections.fontawesome.fontawesome.solid.CircleQuestion
import com.woowla.compose.icon.collections.fontawesome.fontawesome.solid.Code
import com.woowla.compose.icon.collections.fontawesome.fontawesome.solid.Gear
import com.woowla.compose.icon.collections.fontawesome.fontawesome.solid.Palette
import com.woowla.compose.icon.collections.fontawesome.fontawesome.solid.Trophy
import com.woowla.compose.icon.collections.fontawesome.fontawesome.solid.Wrench

@Composable
fun SideMenu(
    revealWidthDp: androidx.compose.ui.unit.Dp,
    onPushFromMenu: (PushRoute) -> Unit,
    onPresentThemeSheet: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(revealWidthDp)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 20.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            UserAvatar(initials = "RT")
            Text(text = "Rectime Operator", color = AppTheme.colors.textPrimary)
            Text(text = "operator@rectime.app", color = AppTheme.colors.textSecondary)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MenuItem(
                icon = SolidGroup.Wrench,
                title = "運営メニュー",
                onClick = { onPushFromMenu(PushRoute.OperatorMenu) },
            )
            MenuItem(
                icon = SolidGroup.Trophy,
                title = "対戦情報",
                onClick = { onPushFromMenu(PushRoute.MatchInfo) },
            )
            MenuItem(
                icon = SolidGroup.Gear,
                title = "設定",
                onClick = { onPushFromMenu(PushRoute.Settings) },
            )
            MenuItem(
                icon = SolidGroup.CircleQuestion,
                title = "ヘルプセンター",
                onClick = { onPushFromMenu(PushRoute.HelpCenter) },
            )
            MenuItem(
                icon = SolidGroup.Code,
                title = "開発メニュー",
                onClick = { onPushFromMenu(PushRoute.Dev) },
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MenuItem(
                icon = SolidGroup.Palette,
                title = "テーマ変更",
                onClick = onPresentThemeSheet,
            )
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

@Composable
fun PushCardScreen(
    route: PushRoute,
    onRequestPop: () -> Unit,
    onPresentSampleSheet: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = when (route) {
        PushRoute.Detail -> "Detail"
        PushRoute.Notifications -> "Notifications"
        PushRoute.Settings -> "Settings"
        PushRoute.HelpCenter -> "HelpCenter"
        PushRoute.MatchInfo -> "MatchInfo"
        PushRoute.OperatorMenu -> "OperatorMenu"
        PushRoute.Dev -> "Dev"
    }
    val description = when (route) {
        PushRoute.Detail -> "試合詳細のワイヤー画面"
        PushRoute.Notifications -> "通知一覧のワイヤー画面"
        PushRoute.Settings -> "設定画面のワイヤー画面"
        PushRoute.HelpCenter -> "ヘルプセンターのワイヤー画面"
        PushRoute.MatchInfo -> "大会情報のワイヤー画面"
        PushRoute.OperatorMenu -> "運営メニューのワイヤー画面"
        PushRoute.Dev -> "開発者向けワイヤー画面"
    }

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
            PressSurface(onClick = onRequestPop, modifier = Modifier.weight(1f)) {
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
            PressSurface(onClick = onPresentSampleSheet, modifier = Modifier.weight(1f)) {
                Text("SampleSheet", color = AppTheme.colors.textPrimary)
            }
        }

        Text(text = title, color = AppTheme.colors.textPrimary, fontWeight = FontWeight.Bold)
        Text(text = description, color = AppTheme.colors.textSecondary)
    }
}

@Composable
fun ThemeSheet(themeStateHolder: ThemeStateHolder) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(text = "Theme", color = AppTheme.colors.textPrimary, fontWeight = FontWeight.SemiBold)
        Text(text = "mode", color = AppTheme.colors.textSecondary)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ThemeChip(
                label = "system",
                selected = themeStateHolder.mode == ThemeMode.System,
                onClick = { themeStateHolder.setMode(ThemeMode.System) },
                modifier = Modifier.weight(1f),
            )
            ThemeChip(
                label = "light",
                selected = themeStateHolder.mode == ThemeMode.Light,
                onClick = { themeStateHolder.setMode(ThemeMode.Light) },
                modifier = Modifier.weight(1f),
            )
            ThemeChip(
                label = "dark",
                selected = themeStateHolder.mode == ThemeMode.Dark,
                onClick = { themeStateHolder.setMode(ThemeMode.Dark) },
                modifier = Modifier.weight(1f),
            )
        }

        Text(text = "theme", color = AppTheme.colors.textSecondary)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ThemeChip(
                label = "default",
                selected = themeStateHolder.themeId == ThemeId.Default,
                onClick = { themeStateHolder.setThemeId(ThemeId.Default) },
                modifier = Modifier.weight(1f),
            )
            ThemeChip(
                label = "blue-2024",
                selected = themeStateHolder.themeId == ThemeId.Blue2024,
                onClick = { themeStateHolder.setThemeId(ThemeId.Blue2024) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ThemeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PressSurface(
        onClick = onClick,
        modifier = modifier,
        color = if (selected) AppTheme.colors.surfaceAccent else AppTheme.colors.surfaceMuted,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 10.dp),
    ) {
        Text(
            text = label,
            color = if (selected) AppTheme.colors.textPrimary else AppTheme.colors.textSecondary,
        )
    }
}

@Composable
fun SampleSheet(
    title: String,
    description: String,
    onPrimaryAction: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = title, color = AppTheme.colors.textPrimary, fontWeight = FontWeight.SemiBold)
        Text(text = description, color = AppTheme.colors.textSecondary)
        PressSurface(
            onClick = onPrimaryAction,
            color = AppTheme.colors.surfaceAccent,
        ) {
            Text(text = "閉じる", color = AppTheme.colors.textPrimary)
        }
    }
}
