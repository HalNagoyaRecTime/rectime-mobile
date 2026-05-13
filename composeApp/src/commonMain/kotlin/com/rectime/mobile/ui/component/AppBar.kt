package com.rectime.mobile.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rectime.mobile.ui.theme.AppTheme
import com.woowla.compose.icon.collections.fontawesome.fontawesome.SolidGroup
import com.woowla.compose.icon.collections.fontawesome.fontawesome.solid.ArrowLeft
import com.woowla.compose.icon.collections.fontawesome.fontawesome.solid.Xmark

@Composable
fun AppBar(
    title: String,
    modifier: Modifier = Modifier,
    onLeadingClick: (() -> Unit)? = null,
    leading: @Composable (() -> Unit)? = null,
    onTrailingClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(AppTheme.layout.headerAction),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title,
            color = AppTheme.colors.textPrimary,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppTheme.layout.headerAction),
        )
        if (leading != null || onLeadingClick != null) {
            AppIconButton(
                onClick = onLeadingClick,
                content = leading,
                modifier = Modifier.align(Alignment.CenterStart),
            )
        }
        if (trailing != null || onTrailingClick != null) {
            AppIconButton(
                onClick = onTrailingClick,
                content = trailing,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
    }
}

@Composable
fun PushAppBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onTrailingClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    AppBar(
        title = title,
        modifier = modifier,
        onLeadingClick = onBack,
        leading = {
            Icon(
                imageVector = SolidGroup.ArrowLeft,
                contentDescription = "戻る",
                tint = AppTheme.colors.textPrimary,
                modifier = Modifier.size(18.dp),
            )
        },
        onTrailingClick = onTrailingClick,
        trailing = trailing,
    )
}

@Composable
fun SheetAppBar(
    title: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppBar(
        title = title,
        modifier = modifier,
        onTrailingClick = onClose,
        trailing = {
            Icon(
                imageVector = SolidGroup.Xmark,
                contentDescription = "閉じる",
                tint = AppTheme.colors.textPrimary,
                modifier = Modifier.size(18.dp),
            )
        },
    )
}
