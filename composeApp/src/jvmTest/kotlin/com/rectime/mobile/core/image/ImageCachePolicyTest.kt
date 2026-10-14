package com.rectime.mobile.core.image

import coil3.Image
import coil3.decode.DataSource
import coil3.intercept.Interceptor
import coil3.request.ImageRequest
import coil3.request.ImageResult
import coil3.request.SuccessResult
import coil3.request.ErrorResult
import coil3.size.Size
import coil3.PlatformContext
import coil3.annotation.ExperimentalCoilApi
import coil3.network.NetworkClient
import coil3.network.NetworkHeaders
import coil3.network.NetworkRequest
import coil3.network.NetworkResponse
import coil3.request.CachePolicy
import coil3.request.Options
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoilApi::class)
class ImageCachePolicyTest {
    private val url = "https://images.example.test/venue.png"
    private val fetchedAt = 1_700_000_000_000L
    private val options = Options(PlatformContext.INSTANCE)
    private fun headers(vararg values: Pair<String, String>) = NetworkHeaders.Builder().apply {
        values.forEach { (name, value) -> set(name, value) }
    }.build()

    @Test fun cacheWithoutServerHeadersIsSharedForExactlyTwelveHours() = runTest {
        var now = fetchedAt
        val freshness = ImageFreshness { now }
        val strategy = ImageCacheStrategy(freshness)
        val cached = NetworkResponse(responseMillis = fetchedAt)
        val request = NetworkRequest(url)
        now += IMAGE_CACHE_FRESH_MILLIS - 1
        assertSame(cached, strategy.read(cached, request, options).response)
        assertTrue(freshness.isFresh(url))
        now++
        assertNull(strategy.read(cached, request, options).response)
        assertFalse(freshness.isFresh(url))
    }

    @Test fun expiredImagesUseEtagEvenWithLongServerMaxAge() = runTest {
        val strategy = ImageCacheStrategy(ImageFreshness { fetchedAt + IMAGE_CACHE_FRESH_MILLIS })
        val cached = NetworkResponse(responseMillis = fetchedAt,
            headers = headers("Cache-Control" to "max-age=31536000", "ETag" to "venue-v1"))
        val result = strategy.read(cached, NetworkRequest(url), options)
        assertEquals("venue-v1", result.request?.headers?.get("If-None-Match"))
        assertNull(result.response)
    }

    @Test fun notModifiedRenewsReceiptTimeWithoutReplacingBody() = runTest {
        val freshness = ImageFreshness { fetchedAt + IMAGE_CACHE_FRESH_MILLIS }
        val result = ImageCacheStrategy(freshness).write(NetworkResponse(responseMillis = fetchedAt),
            NetworkRequest(url), NetworkResponse(code = 304, responseMillis = fetchedAt + IMAGE_CACHE_FRESH_MILLIS), options)
        assertEquals(fetchedAt + IMAGE_CACHE_FRESH_MILLIS, result.response?.responseMillis)
        assertNull(result.response?.body)
        assertTrue(freshness.isFresh(url))
    }

    @Test fun noStoreAndFailedResponsesDoNotBecomeFresh() = runTest {
        val freshness = ImageFreshness { fetchedAt }
        val strategy = ImageCacheStrategy(freshness)
        val forbidden = NetworkResponse(responseMillis = fetchedAt, headers = headers("Cache-Control" to "NO-STORE"))
        assertNull(strategy.write(null, NetworkRequest(url), forbidden, options).response)
        assertFalse(freshness.isFresh(url))
        assertTrue(freshness.isForbidden(url))
        assertNull(strategy.read(forbidden, NetworkRequest(url), options).response)
        assertFalse(freshness.isFresh(url))
        assertNull(strategy.write(null, NetworkRequest(url), NetworkResponse(code = 500), options).response)
        assertFalse(freshness.isFresh(url))
    }

    @Test fun offlineRequestCanReadExpiredImageWithoutExtendingFreshness() = runTest {
        val freshness = ImageFreshness { fetchedAt + IMAGE_CACHE_FRESH_MILLIS }
        val cached = NetworkResponse(responseMillis = fetchedAt)
        val result = ImageCacheStrategy(freshness).read(cached, NetworkRequest(url), options.copy(networkCachePolicy = CachePolicy.DISABLED))
        assertSame(cached, result.response)
        assertFalse(freshness.isFresh(url))
    }

