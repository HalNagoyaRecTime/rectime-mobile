package com.rectime.mobile.feature.event

import com.rectime.mobile.core.cache.CacheGeneration
import com.rectime.mobile.core.cache.CacheRequestGeneration
import com.rectime.mobile.core.cache.LocalCache
import com.rectime.mobile.core.cache.PlatformKeyValueStore
import com.rectime.mobile.core.network.EventDetailResponse
import com.rectime.mobile.feature.schedule.EventResponse
import com.rectime.mobile.feature.schedule.EventsResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

// 一覧と詳細は別のViewModel・LocalCacheから呼ばれるため、保存先ごとに更新順を共有する。
private val eventCacheMutex = Mutex()
private val eventCacheStates = mutableMapOf<Any, EventCacheState>()
private object PlatformEventCacheKey
private class EventCacheState(val generation: Int) {
    var revision = 0L
    val writes = mutableMapOf<Int, EventCacheWrite>()
}
private data class EventCacheWrite(val revision: Long, val detail: Boolean)

internal class EventCacheRequest internal constructor(
    internal val revision: Long,
    internal val generation: CacheRequestGeneration,
)

/** 一覧と詳細を新旧比較して共有する。集合情報は別に管理する。 */
@OptIn(ExperimentalTime::class)
internal class EventCache(private val cache: LocalCache) {
    private val key: Any = if (cache.store is PlatformKeyValueStore) PlatformEventCacheKey else cache.store

    private fun state(): EventCacheState = eventCacheStates.getOrPut(key) {
        EventCacheState(CacheGeneration.value)
    }.let {
        if (it.generation == CacheGeneration.value) it
        else EventCacheState(CacheGeneration.value).also { next -> eventCacheStates[key] = next }
    }

    /** 通信開始前に保存順を記録する。同じ秒に更新された応答も、後から開始した取得結果を巻き戻さない。 */
    suspend fun beginRequest(): EventCacheRequest = eventCacheMutex.withLock {
        EventCacheRequest(++state().revision, CacheRequestGeneration())
    }

    suspend fun loadEvents(): EventsResponse? = eventCacheMutex.withLock {
        val generation = CacheRequestGeneration()
        loadOrNull<EventsResponse>("schedule_events_v1").takeIf { generation.isCurrent }
    }

    suspend fun saveEvents(events: EventsResponse): EventsResponse = saveEvents(events, beginRequest())

    suspend fun saveEvents(events: EventsResponse, request: EventCacheRequest): EventsResponse =
        mergeEvents(events, request, persist = true)

    suspend fun reconcileEvents(events: EventsResponse, request: EventCacheRequest): EventsResponse =
        mergeEvents(events, request, persist = false)

    private suspend fun mergeEvents(events: EventsResponse, request: EventCacheRequest, persist: Boolean): EventsResponse =
        eventCacheMutex.withLock {
            if (!request.generation.isCurrent) return@withLock events
            val savedEvents = loadOrNull<EventsResponse>("schedule_events_v1")
            val merged = events.copy(events = events.events.map { incoming ->
                val saved = savedDetail(incoming.eventId, savedEvents)
                if (saved != null && keepSaved(saved.updatedAt, incoming.updatedAt, incoming.eventId, request)) {
                    incoming.withDetail(saved)
                } else incoming
            })
            if (persist && saveOrIgnore("schedule_events_v1", merged, request)) {
                merged.events.forEach { recordWrite(it.eventId, detail = false, request) }
            }
            // 保存に失敗しても、通信結果と統合した内容は画面へ返す。
            merged
        }

    suspend fun loadDetail(eventId: Int): EventDetailResponse? = eventCacheMutex.withLock {
        val generation = CacheRequestGeneration()
        savedDetail(eventId, loadOrNull("schedule_events_v1")).takeIf { generation.isCurrent }
    }

    suspend fun saveDetail(detail: EventDetailResponse): EventDetailResponse = saveDetail(detail, beginRequest())

    suspend fun saveDetail(detail: EventDetailResponse, request: EventCacheRequest): EventDetailResponse =
        mergeDetail(detail, request, persist = true)

