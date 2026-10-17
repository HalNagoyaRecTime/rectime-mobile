package com.rectime.mobile.core.cache

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 同じ保存先への同時取得を共有する。1画面の破棄で他画面の取得まで取り消さない。 */
internal class CacheRequestPool {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutex = Mutex()
    private val requests = mutableMapOf<String, Deferred<Any>>()
    private val waiters = mutableMapOf<Deferred<Any>, Int>()

    @Suppress("UNCHECKED_CAST")
    suspend fun <T : Any> run(key: String, fetch: suspend () -> T): T {
        val request = CacheRequestGeneration()
        val pending = mutex.withLock {
            val task = requests[key]?.takeUnless { it.isCompleted }
                ?: scope.async(start = CoroutineStart.LAZY) {
                    if (!request.isCurrent) throw CancellationException("キャッシュのセッションが変わりました")
                    fetch()
                }.also { requests[key] = it }
            waiters[task] = (waiters[task] ?: 0) + 1
            task
        }
        try {
            val value = pending.await()
            if (!request.isCurrent) throw CancellationException("キャッシュのセッションが変わりました")
            return value as T
        } finally {
            withContext(NonCancellable) {
                mutex.withLock {
                    val remaining = (waiters[pending] ?: 1) - 1
                    if (remaining == 0) {
                        waiters.remove(pending)
                        if (!pending.isCompleted) pending.cancel()
                        if (requests[key] === pending) requests.remove(key)
                    } else waiters[pending] = remaining
                }
            }
        }
    }

    suspend fun awaitCurrent(key: String) {
        val pending = mutex.withLock { requests[key] }
        try { pending?.await() } catch (e: CancellationException) {
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
        } catch (_: Exception) { }
    }

    /** 画面所有のクライアントも、別画面が共有中の通信を終えてから破棄する。 */
    fun afterRequests(close: () -> Unit) {
        val pending = requests.values.toList()
        if (pending.isEmpty()) close()
        else scope.launch {
            pending.forEach { task ->
                try { task.await() }
                catch (e: CancellationException) { kotlinx.coroutines.currentCoroutineContext().ensureActive() }
                catch (_: Exception) { }
            }
            close()
        }
    }

    fun cancelAll() {
        requests.values.forEach { it.cancel() }
        requests.clear()
        waiters.clear()
    }
}
