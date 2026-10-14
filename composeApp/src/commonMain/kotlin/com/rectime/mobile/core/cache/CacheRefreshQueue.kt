package com.rectime.mobile.core.cache

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 初回表示の重複は進行中の取得に合流する。明示更新・前面復帰は完了後にもう一度取得する。
 * 待機中の要求は1件にまとめ、画面破棄・セッション変更後には実行しない。
 * Main.immediate上で、画面固有の取得時機と取得本体を渡して使う。
 */
internal class CacheRefreshQueue {
    private var job: Job? = null
    private var started = false
    private var session = CacheRequestGeneration()
    private var pending: (suspend CoroutineScope.() -> Unit)? = null

    fun launch(
        scope: CoroutineScope,
        afterCurrent: Boolean,
        block: suspend CoroutineScope.() -> Unit,
    ): Job {
        if (!session.isCurrent) {
            job?.cancel()
            pending = null
            job = null
            session = CacheRequestGeneration()
        }
        job?.takeIf { it.isActive }?.let {
            if (afterCurrent && started) pending = block
            return it
        }
        val request = CacheRequestGeneration()
        started = false
        val next = scope.launch(start = CoroutineStart.LAZY) {
            started = true
            var action: (suspend CoroutineScope.() -> Unit)? = block
            try {
                while (request.isCurrent && action != null) {
                    action.invoke(this)
                    action = pending
                    pending = null
                }
            } finally {
                // キャンセルされた旧セッションのfinallyが、新しい要求を消さない。
                if (session === request) {
                    pending = null
                    started = false
                }
            }
        }
        session = request
        job = next
        next.start()
        return next
    }
}
