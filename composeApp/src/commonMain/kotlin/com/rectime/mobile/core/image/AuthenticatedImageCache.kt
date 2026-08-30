package com.rectime.mobile.core.image

import coil3.ImageLoader

interface AuthenticatedImageCache {
    suspend fun clear()
}

// App.ktで生成したImageLoaderを保持する。ViewModelへPlatformContextを引き回さずに
// 認証ライフサイクル(ログアウト・401・利用者切替)からCacheを消せるようにするため。
object CoilAuthenticatedImageCache : AuthenticatedImageCache {
    var imageLoader: ImageLoader? = null

    override suspend fun clear() {
        val loader = imageLoader ?: return
        loader.memoryCache?.clear()
        loader.diskCache?.clear()
    }
}
