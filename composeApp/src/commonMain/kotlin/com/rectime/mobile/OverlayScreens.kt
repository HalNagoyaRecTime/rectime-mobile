package com.rectime.mobile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

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
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            UserAvatar(initials = "RT")
            Text(text = "Rectime Operator", color = AppTheme.colors.textPrimary)
            Text(text = "operator@rectime.app", color = AppTheme.colors.textSecondary)
        }

        Spacer(modifier = Modifier.height(8.dp))

        MenuItem("Operator", onClick = { onPushFromMenu(PushRoute.OperatorMenu) })
        MenuItem("MatchInfo", onClick = { onPushFromMenu(PushRoute.MatchInfo) })
        MenuItem("Settings", onClick = { onPushFromMenu(PushRoute.Settings) })
        MenuItem("HelpCenter", onClick = { onPushFromMenu(PushRoute.HelpCenter) })
        MenuItem("Dev", onClick = { onPushFromMenu(PushRoute.Dev) })
        MenuItem("Theme", onClick = onPresentThemeSheet)
    }
}

@Composable
private fun MenuItem(
    title: String,
    onClick: () -> Unit,
) {
    PressSurface(
        onClick = onClick,
        color = AppTheme.colors.navigationSurface,
    ) {
        Text(text = title, color = AppTheme.colors.textPrimary)
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
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PressSurface(onClick = onRequestPop, modifier = Modifier.weight(1f)) {
                Text("戻る", color = AppTheme.colors.textPrimary)
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

