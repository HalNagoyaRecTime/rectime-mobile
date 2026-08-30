package com.rectime.mobile.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import com.rectime.mobile.core.image.authenticatedImageCacheKey
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
        userId = profile.id,
        modifier = modifier
    )
}

@Composable
fun UserAvatar(
    initials: String,
    imageUrl: String? = null,
    userId: String? = null,
    modifier: Modifier = Modifier
) {
    // 認証ヘッダーはApp.ktのKtorNetworkFetcherFactoryクライアントに
    // installしたMobileAuthHeadersPluginが自動付与するため、ここで個別に
    // NetworkHeadersを組み立てて付与すると二重になってしまう。
    val platformContext = LocalPlatformContext.current
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(AppTheme.colors.surfaceAccentStrong),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = initials, color = AppTheme.colors.textOnAccent, fontWeight = FontWeight.Bold)
        if (!imageUrl.isNullOrBlank()) {
            val cacheKey = authenticatedImageCacheKey(imageUrl, userId)
            AsyncImage(
                model = ImageRequest.Builder(platformContext)
                    .data(imageUrl)
                    .memoryCacheKey(cacheKey)
                    // Profile画像はDiskへ残さない。共有端末で前の利用者の画像が
                    // ファイルとして残らないようにするため。
                    .diskCachePolicy(CachePolicy.DISABLED)
                    // 利用者を特定できないときはMemoryCacheも使わない。
                    // Keyを分けられず、前の利用者の画像を引く恐れがあるため。
                    .memoryCachePolicy(
                        if (cacheKey == null) CachePolicy.DISABLED else CachePolicy.ENABLED
                    )
                    .build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}
