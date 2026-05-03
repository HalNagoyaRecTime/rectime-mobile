package com.rectime.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun PressSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color = AppTheme.colors.surfaceMuted,
    shape: RoundedCornerShape = RoundedCornerShape(16.dp),
    contentPadding: PaddingValues = PaddingValues(12.dp),
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(color)
            .clickable(onClick = onClick)
            .padding(contentPadding),
    ) {
        androidx.compose.material3.ProvideTextStyle(
            value = LocalTextStyle.current.copy(color = AppTheme.colors.textPrimary),
            content = content,
        )
    }
}

@Composable
fun UserAvatar(initials: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(AppTheme.colors.surfaceAccentStrong),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = initials, color = AppTheme.colors.textOnAccent, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun HeaderActionButton(
    label: String,
    onClick: () -> Unit,
) {
    PressSurface(
        onClick = onClick,
        color = AppTheme.colors.surfaceMuted,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            color = AppTheme.colors.textPrimary,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(LayoutTokens.headerAction),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(LayoutTokens.headerAction), contentAlignment = Alignment.Center) {
            leading?.invoke()
        }
        Text(
            text = title,
            color = AppTheme.colors.textPrimary,
            fontWeight = FontWeight.SemiBold,
        )
        Box(modifier = Modifier.size(LayoutTokens.headerAction), contentAlignment = Alignment.Center) {
            trailing?.invoke()
        }
    }
}

@Composable
fun BottomNavigationBar(
    currentRoute: RootRoute,
    onSelectRoot: (RootRoute) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = LayoutTokens.bottomInsetMin)
            .background(
                color = AppTheme.colors.navigationSurface,
                shape = RoundedCornerShape(18.dp),
            )
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .heightIn(min = LayoutTokens.bottomTabMinHeight),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BottomNavigationItem(
            label = "Home",
            selected = currentRoute == RootRoute.Home,
            onClick = { onSelectRoot(RootRoute.Home) },
            modifier = Modifier.weight(1f),
        )
        BottomNavigationItem(
            label = "Calendar",
            selected = currentRoute == RootRoute.Calendar,
            onClick = { onSelectRoot(RootRoute.Calendar) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun BottomNavigationItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backgroundColor = if (selected) AppTheme.colors.surfaceAccent else AppTheme.colors.navigationSurface
    val textColor = if (selected) AppTheme.colors.navigationActive else AppTheme.colors.navigationInactive

    PressSurface(
        onClick = onClick,
        modifier = modifier,
        color = backgroundColor,
        contentPadding = PaddingValues(vertical = 12.dp),
    ) {
        Text(
            text = label,
            color = textColor,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