    @Test fun unknownTimestampsClockRollbackAndDifferentUrlsAreNotFresh() = runTest {
        val freshness = ImageFreshness { fetchedAt }
        assertFalse(freshness.isFresh(0))
        assertFalse(freshness.isFresh(fetchedAt + 1))
        freshness.record(url, fetchedAt)
        assertTrue(freshness.isFresh(url))
        assertFalse(freshness.isFresh("$url?changed=1"))
    }
    @Test fun staleMemoryCannotBypassDiskExpiryAndNetworkFailureKeepsSavedImage() = runTest {
        val requests = mutableListOf<ImageRequest>()
        val original = ImageRequest.Builder(PlatformContext.INSTANCE).data(url).build()
        val image = object : Image {
            override val size = 4L
            override val width = 1
            override val height = 1
            override val shareable = true
            override fun draw(canvas: org.jetbrains.skia.Canvas) = Unit
        }
        fun chain(request: ImageRequest): Interceptor.Chain = object : Interceptor.Chain {
            override val request = request
            override val size = Size.ORIGINAL
            override fun withRequest(request: ImageRequest) = chain(request)
            override fun withSize(size: Size) = this
            override suspend fun proceed(): ImageResult {
                requests += request
                return if (request.memoryCachePolicy.readEnabled) SuccessResult(image, request, DataSource.MEMORY_CACHE)
                else ErrorResult(image = null, request = request, throwable = java.io.IOException("offline"))
            }
        }
        val freshness = ImageFreshness { fetchedAt + IMAGE_CACHE_FRESH_MILLIS }
        freshness.record(url, fetchedAt)
        val result = ImageCacheInterceptor(freshness).intercept(chain(original))
        assertTrue(result is SuccessResult)
        assertEquals(2, requests.size)
        assertEquals(CachePolicy.DISABLED, requests[0].memoryCachePolicy)
        assertEquals(CachePolicy.DISABLED, requests[1].networkCachePolicy)
        assertFalse(freshness.isFresh(url))
    }

    @Test fun freshMemoryDoesNotRetryTheRequest() = runTest {
        val requests = mutableListOf<ImageRequest>()
        val request = ImageRequest.Builder(PlatformContext.INSTANCE).data(url).build()
        val image = object : Image {
            override val size = 4L
            override val width = 1
            override val height = 1
            override val shareable = true
            override fun draw(canvas: org.jetbrains.skia.Canvas) = Unit
        }
        val chain = object : Interceptor.Chain {
            override val request = request
            override val size = Size.ORIGINAL
            override fun withRequest(request: ImageRequest): Interceptor.Chain = error("Fresh memory should not retry")
            override fun withSize(size: Size) = this
            override suspend fun proceed(): ImageResult {
                requests += request
                return SuccessResult(image, request, DataSource.MEMORY_CACHE)
            }
        }
        val freshness = ImageFreshness { fetchedAt + IMAGE_CACHE_FRESH_MILLIS - 1 }
        freshness.record(url, fetchedAt)
        assertTrue(ImageCacheInterceptor(freshness).intercept(chain) is SuccessResult)
        assertEquals(1, requests.size)
    }

    @Test fun noStorePreventsMemoryWritesAndOfflineFallback() = runTest {
        val freshness = ImageFreshness { fetchedAt }
        ImageCacheStrategy(freshness).write(null, NetworkRequest(url), NetworkResponse(
            responseMillis = fetchedAt, headers = headers("Cache-Control" to "no-store")), options)
        val requests = mutableListOf<ImageRequest>()
        fun chain(request: ImageRequest): Interceptor.Chain = object : Interceptor.Chain {
            override val request = request
            override val size = Size.ORIGINAL
            override fun withRequest(request: ImageRequest) = chain(request)
            override fun withSize(size: Size) = this
            override suspend fun proceed(): ImageResult {
                requests += request
                return ErrorResult(null, request, java.io.IOException("offline"))
            }
        }
        val request = ImageRequest.Builder(PlatformContext.INSTANCE).data(url).build()
        assertTrue(ImageCacheInterceptor(freshness).intercept(chain(request)) is ErrorResult)
        assertEquals(1, requests.size)
        assertFalse(requests.single().memoryCachePolicy.writeEnabled)
    }

    @Test fun cacheOnlyMissNeverStartsAnotherNetworkRequest() = runTest {
        var calls = 0
        val delegate = object : NetworkClient {
            override suspend fun <T> executeRequest(request: NetworkRequest, block: suspend (NetworkResponse) -> T): T {
                calls++
                return block(NetworkResponse(code = 200))
            }
        }
        val client = CacheOnlyImageNetworkClient(delegate)
        val cacheOnly = NetworkRequest(url, headers = headers("Cache-Control" to "only-if-cached, max-stale=2147483647"))
        assertEquals(504, client.executeRequest(cacheOnly) { it.code })
        assertEquals(0, calls)
        assertEquals(200, client.executeRequest(NetworkRequest(url)) { it.code })
        assertEquals(1, calls)
    }

}
