package com.rectime.mobile.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rectime.mobile.core.model.UserProfile
import com.rectime.mobile.ui.theme.AppTheme

@Composable
fun HeaderActionButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppBtn(
        onClick = onClick,
        size = AppBtnSize.Sm,
        modifier = modifier,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = AppTheme.colors.textPrimary,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
fun RootScreenHeader(
    title: String,
    profile: UserProfile,
    onOpenMenu: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    ScreenHeader(
        title = title,
        modifier = modifier,
        leading = {
            PressSurface(
                onClick = onOpenMenu,
                color = Color.Transparent,
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                UserAvatar(
                    profile = profile,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        },
        trailing = trailing,
    )
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
            .height(AppTheme.layout.headerAction),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(AppTheme.layout.headerAction), contentAlignment = Alignment.Center) {
            leading?.invoke()
        }
        Text(
            text = title,
            color = AppTheme.colors.textPrimary,
            fontWeight = FontWeight.SemiBold,
        )
        Box(modifier = Modifier.size(AppTheme.layout.headerAction), contentAlignment = Alignment.Center) {
            trailing?.invoke()
        }
    }
}
