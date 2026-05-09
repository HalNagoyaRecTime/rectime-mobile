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
fun BackBtn(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppBtn(
        onClick = onClick,
        modifier = modifier,
        size = AppBtnSize.Sm,
    ) {
        Icon(
            imageVector = SolidGroup.ArrowLeft,
            contentDescription = null,
            tint = AppTheme.colors.textPrimary,
            modifier = Modifier.size(16.dp),
        )
    }
}
