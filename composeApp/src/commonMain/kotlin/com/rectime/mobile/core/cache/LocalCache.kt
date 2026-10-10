package com.rectime.mobile.core.cache

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** APIの更新時刻とは別に、受理した取得結果の時刻を保存する。旧保存には時刻がない。 */
@PublishedApi
@Serializable
internal data class FetchedCache<T>(
    val data: T,
    @SerialName("fetched_at") val fetchedAt: String?,
)

/**
 * 保存済みJSONがアプリ更新後のスキーマ変更で読めなくなっても、
 * throwせずnullにフォールバックする(オフライン表示は"ベストエフォート"のため)。
 */
@OptIn(ExperimentalTime::class)
class LocalCache(
    @PublishedApi internal val store: KeyValueStore = PlatformKeyValueStore(),
    private val clock: Clock = Clock.System,
) {
    suspend inline fun <reified T> load(key: String): T? = loadEntry<T>(key)?.data

    @PublishedApi
    internal suspend inline fun <reified T> loadEntry(key: String): FetchedCache<T>? {
        val raw = store.getString(key) ?: return null
        return runCatching {
            val json = Json.parseToJsonElement(raw)
            if (json is JsonObject && "fetched_at" in json && "data" in json) {
                Json.decodeFromJsonElement<FetchedCache<T>>(json)
            } else {
                // 旧JSONは表示に使えるが、読み出した時刻を取得時刻にしない。
                FetchedCache(Json.decodeFromJsonElement<T>(json), fetchedAt = null)
            }
        }.getOrNull()
    }

    suspend inline fun <reified T> save(key: String, value: T) {
        store.putString(key, Json.encodeToString(value))
    }

    internal fun fetchedNow(): String = clock.now().toString()

    internal suspend inline fun <reified T> saveFetched(key: String, value: T) {
        saveEntry(key, value, fetchedNow())
    }

    internal suspend inline fun <reified T> saveEntry(key: String, value: T, fetchedAt: String?) {
        // 本文と時刻を1つの値で保存し、片方だけ新しくなる状態を避ける。
        store.putString(key, Json.encodeToString(FetchedCache(value, fetchedAt)))
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
