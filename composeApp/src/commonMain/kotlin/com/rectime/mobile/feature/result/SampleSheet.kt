package com.rectime.mobile.feature.result

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rectime.mobile.ui.component.PressSurface
import com.rectime.mobile.ui.theme.AppTheme

@Composable
fun SampleSheet(
    title: String,
    description: String,
    onPrimaryAction: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = title, color = AppTheme.colors.textPrimary, fontWeight = FontWeight.SemiBold)
        Text(text = description, color = AppTheme.colors.textSecondary)
        PressSurface(
            onClick = onPrimaryAction,
            color = AppTheme.colors.surfaceAccent,
        ) {
            Text(text = "閉じる", color = AppTheme.colors.textPrimary)
        }
    }
}
