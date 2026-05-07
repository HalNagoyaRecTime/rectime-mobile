package com.rectime.mobile.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.rectime.mobile.core.model.UserProfile
import com.rectime.mobile.ui.theme.AppTheme

@Composable
fun UserAvatar(
    profile: UserProfile,
    modifier: Modifier = Modifier
) {
    UserAvatar(
        initials = profile.initials,
        imageUrl = profile.imageUrl,
        modifier = modifier
    )
}

@Composable
fun UserAvatar(
    initials: String,
    imageUrl: String? = null,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(AppTheme.colors.surfaceAccentStrong),
        contentAlignment = Alignment.Center,
    ) {
        if (imageUrl != null) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(text = initials, color = AppTheme.colors.textOnAccent, fontWeight = FontWeight.Bold)
        }
    }
}
