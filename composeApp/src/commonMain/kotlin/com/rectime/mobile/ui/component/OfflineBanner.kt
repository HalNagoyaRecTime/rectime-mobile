package com.rectime.mobile.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.rectime.mobile.ui.theme.AppTheme

@Composable
fun OfflineBanner(message: String, modifier: Modifier = Modifier) {
    Text(
        text = message,
        color = AppTheme.colors.textSecondary,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(AppTheme.colors.surfaceMuted)
            .padding(PaddingValues(horizontal = 12.dp, vertical = 10.dp)),
    )
}
