package com.rectime.mobile.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp

@Composable
fun RootHeader(
    title: String,
    modifier: Modifier = Modifier,
    onTrailingClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    HeaderTitleBar(
        title = title,
        modifier = modifier,
        fontSize = 18.sp,
        onTrailingClick = onTrailingClick,
        trailing = trailing,
    )
}
