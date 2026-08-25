package com.rectime.mobile.core.cache

// 全画面のLocalCacheインスタンスをまたいで共有する世代カウンタ。
//
// LocalCache.clearAll()(ログアウト・新規ログイン時)実行中に、別画面の
// fetchWithCacheFallbackが既に通信を終えていて、そのsaveCacheがclearAll()の
// 直後に走ってしまうと、消去したはずの(前ユーザーの可能性がある)データが
// キャッシュへ書き戻されてしまう。fetchWithCacheFallbackがfetchLive開始時点の
// 世代を覚えておき、saveCache直前に世代が変わっていないか確認することで、
// このすれ違いを防ぐ。
//
// viewModelScopeの既定ディスパッチャ(Dispatchers.Main.immediate)はシングル
// スレッドのcooperativeスケジューリングのため、suspendの中断点以外で複数の
// コルーチンが同時にこの値へアクセスすることはない。そのためロック無しの
// プレーンなvarで安全。
internal object CacheGeneration {
    var value: Int = 0
        private set

    fun bump() {
        value++
    }
}
