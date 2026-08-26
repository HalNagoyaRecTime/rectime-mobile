package com.rectime.mobile.ui.component

import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rectime.mobile.ui.theme.AppTheme
import com.woowla.compose.icon.collections.fontawesome.fontawesome.SolidGroup
import com.woowla.compose.icon.collections.fontawesome.fontawesome.solid.ChevronLeft

private val BackIconOffsetX = 15.dp

@Composable
fun PushHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onTrailingClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    HeaderTitleBar(
        title = title,
        modifier = modifier,
        onLeadingClick = onBack,
        leading = {
            Icon(
                imageVector = SolidGroup.ChevronLeft,
                contentDescription = "戻る",
                tint = AppTheme.colors.textDetailsScreenHeader,
                modifier = Modifier
                    .size(25.dp)
                    .offset(x = BackIconOffsetX),
            )
        },
        onTrailingClick = onTrailingClick,
        trailing = trailing,
    )
}