    suspend fun reconcileDetail(detail: EventDetailResponse, request: EventCacheRequest): EventDetailResponse =
        mergeDetail(detail, request, persist = false)

    private suspend fun mergeDetail(detail: EventDetailResponse, request: EventCacheRequest, persist: Boolean): EventDetailResponse =
        eventCacheMutex.withLock {
            if (!request.generation.isCurrent) return@withLock detail
            val events = loadOrNull<EventsResponse>("schedule_events_v1")
            val saved = savedDetail(detail.eventId, events)
            val merged = if (saved != null && keepSaved(saved.updatedAt, detail.updatedAt, detail.eventId, request)) saved else detail
            val detailSaved = persist && saveOrIgnore("event_detail_v1_${detail.eventId}", merged, request)
            if (detailSaved) {
                recordWrite(detail.eventId, detail = true, request)
            }
            // 詳細1件から不完全な一覧は作らない。既存の他イベントも保持する。
            if (persist && events != null && events.events.any { it.eventId == detail.eventId }) {
                if (saveOrIgnore("schedule_events_v1", events.copy(events = events.events.map {
                    if (it.eventId == detail.eventId) it.withDetail(merged) else it
                }), request)) {
                    // 詳細の保存だけ失敗した場合は、更新できた一覧を優先する。
                    recordWrite(detail.eventId, detail = detailSaved, request)
                }
            }
            merged
        }

    private suspend fun savedDetail(eventId: Int, events: EventsResponse?): EventDetailResponse? {
        val listed = events?.events?.firstOrNull { it.eventId == eventId }?.toDetail()
        val detailed = loadOrNull<EventDetailResponse>("event_detail_v1_$eventId")
        if (listed == null) return detailed
        if (detailed == null) return listed
        val comparison = compareTime(detailed.updatedAt, listed.updatedAt)
        return when {
            comparison != null && comparison > 0 -> detailed
            comparison != null && comparison < 0 -> listed
            state().writes[eventId]?.detail == true -> detailed
            else -> listed
        }
    }

    private fun keepSaved(savedAt: String?, incomingAt: String?, eventId: Int, request: EventCacheRequest): Boolean {
        val comparison = compareTime(savedAt, incomingAt)
        if (comparison != null && comparison != 0) return comparison > 0
        // updated_atは秒単位で、旧キャッシュには存在しない場合もある。
        // 判別できないときは、後から開始した取得の内容を優先する。
        return (state().writes[eventId]?.revision ?: 0L) > request.revision
    }

    private fun recordWrite(eventId: Int, detail: Boolean, request: EventCacheRequest) {
        val state = state()
        state.writes[eventId] = EventCacheWrite(maxOf(state.writes[eventId]?.revision ?: 0L, request.revision), detail)
    }

    private fun compareTime(first: String?, second: String?): Int? {
        fun parse(value: String?): Instant? = value?.let {
            // SQLiteのUTC日時とISO 8601の両方を比較できるようにする。
            val normalized = if (it.matches(Regex("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"))) it.replace(' ', 'T') + "Z" else it
            runCatching { Instant.parse(normalized) }.getOrNull()
        }
        val a = parse(first) ?: return null
        val b = parse(second) ?: return null
        return a.compareTo(b)
    }

    private suspend inline fun <reified T> saveOrIgnore(key: String, value: T, request: EventCacheRequest): Boolean {
        if (!request.generation.isCurrent) return false
        return try {
            cache.save(key, value)
            request.generation.isCurrent
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private suspend inline fun <reified T> loadOrNull(key: String): T? = try {
        cache.load<T>(key)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }
}

private fun EventResponse.toDetail() = EventDetailResponse(
    eventId, eventName, venues, startTime, endTime, ruleText, updatedAt,
)
private fun EventResponse.withDetail(detail: EventDetailResponse) = copy(
    eventName = detail.eventName, venues = detail.venues, startTime = detail.startTime,
    endTime = detail.endTime, ruleText = detail.ruleText, updatedAt = detail.updatedAt ?: updatedAt,
)
