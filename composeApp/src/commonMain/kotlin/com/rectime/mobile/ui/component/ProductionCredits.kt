package com.rectime.mobile.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import com.rectime.mobile.ui.theme.AppTheme

internal const val AppTeamCredit = "Developed by RE:CREATION App Team"

@Composable
fun ProductionCredits(
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = horizontalAlignment,
    ) {
        Text(
            text = "Produced by HAL Nagoya",
            fontSize = 12.sp,
            color = AppTheme.colors.textCopyRight,
        )
        Text(
            text = AppTeamCredit,
            fontSize = 12.sp,
            color = AppTheme.colors.textCopyRight,
        )
    }
}
