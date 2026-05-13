package com.rectime.mobile.feature.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rectime.mobile.app.navigation.NavigationController
import com.rectime.mobile.app.navigation.Screen
import com.rectime.mobile.ui.component.PressSurface
import com.rectime.mobile.ui.component.SheetScaffold
import com.rectime.mobile.ui.theme.AppTheme
import com.rectime.mobile.ui.theme.ThemeId
import com.rectime.mobile.ui.theme.ThemeMode
import com.rectime.mobile.ui.theme.ThemeStateHolder

data class ThemeSheet(val themeStateHolder: ThemeStateHolder) : Screen {
    override val key: String = "theme_sheet"

    @Composable
    override fun Content(navigationController: NavigationController) {
        SheetScaffold(
            title = "テーマ",
            onClose = { navigationController.requestDismissSheet() },
        ) {
            ThemeSheetContent(themeStateHolder = themeStateHolder)
        }
    }
}

@Composable
private fun ThemeSheetContent(themeStateHolder: ThemeStateHolder) {
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
