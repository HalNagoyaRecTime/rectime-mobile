package com.rectime.mobile.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.request.CachePolicy
import coil3.request.ImageRequest

/** 保存済み画像を通信なしで先に表示し、再取得が成功したら置き換える。 */
@Composable
internal fun rememberCachedImagePainter(url: String): AsyncImagePainter {
    val context = LocalPlatformContext.current
    val cached = rememberAsyncImagePainter(ImageRequest.Builder(context).data(url)
        .networkCachePolicy(CachePolicy.DISABLED).build())
    val live = rememberAsyncImagePainter(url)
    val saved by cached.state.collectAsState()
    val current by live.state.collectAsState()
    return if ((current is AsyncImagePainter.State.Empty || current is AsyncImagePainter.State.Loading) &&
        saved is AsyncImagePainter.State.Success) cached else live
}
