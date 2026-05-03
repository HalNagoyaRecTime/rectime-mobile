package com.rectime.mobile

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.woowla.compose.icon.collections.fontawesome.fontawesome.SolidGroup
import com.woowla.compose.icon.collections.fontawesome.fontawesome.solid.CalendarDays
import com.woowla.compose.icon.collections.fontawesome.fontawesome.solid.House

@Composable
fun PressSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color = AppTheme.colors.surfaceMuted,
    shape: RoundedCornerShape = RoundedCornerShape(16.dp),
    contentPadding: PaddingValues = PaddingValues(12.dp),
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(stiffness = 500f, dampingRatio = 0.8f),
        label = "pressScale",
    )
    Box(
        modifier = modifier
            .graphicsLayer(scaleX = scale, scaleY = scale, shape = shape, clip = true)
            .background(color)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
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
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    PressSurface(
        onClick = onClick,
        color = AppTheme.colors.surfaceMuted,
        contentPadding = PaddingValues(10.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = AppTheme.colors.textPrimary,
            modifier = Modifier.size(18.dp),
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
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(AppTheme.colors.navigationSurface),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(AppTheme.colors.borderSubtle),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 10.dp, vertical = 8.dp)
                .heightIn(min = LayoutTokens.bottomTabMinHeight),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BottomNavigationItem(
                label = "ホーム",
                icon = SolidGroup.House,
                selected = currentRoute == RootRoute.Home,
                onClick = { onSelectRoot(RootRoute.Home) },
                modifier = Modifier.weight(1f),
            )
            BottomNavigationItem(
                label = "日程",
                icon = SolidGroup.CalendarDays,
                selected = currentRoute == RootRoute.Calendar,
                onClick = { onSelectRoot(RootRoute.Calendar) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun BottomNavigationItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val contentColor = if (selected) AppTheme.colors.navigationActive else AppTheme.colors.navigationInactive

    PressSurface(
        onClick = onClick,
        modifier = modifier,
        color = androidx.compose.ui.graphics.Color.Transparent,
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(vertical = 10.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = label,
                color = contentColor,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
