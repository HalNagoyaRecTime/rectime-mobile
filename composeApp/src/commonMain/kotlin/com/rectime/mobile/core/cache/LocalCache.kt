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
        store.clear()
    }
}
