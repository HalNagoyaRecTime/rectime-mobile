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

/** 保存済み画像を表示しながら通常の取得を行い、失敗しても画像を消さない。 */
@Composable
internal fun rememberCachedImagePainter(url: String): AsyncImagePainter {
    val context = LocalPlatformContext.current
    val networkPainter = rememberAsyncImagePainter(url)
    val state by networkPainter.state.collectAsState()
    if (state is AsyncImagePainter.State.Success) return networkPainter
    val cachedRequest = remember(context, url) {
        ImageRequest.Builder(context).data(url)
            .networkCachePolicy(CachePolicy.DISABLED)
            .diskCachePolicy(CachePolicy.READ_ONLY)
            .build()
    }
    val cachedPainter = rememberAsyncImagePainter(cachedRequest)
    val cachedState by cachedPainter.state.collectAsState()
    return if (cachedState is AsyncImagePainter.State.Success) cachedPainter else networkPainter
}
