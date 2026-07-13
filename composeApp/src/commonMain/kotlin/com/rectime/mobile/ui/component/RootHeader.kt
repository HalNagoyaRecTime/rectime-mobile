package com.rectime.mobile.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.rectime.mobile.core.model.UserProfile
import com.rectime.mobile.ui.theme.AppTheme

@Composable
fun RootHeader(
    title: String,
    profile: UserProfile,
    onOpenMenu: () -> Unit,
    modifier: Modifier = Modifier,
    onTrailingClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(AppTheme.layout.headerAction),
        contentAlignment = Alignment.Center,
    ) {
        AppIconButton(
            onClick = onOpenMenu,
            content = {
                UserAvatar(
                    profile = profile,
                    modifier = Modifier.fillMaxSize(),
                )
            },
            modifier = Modifier.align(Alignment.CenterStart),
        )
        Text(
            text = title,
            color = AppTheme.colors.textPrimary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppTheme.layout.headerAction),
        )
        if (trailing != null || onTrailingClick != null) {
            AppIconButton(
                onClick = onTrailingClick,
                content = trailing,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
    }
}
