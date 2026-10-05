package com.rectime.mobile.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.request.CachePolicy
import coil3.request.ImageRequest

/** 通常の再検証に失敗した場合だけ保存済み画像へ戻す。別の画像キャッシュは作らない。 */
@Composable
internal fun rememberCachedImagePainter(url: String): AsyncImagePainter {
    val context = LocalPlatformContext.current
    val networkPainter = rememberAsyncImagePainter(url)
    val state by networkPainter.state.collectAsState()
    if (state !is AsyncImagePainter.State.Error) return networkPainter
    val cachedRequest = remember(context, url) {
        ImageRequest.Builder(context).data(url)
            .networkCachePolicy(CachePolicy.DISABLED)
            .diskCachePolicy(CachePolicy.READ_ONLY)
            .build()
    }
    return rememberAsyncImagePainter(cachedRequest)
}
