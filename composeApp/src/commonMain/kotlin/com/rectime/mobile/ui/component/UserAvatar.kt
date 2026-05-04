package com.rectime.mobile.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rectime.mobile.ui.theme.AppTheme

@Composable
fun UserAvatar(initials: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(AppTheme.colors.surfaceAccentStrong),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = initials, color = AppTheme.colors.textOnAccent, fontWeight = FontWeight.Bold)
    }
}
