package com.rectime.mobile.ui.component

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rectime.mobile.ui.theme.AppTheme
import com.woowla.compose.icon.collections.fontawesome.fontawesome.SolidGroup
import com.woowla.compose.icon.collections.fontawesome.fontawesome.solid.ArrowLeft

@Composable
fun PushScreenHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onTrailingClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    ScreenHeader(
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
