package com.rectime.mobile.core.cache

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
        val raw = readRaw(key) ?: return null
        return decodeEntry<T>(raw)
    }

    suspend inline fun <reified T> save(key: String, value: T) {
        writeRaw(key, Json.encodeToString(value))
    }

    internal fun fetchedNow(): String = clock.now().toString()

    internal suspend inline fun <reified T> saveFetched(key: String, value: T) {
        saveEntry(key, value, fetchedNow())
    }

    internal suspend inline fun <reified T> saveEntry(key: String, value: T, fetchedAt: String?) {
        // 本文と時刻を1つの値で保存し、片方だけ新しくなる状態を避ける。
        writeRaw(key, Json.encodeToString(FetchedCache(value, fetchedAt)))
    }

    /** 通信は呼び出し側が決め、表示側は同じ保存先の変更を購読する。 */
    internal inline fun <reified T> observe(key: String): Flow<T?> = flow {
        try { load<T>(key) } catch (e: CancellationException) { throw e } catch (_: Exception) { }
        emitAll(rawState(key).map { value ->
            value.raw?.takeIf { value.generation == CacheGeneration.value }?.let { decodeEntry<T>(it)?.data }
        })
    }

    @PublishedApi
    internal fun rawState(key: String): MutableStateFlow<RawCacheValue> = memory().entry(key).raw

    @PublishedApi
    internal suspend fun readRaw(key: String): String? {
        val request = CacheRequestGeneration()
        val memory = memory()
        return memory.mutex.withLock {
            val entry = memory.entry(key)
            if (!entry.loaded) {
                val saved = if (memory.ignoreSaved) null else store.getString(key)
                if (!request.isCurrent) return@withLock null
                entry.raw.value = RawCacheValue(saved, CacheGeneration.value)
                entry.loaded = true
            }
            entry.raw.value.raw.takeIf { request.isCurrent }
        }
    }

    @PublishedApi
    internal suspend fun writeRaw(key: String, raw: String, persist: Boolean = true) {
        currentCoroutineContext().ensureActive()
        val request = CacheRequestGeneration()
        val memory = memory()
        memory.mutex.withLock {
            if (!request.isCurrent) return@withLock
            val entry = memory.entry(key)
            // 永続化に失敗しても、今回取得した値を全画面の表示・次のフォールバックに使う。
            entry.loaded = true
            entry.raw.value = RawCacheValue(raw, CacheGeneration.value)
            if (persist) {
                memory.transientKeys.remove(key)
                store.putString(key, raw)
            } else {
                memory.transientKeys.remove(key)
                memory.transientKeys.add(key)
                while (memory.transientKeys.size > MemoryOnlyCacheLimit) {
                    val oldest = memory.transientKeys.first()
                    memory.transientKeys.remove(oldest)
                    // Flowは残し、購読中の画面が以後の更新を受け取れるようにする。
                    memory.entries[oldest]?.raw?.value = RawCacheValue(null, CacheGeneration.value)
                }
            }
        }
    }

    /** 詳細を共有するが、履歴の保存上限を超えて永続化しない。 */
    internal suspend inline fun <reified T> saveMemory(key: String, value: T) {
        writeRaw(key, Json.encodeToString(FetchedCache(value, fetchedNow())), persist = false)
    }

    internal fun hasMemoryEntry(key: String): Boolean = key in memory().entries
    internal suspend fun invalidateMemory(key: String) = writeRaw(key, "null", persist = false)

    /** 削除・閲覧拒否が確定した値を次回の復元にも使わない。 */
    internal suspend fun invalidate(key: String) = writeRaw(key, "null")

    internal val requests: CacheRequestPool get() = memory().requests

    private fun memory(): CacheMemory = cacheMemories.getOrPut(
        if (store is PlatformKeyValueStore) PlatformCacheMemoryKey else store,
    ) { CacheMemory() }

    suspend fun clearAll() {
        val memory = memory()
        CacheGeneration.bump()
        try {
            com.rectime.mobile.core.attachment.AttachmentCaches.clearAll()
        } finally {
            // 添付の消去失敗でも、APIデータの消去は試みる。
            memory.mutex.withLock {
                store.clear()
                memory.ignoreSaved = false
            }
        }
    }
}

internal fun clearCacheMemory() {
    cacheMemories.values.forEach { memory ->
        memory.requests.cancelAll()
        memory.transientKeys.clear()
        memory.ignoreSaved = true
        memory.entries.values.forEach { entry ->
            entry.loaded = true
            entry.raw.value = RawCacheValue(null, CacheGeneration.value)
        }
    }
}

private const val MemoryOnlyCacheLimit = 100
private object PlatformCacheMemoryKey
private val cacheMemories = mutableMapOf<Any, CacheMemory>()
private class CacheMemory {
    var ignoreSaved = false
    val transientKeys = linkedSetOf<String>()
    val requests = CacheRequestPool()
    val mutex = Mutex()
    val entries = mutableMapOf<String, CacheEntry>()
    fun entry(key: String): CacheEntry = entries.getOrPut(key) { CacheEntry() }
}
@PublishedApi
internal data class RawCacheValue(val raw: String?, val generation: Int)

private class CacheEntry {
    var loaded = false
    val raw = MutableStateFlow(RawCacheValue(null, CacheGeneration.value))
}

@PublishedApi
internal inline fun <reified T> decodeEntry(raw: String): FetchedCache<T>? = runCatching {
    val json = Json.parseToJsonElement(raw)
    if (json is JsonObject && "fetched_at" in json && "data" in json) {
        Json.decodeFromJsonElement<FetchedCache<T>>(json)
    } else FetchedCache(Json.decodeFromJsonElement<T>(json), fetchedAt = null)
}.getOrNull()
