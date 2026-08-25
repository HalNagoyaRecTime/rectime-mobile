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
    fun failedResultIsReturnedWhenLiveFetchFailsAndLoadCacheItselfThrows() = runTest {
        // loadCache自体が例外を投げても、fetchWithCacheFallbackの「例外を投げない」
        // 契約を維持するため、キャッシュなしのFailedとして扱う。
        val liveError = IllegalStateException("network down")

        val result = fetchWithCacheFallback(
            fetchLive = { throw liveError },
            loadCache = { throw IllegalStateException("store read failed") },
            saveCache = { fail("saveCache should not be called on failure") },
        )

        val failed = assertIs<CachedFetchResult.Failed>(result)
        assertEquals(liveError, failed.error)
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

    @Test
    fun resultIsDiscardedAsFailedWhenClearAllRunsWhileFetchLiveIsInFlightAndSucceeds() = runTest {
        // ログアウト・新規ログイン等で、通信中にLocalCache.clearAll()が別画面から
        // 呼ばれた場合、この通信の結果(前ユーザー/前セッションのものである可能性が
        // ある)はキャッシュへ書き戻すだけでなく、呼び出し元(画面)へ返して
        // 表示させてもならない。書き込みだけ止めてもFreshとして返せば、
        // 呼び出し元の画面に前ユーザーのデータが表示されてしまうため。
        var saveCacheCalled = false

        val result = fetchWithCacheFallback(
            fetchLive = {
                CacheGeneration.bump()
                "live-value"
            },
            loadCache = { fail("loadCache should not be called in this scenario") },
            saveCache = { saveCacheCalled = true },
        )

        assertIs<CachedFetchResult.Failed>(result)
        assertFalse(saveCacheCalled)
    }

    @Test
    fun resultIsDiscardedAsFailedWhenClearAllRunsWhileFetchLiveIsInFlightAndFails() = runTest {
        // fetchLive失敗時のキャッシュフォールバック経路でも同様に、通信中に
        // clearAll()が走った場合はloadCache()の結果を呼び出し元へ返してはならない。
        val liveError = IllegalStateException("network down")

        val result = fetchWithCacheFallback(
            fetchLive = {
                CacheGeneration.bump()
                throw liveError
            },
            loadCache = { fail("loadCache should not be called in this scenario") },
            saveCache = { fail("saveCache should not be called on failure") },
        )

        assertIs<CachedFetchResult.Failed>(result)
    }

    @Test
    fun resultIsDiscardedAsFailedWhenClearAllRunsWhileLoadCacheIsInFlight() = runTest {
        // fetchLiveは開始時点の世代のまま失敗し、その後のloadCache()実行中に
        // clearAll()が走るケース。loadCache()自体は(まだ消去中/消去直前の)
        // 前セッションのデータを返してしまう可能性があるため、それを呼び出し元へ
        // 返してはならない。
        val liveError = IllegalStateException("network down")

        val result = fetchWithCacheFallback(
            fetchLive = { throw liveError },
            loadCache = {
                CacheGeneration.bump()
                "stale-cached-value"
            },
            saveCache = { fail("saveCache should not be called on failure") },
        )

        assertIs<CachedFetchResult.Failed>(result)
    }
}
