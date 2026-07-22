package com.rectime.mobile.ui.component

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
    HeaderTitleBar(
        title = title,
        modifier = modifier,
        onLeadingClick = onLeadingClick,
        leading = leading,
        onTrailingClick = onTrailingClick,
        trailing = trailing,
    )
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
