package com.rectime.mobile.ui.component

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rectime.mobile.ui.theme.AppTheme
import com.woowla.compose.icon.collections.fontawesome.fontawesome.SolidGroup
import com.woowla.compose.icon.collections.fontawesome.fontawesome.solid.Xmark

@Composable
fun SheetHeader(
    title: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ScreenHeader(
        title = title,
        modifier = modifier,
        trailing = {
            AppBtn(
                onClick = onClose,
                size = AppBtnSize.Sm,
            ) {
                Icon(
                    imageVector = SolidGroup.Xmark,
                    contentDescription = "閉じる",
                    tint = AppTheme.colors.textPrimary,
                    modifier = Modifier.size(16.dp),
                )
            }
        },
    )
}
