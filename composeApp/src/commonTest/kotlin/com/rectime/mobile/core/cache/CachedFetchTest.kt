package com.rectime.mobile.core.cache

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.fail

class CachedFetchTest {
    @Test
    fun freshResultIsReturnedAndSavedToCacheOnSuccess() = runTest {
        var saved: String? = null

        val result = fetchWithCacheFallback(
            fetchLive = { "live-value" },
            loadCache = { fail("loadCache should not be called on success") },
            saveCache = { saved = it },
        )

        assertEquals(CachedFetchResult.Fresh("live-value"), result)
        assertEquals("live-value", saved)
    }

    @Test
    fun cachedResultIsReturnedWhenLiveFetchFailsAndCacheHasAValue() = runTest {
        val liveError = IllegalStateException("network down")

        val result = fetchWithCacheFallback(
            fetchLive = { throw liveError },
            loadCache = { "cached-value" },
            saveCache = { fail("saveCache should not be called on failure") },
        )

        val cached = assertIs<CachedFetchResult.Cached<String>>(result)
        assertEquals("cached-value", cached.value)
        assertEquals(liveError, cached.error)
    }

    @Test
    fun failedResultIsReturnedWhenLiveFetchFailsAndCacheIsEmpty() = runTest {
        val liveError = IllegalStateException("network down")

        val result = fetchWithCacheFallback(
            fetchLive = { throw liveError },
            loadCache = { null },
            saveCache = { fail("saveCache should not be called on failure") },
        )

        val failed = assertIs<CachedFetchResult.Failed>(result)
        assertEquals(liveError, failed.error)
    }

    @Test
    fun freshResultIsReturnedEvenWhenSaveCacheFails() = runTest {
        // ライブ取得自体は成功しているので、キャッシュへの書き込み失敗を理由に
        // 古いキャッシュへフォールバックしてはならない(オンラインなのに
        // 「オフライン」表示になってしまうため)。
        val result = fetchWithCacheFallback(
            fetchLive = { "live-value" },
            loadCache = { fail("loadCache should not be called when fetchLive succeeds") },
            saveCache = { throw IllegalStateException("disk full") },
        )

        assertEquals(CachedFetchResult.Fresh("live-value"), result)
    }

    @Test
    fun cancellationExceptionPropagatesWithoutTouchingCache() = runTest {
        var loadCacheCalled = false
        var saveCacheCalled = false

        try {
            fetchWithCacheFallback(
                fetchLive = { throw CancellationException("cancelled") },
                loadCache = { loadCacheCalled = true; null },
                saveCache = { saveCacheCalled = true },
            )
            fail("Expected CancellationException to propagate")
        } catch (e: CancellationException) {
            // expected
        }

        assertFalse(loadCacheCalled)
        assertFalse(saveCacheCalled)
    }
}
