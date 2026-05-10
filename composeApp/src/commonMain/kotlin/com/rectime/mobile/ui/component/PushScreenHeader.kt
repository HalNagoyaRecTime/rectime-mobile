package com.rectime.mobile.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun PushScreenHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    ScreenHeader(
        title = title,
        modifier = modifier,
        leading = { BackBtn(onClick = onBack) },
        trailing = trailing,
    )
}
