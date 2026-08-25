package com.rectime.mobile.core.cache

import kotlinx.coroutines.CancellationException

sealed class CachedFetchResult<out T> {
    data class Fresh<T>(val value: T) : CachedFetchResult<T>()

    // 元の例外を保持し、呼び出し側が401/404のような具体的な状態を
    // オフライン扱いに紛れさせず区別できるようにする。
    data class Cached<T>(val value: T, val error: Exception) : CachedFetchResult<T>()
    data class Failed(val error: Exception) : CachedFetchResult<Nothing>()
}

// ログアウト・新規ログイン等でこの通信中にキャッシュ世代が切り替わった場合に
// 呼び出し元へ返す例外。値そのもの(前セッションのものである可能性がある)を
// 呼び出し元(ViewModel)に渡してしまうと、書き込みだけ止めても画面に表示され
// てしまうため、この関数自体の結果としてFailed扱いにする。
private class StaleCacheGenerationException : Exception("キャッシュ世代が変化したため、この結果は破棄します")

suspend fun <T> fetchWithCacheFallback(
    fetchLive: suspend () -> T,
    loadCache: suspend () -> T?,
    saveCache: suspend (T) -> Unit,
): CachedFetchResult<T> {
    // fetchLive/loadCache実行中にログアウト・新規ログイン等でLocalCache.clearAll()が
    // 走った場合、この通信は前ユーザー(または前セッション)のものである可能性が
    // あるため、キャッシュへの書き込みだけでなく、呼び出し元への返却(画面表示)も
    // 行ってはならない(CacheGeneration参照)。
    val generationAtStart = CacheGeneration.value
    fun staleResultOrNull(): CachedFetchResult<T>? =
        if (generationAtStart != CacheGeneration.value) {
            CachedFetchResult.Failed(StaleCacheGenerationException())
        } else {
            null
        }

    val value = try {
        fetchLive()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        staleResultOrNull()?.let { return it }

        // キャッシュの読み込み自体が失敗した場合も、キャッシュなし(Failed)として
        // 扱う。ここで例外を伝播させると、この関数自体の「例外を投げない」契約が
        // 崩れてしまう。
        val cached = try {
            loadCache()
        } catch (cacheError: CancellationException) {
            throw cacheError
        } catch (cacheError: Exception) {
            null
        }

        staleResultOrNull()?.let { return it }

        return if (cached != null) CachedFetchResult.Cached(cached, e) else CachedFetchResult.Failed(e)
    }

    staleResultOrNull()?.let { return it }

    // キャッシュへの書き込み失敗はベストエフォートとして無視する。ここでの
    // 失敗をfetchLive()の失敗と同様に扱うと、通信自体は成功しているのに
    // 古いキャッシュへフォールバックしてしまう(オンラインなのに
    // 「オフライン」表示になる)ため。
    try {
        saveCache(value)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // no-op
    }

    return CachedFetchResult.Fresh(value)
}
