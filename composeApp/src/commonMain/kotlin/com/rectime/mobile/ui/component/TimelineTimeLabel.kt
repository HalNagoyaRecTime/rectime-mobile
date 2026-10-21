package com.rectime.mobile.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rectime.mobile.ui.theme.AppTheme

@Composable
internal fun TimelineTimeLabel(text: String, modifier: Modifier = Modifier) {
    Box(modifier.height(20.dp).padding(end = 6.dp), contentAlignment = Alignment.CenterEnd) {
        AppTimeText(text, fontSize = 12.sp, lineHeight = 20.sp, color = AppTheme.colors.textScheduleTimeBar)
    }
}
