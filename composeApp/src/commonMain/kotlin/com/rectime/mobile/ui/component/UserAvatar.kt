package com.rectime.mobile.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import com.rectime.mobile.ui.theme.AppTheme

private val AvatarBackgrounds = listOf(
    Color(0xFFD4EEF0), Color(0xFFF5DDD5), Color(0xFFF5EBCB),
    Color(0xFFDFE4F5), Color(0xFFEADDF1), Color(0xFFDDEEDF),
)

// 同じユーザーは起動・ログアウト・名前変更を経ても同じ色になる。
internal fun avatarBackground(userId: String): Color {
    return AvatarBackgrounds[avatarSeed(userId) % AvatarBackgrounds.size]
}

/** 写真のない時や画像の描画失敗時も、スポーツのピクトグラムを表示する。追加通信は行わない。 */
@Composable
internal fun UserAvatar(
    userId: String,
    photoBytes: ByteArray?,
    modifier: Modifier = Modifier,
    sportOverride: SportAvatarPictogram? = null,
    usePhoto: Boolean = true,
) {
    val platformContext = LocalPlatformContext.current
    val background = remember(userId) { avatarBackground(userId) }
    val sport = sportOverride ?: remember(userId) { avatarSport(userId) }
    val request = remember(platformContext, userId, photoBytes) {
        photoBytes?.let { bytes ->
            ImageRequest.Builder(platformContext)
                .data(bytes)
                // 写真の保存・削除はProfilePhotoRepositoryだけで管理する。
                .memoryCachePolicy(CachePolicy.DISABLED)
                .diskCachePolicy(CachePolicy.DISABLED)
                .build()
        }
    }
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(AppTheme.colors.settingBackground)
            .border(3.dp, AppTheme.colors.settingBackground, CircleShape)
            .padding(3.dp)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.fillMaxSize().clip(CircleShape).background(background), contentAlignment = Alignment.Center) {
            Icon(
                painter = sportAvatarPainter(sport),
                contentDescription = null,
                tint = Color(0xFF354657),
                // 手足・用具を含む矩形全体が丸の内側に収まる大きさにする。
                modifier = Modifier.fillMaxSize(0.64f),
            )
            if (usePhoto && request != null) {
                AsyncImage(
                    model = request,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                )
            }
        }
    }
}
