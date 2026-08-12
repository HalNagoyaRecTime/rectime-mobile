package com.rectime.mobile.core.cache

import kotlinx.coroutines.CancellationException

sealed class CachedFetchResult<out T> {
    data class Fresh<T>(val value: T) : CachedFetchResult<T>()

    // 元の例外を保持し、呼び出し側が401/404のような具体的な状態を
    // オフライン扱いに紛れさせず区別できるようにする。
    data class Cached<T>(val value: T, val error: Exception) : CachedFetchResult<T>()
    data class Failed(val error: Exception) : CachedFetchResult<Nothing>()
}

suspend fun <T> fetchWithCacheFallback(
    fetchLive: suspend () -> T,
    loadCache: suspend () -> T?,
    saveCache: suspend (T) -> Unit,
): CachedFetchResult<T> {
    val value = try {
        fetchLive()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        val cached = loadCache()
        return if (cached != null) CachedFetchResult.Cached(cached, e) else CachedFetchResult.Failed(e)
    }

    // キャッシュへの書き込み失敗はベストエフォートとして無視する。ここでの失敗を
    // fetchLive()の失敗と同様に扱うと、通信自体は成功しているのに古いキャッシュへ
    // フォールバックしてしまう(オンラインなのに「オフライン」表示になる)ため。
    try {
        saveCache(value)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // no-op
    }

    return CachedFetchResult.Fresh(value)
}
