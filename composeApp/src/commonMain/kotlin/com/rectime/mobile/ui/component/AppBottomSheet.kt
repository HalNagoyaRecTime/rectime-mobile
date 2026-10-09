package com.rectime.mobile.ui.component

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import com.rectime.mobile.ui.theme.AppTheme

/** 見た目だけを共通化し、ドラッグ・終了・インセットは標準シートに任せる。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppBottomSheet(
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = { SheetDragHandle() },
        containerColor = AppTheme.colors.commonBackground,
        shape = RoundedCornerShape(topStart = AppTheme.radius.sheet, topEnd = AppTheme.radius.sheet),
        content = content,
    )
}
