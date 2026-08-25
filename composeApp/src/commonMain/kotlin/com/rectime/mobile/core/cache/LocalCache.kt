package com.rectime.mobile.core.cache

import kotlinx.serialization.json.Json

/**
 * 保存済みJSONがアプリ更新後のスキーマ変更で読めなくなっても、
 * throwせずnullにフォールバックする(オフライン表示は"ベストエフォート"のため)。
 */
class LocalCache(@PublishedApi internal val store: KeyValueStore = PlatformKeyValueStore()) {
    suspend inline fun <reified T> load(key: String): T? {
        val raw = store.getString(key) ?: return null
        return runCatching { Json.decodeFromString<T>(raw) }.getOrNull()
    }

    suspend inline fun <reified T> save(key: String, value: T) {
        store.putString(key, Json.encodeToString(value))
    }

    // ログアウト・セッション失効時に他ユーザーへキャッシュが漏れないよう全消去する。
    suspend fun clearAll() {
        // store.clear()より先にbump()する。万が一store.clear()が中断点を挟んで
        // 他画面のfetchWithCacheFallbackにスケジューリングが移った場合でも、
        // 世代が既に変わっていることを検知できるようにするため
        // (先にclear()してからbump()すると、その中断の間だけ検知できない
        // 窓が生まれてしまう)。
        CacheGeneration.bump()
        store.clear()
    }
}
