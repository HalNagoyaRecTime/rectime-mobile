package com.rectime.mobile.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.rectime.mobile.ui.theme.AppBtnVisualStyle
import com.rectime.mobile.ui.theme.AppTheme

@Composable
fun SideMenuBtn(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: AppBtnSize = AppBtnSize.Md,
    visualStyle: AppBtnVisualStyle = AppTheme.buttons.defaultVisualStyle,
) {
    AppBtn(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        size = size,
        visualStyle = visualStyle,
        contentColor = AppTheme.colors.textPrimary,
        containerColor = AppTheme.colors.navigationSurface.copy(
            alpha = if (visualStyle == AppBtnVisualStyle.LiquidGlass) 0.48f else 0.72f,
        ),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = AppTheme.colors.textSecondary,
                modifier = Modifier.size(sideMenuIconSize(size)),
            )
            Text(text = title, color = AppTheme.colors.textPrimary)
        }
    }
}

private fun sideMenuIconSize(size: AppBtnSize) = when (size) {
    AppBtnSize.Sm -> 14.dp
    AppBtnSize.Md -> 16.dp
    AppBtnSize.Lg -> 18.dp
}
