package com.rectime.mobile.ui.component

import androidx.compose.runtime.*
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import okio.Path.Companion.toPath
import com.rectime.mobile.app.getCacheDir
import com.rectime.mobile.core.attachment.*
import com.rectime.mobile.core.network.createAttachmentHttpClient
import coil3.request.CachePolicy
import com.rectime.mobile.core.network.MobileAuthHeadersPlugin
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow

internal val LocalAttachmentCache = staticCompositionLocalOf<AttachmentCache?> { null }

@Composable
internal fun rememberAttachmentLease(url: String, enabled: Boolean = true, attempt: Int = 0, progress: MutableStateFlow<AttachmentProgress>? = null): State<Result<AttachmentLease>?> {
    val context = LocalPlatformContext.current
    val providedCache = LocalAttachmentCache.current
    return produceState<Result<AttachmentLease>?>(null, url, enabled, attempt, providedCache) {
        value = null
        if (!enabled) return@produceState
        var lease: AttachmentLease? = null
        try {
            val cache = providedCache ?: AttachmentCaches.get(getCacheDir(context) + "/attachments") {
                createAttachmentHttpClient().config { install(MobileAuthHeadersPlugin) }
            }
            lease = cache.acquireSaved(url)
            lease?.let { value = Result.success(it) }
            val refreshed = cache.acquire(url, progress ?: MutableStateFlow(AttachmentProgress()), forceRefresh = attempt > 0)
            lease?.release?.invoke()
            lease = refreshed
            value = Result.success(refreshed)
            awaitCancellation()
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) { value = Result.failure(e)
        } finally { withContext(NonCancellable) { lease?.release?.invoke() } }
    }
}

/** 取得は表示側のタイミングで行い、Coilへは保存済みファイルだけ渡す。 */
@Composable
internal fun rememberCachedImagePainter(url: String, enabled: Boolean = true, attempt: Int = 0, progress: MutableStateFlow<AttachmentProgress>? = null): AsyncImagePainter {
    val context = LocalPlatformContext.current
    val result by rememberAttachmentLease(url, enabled, attempt, progress)
    val lease = result?.getOrNull()
    return rememberAsyncImagePainter(lease?.let {
        ImageRequest.Builder(context).data(it.path.toPath())
            .memoryCachePolicy(CachePolicy.DISABLED).build()
    } ?: if (result?.isFailure == true) ImageRequest.Builder(context).data("attachment-unavailable").build() else null)
}
