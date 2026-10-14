package com.rectime.mobile.core.image

import coil3.annotation.ExperimentalCoilApi
import coil3.decode.DataSource
import coil3.intercept.Interceptor
import coil3.network.CacheStrategy
import coil3.network.NetworkClient
import coil3.network.NetworkRequest
import coil3.network.NetworkResponse
import coil3.network.cachecontrol.CacheControlCacheStrategy
import coil3.request.CachePolicy
import coil3.request.ErrorResult
import coil3.request.Options
import coil3.request.SuccessResult
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock

internal const val IMAGE_CACHE_FRESH_MILLIS = 12 * 60 * 60 * 1_000L

/** URLごとの取得成功時刻。画像本体・永続時刻はCoilのディスクキャッシュに任せる。 */
internal class ImageFreshness(private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() }) {
    private val mutex = Mutex()
    private val fetched = linkedMapOf<String, Long>()
    private val forbidden = mutableSetOf<String>()
    suspend fun forbid(url: String) = mutex.withLock { fetched.remove(url); forbidden.add(url); Unit }
    suspend fun isForbidden(url: String): Boolean = mutex.withLock { url in forbidden }

    fun isFresh(receivedAt: Long): Boolean = receivedAt > 0 &&
        now() - receivedAt in 0 until IMAGE_CACHE_FRESH_MILLIS

    suspend fun isFresh(url: String): Boolean = mutex.withLock { fetched[url]?.let(::isFresh) == true }

    suspend fun record(url: String, receivedAt: Long?) = mutex.withLock {
        fetched.remove(url)
        if (receivedAt != null) { fetched[url] = receivedAt; forbidden.remove(url) }
        while (fetched.size > 512) fetched.remove(fetched.keys.first())
    }
}

/** メモリで期限判定を飛ばさず、期限後はETag等で再検証する。 */
@OptIn(ExperimentalCoilApi::class)
internal class ImageCacheStrategy(private val freshness: ImageFreshness) : CacheStrategy {
    private val http = CacheControlCacheStrategy()

    override suspend fun read(cacheResponse: NetworkResponse, networkRequest: NetworkRequest, options: Options): CacheStrategy.ReadResult {
        val noStore = cacheResponse.headers["Cache-Control"].hasDirective("no-store") ||
            networkRequest.headers["Cache-Control"].hasDirective("no-store") || freshness.isForbidden(networkRequest.url)
        if (noStore) freshness.forbid(networkRequest.url)
        else freshness.record(networkRequest.url, cacheResponse.responseMillis)
        if (!noStore && (!options.networkCachePolicy.readEnabled ||
                    (freshness.isFresh(cacheResponse.responseMillis) &&
                        !networkRequest.headers["Cache-Control"].hasDirective("no-cache")))) {
            return CacheStrategy.ReadResult(cacheResponse)
        }
        // サーバーの長いmax-ageでも12時間後には条件付き取得を行う。
        val expired = cacheResponse.copy(headers = cacheResponse.headers.newBuilder()
            .set("Cache-Control", if (noStore) "no-store" else "max-age=0")
            .build())
        return http.read(expired, networkRequest, options)
    }

    override suspend fun write(cacheResponse: NetworkResponse?, networkRequest: NetworkRequest, networkResponse: NetworkResponse, options: Options): CacheStrategy.WriteResult {
        if (networkResponse.code !in 200..299 && networkResponse.code != 304) return CacheStrategy.WriteResult.DISABLED
        if (networkResponse.headers["Cache-Control"].hasDirective("no-store")) {
            freshness.forbid(networkRequest.url)
            return CacheStrategy.WriteResult.DISABLED
        }
        val result = http.write(cacheResponse, networkRequest, networkResponse, options)
        freshness.record(networkRequest.url, result.response?.responseMillis)
        return result
    }
}

private fun String?.hasDirective(name: String): Boolean =
    this?.split(',')?.any { it.trim().substringBefore('=').equals(name, ignoreCase = true) } == true

/** どの画面でも同じURLの期限・オフライン代替表示を使う。ローカル画像は対象外。 */
internal class ImageCacheInterceptor(private val freshness: ImageFreshness) : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): coil3.request.ImageResult {
        val request = chain.request
        val url = request.data.toString()
        if (!url.startsWith("https://") && !url.startsWith("http://")) return chain.proceed()
        // 応答の保存可否を知るまでは、Coilのデコード済みメモリへ書き込ませない。
        var result = if (!freshness.isFresh(url)) chain.withRequest(request.newBuilder()
            .memoryCachePolicy(CachePolicy.DISABLED).build()).proceed() else chain.proceed()
        if (result is SuccessResult && result.dataSource == DataSource.MEMORY_CACHE &&
            request.networkCachePolicy.readEnabled && !freshness.isFresh(url)) {
            result = chain.withRequest(request.newBuilder().memoryCachePolicy(CachePolicy.WRITE_ONLY).build()).proceed()
        }
        if (result is ErrorResult && request.networkCachePolicy.readEnabled && !freshness.isForbidden(url)) {
            val cached = chain.withRequest(request.newBuilder()
                .memoryCachePolicy(CachePolicy.READ_ONLY)
                .diskCachePolicy(CachePolicy.READ_ONLY)
                .networkCachePolicy(CachePolicy.DISABLED).build()).proceed()
            if (cached is SuccessResult) return cached
        }
        return result
    }
}

/** Ktorはonly-if-cachedだけでは通信を止めないため、保存画像の代替取得を明示的に遮断する。 */
internal class CacheOnlyImageNetworkClient(private val delegate: NetworkClient) : NetworkClient {
    override suspend fun <T> executeRequest(request: NetworkRequest, block: suspend (NetworkResponse) -> T): T =
        if (request.headers["Cache-Control"].hasDirective("only-if-cached")) block(NetworkResponse(code = 504))
        else delegate.executeRequest(request, block)
}
