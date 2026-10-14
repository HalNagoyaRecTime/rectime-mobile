package com.rectime.mobile.feature.event

import com.rectime.mobile.core.cache.CacheGeneration
import com.rectime.mobile.core.cache.CacheRequestGeneration
import com.rectime.mobile.core.cache.LocalCache
import com.rectime.mobile.core.cache.FetchedCache
import com.rectime.mobile.core.cache.PlatformKeyValueStore
import com.rectime.mobile.core.network.HttpStatusException
import io.ktor.http.HttpStatusCode
import com.rectime.mobile.core.network.EventDetailResponse
import com.rectime.mobile.feature.schedule.EventResponse
import com.rectime.mobile.feature.schedule.EventsResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// 一覧と詳細は別のViewModel・LocalCacheから呼ばれるため、保存先ごとに更新順を共有する。
private const val InvalidatedEventsKey = "event_invalidations_v1"
private val eventCacheMutex = Mutex()
private val eventCacheStates = mutableMapOf<Any, EventCacheState>()
private object PlatformEventCacheKey
private class EventCacheState(val generation: Int) {
    var revision = 0L
    var listRevision = 0L
    val invalidations = mutableMapOf<Int, Pair<Long, Exception>>()
    val writes = mutableMapOf<Int, EventCacheWrite>()
    val roundRevisions = mutableMapOf<Int, Long>()
}
private data class EventCacheWrite(val revision: Long, val detail: Boolean)

internal class EventCacheRequest internal constructor(
    internal val revision: Long,
    internal val generation: CacheRequestGeneration,
)

/** 一覧と詳細を新旧比較して共有する。詳細のラウンド情報は一覧の更新でも保持する。 */
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

    suspend fun invalidateDetail(eventId: Int, error: Exception = HttpStatusException(HttpStatusCode.NotFound)) = eventCacheMutex.withLock {
        // 先に拒否・削除の記録を保存する。一覧と詳細の片方だけ保存できても復元しない。
        val invalidated = loadInvalidatedIds() + eventId
        cache.save(InvalidatedEventsKey, invalidated)
        val revision = ++state().revision
        state().invalidations[eventId] = revision to error
        state().listRevision = revision
        try { cache.invalidate("event_detail_v1_$eventId") }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { e.printStackTrace() }
        val saved = loadEntryOrNull<EventsResponse>("schedule_events_v1") ?: return@withLock
        val remaining = saved.data.events.filterNot { it.eventId == eventId }
        saveOrIgnore("schedule_events_v1", saved.data.copy(events = remaining, total = remaining.size),
            EventCacheRequest(revision, CacheRequestGeneration()), saved.fetchedAt)
    }

    suspend fun loadEvents(): EventsResponse? = loadEventsEntry()?.data

    suspend fun loadEventsEntry(): FetchedCache<EventsResponse>? = eventCacheMutex.withLock {
        val generation = CacheRequestGeneration()
        val saved = loadEntryOrNull<EventsResponse>("schedule_events_v1")
        val ids = loadInvalidatedIds()
        saved?.copy(data = saved.data.withoutEvents(ids)).takeIf { generation.isCurrent }
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
            if (savedEvents != null && state().listRevision > request.revision) return@withLock savedEvents.withoutEvents(loadInvalidatedIds())
            val invalidated = loadInvalidatedIds()
            val available = events.events.filterNot { it.eventId in invalidated || (state().invalidations[it.eventId]?.first ?: 0L) > request.revision }
            val merged = events.copy(total = available.size, events = available.map { incoming ->
                val saved = savedDetail(incoming.eventId, savedEvents)?.data
                if (saved != null && keepSaved(incoming.eventId, request)) {
                    incoming.withDetail(saved)
                } else incoming
            })
            if (persist && saveOrIgnore("schedule_events_v1", merged, request, cache.fetchedNow())) {
                state().listRevision = request.revision
                merged.events.forEach { recordWrite(it.eventId, detail = false, request) }
            }
            // 保存に失敗しても、通信結果と統合した内容は画面へ返す。
            merged
        }

    suspend fun loadDetail(eventId: Int): EventDetailResponse? = loadDetailEntry(eventId)?.data

    suspend fun loadDetailEntry(eventId: Int): FetchedCache<EventDetailResponse>? = eventCacheMutex.withLock {
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
            state().invalidations[detail.eventId]?.takeIf { it.first > request.revision }?.let { throw it.second }
            val savedEvents = loadEntryOrNull<EventsResponse>("schedule_events_v1")
            val events = savedEvents?.data
            val savedEntry = savedDetail(detail.eventId, events)
            val saved = savedEntry?.data
            val retained = saved?.takeIf { keepSaved(detail.eventId, request) }
            // ラウンドは詳細APIだけが返すため、一覧やEvent本体の更新時刻と比較しない。
            val keepRounds = detail.rounds == null ||
                (state().roundRevisions[detail.eventId] ?: 0L) > request.revision
            val merged = (retained ?: detail).copy(
                rounds = if (keepRounds) saved?.rounds else detail.rounds,
            )
            val detailKey = "event_detail_v1_${detail.eventId}"
            val detailSaved = if (persist) {
                val fetchedAt = if (keepRounds && saved?.rounds != null) {
                    savedEntry.fetchedAt
                } else cache.fetchedNow()
                saveOrIgnore(detailKey, merged, request, fetchedAt)
            } else false
            if (detailSaved) {
                recordWrite(detail.eventId, detail = true, request)
                if (!keepRounds) state().roundRevisions[detail.eventId] = request.revision
            }
            // 詳細1件から不完全な一覧は作らない。既存の他イベントも保持する。
            if (persist && events != null && events.events.any { it.eventId == detail.eventId }) {
                if (saveOrIgnore("schedule_events_v1", events.copy(events = events.events.map {
                    if (it.eventId == detail.eventId) it.withDetail(merged) else it
                }), request, savedEvents.fetchedAt)) {
                    // 詳細の保存だけ失敗した場合は、更新できた一覧を優先する。
                    recordWrite(detail.eventId, detail = detailSaved, request)
                }
            }
            if (persist && detailSaved) {
                val ids = loadInvalidatedIds()
                if (detail.eventId in ids) cache.save(InvalidatedEventsKey, ids - detail.eventId)
            }
            merged
        }

    private suspend fun savedDetail(eventId: Int, events: EventsResponse?): FetchedCache<EventDetailResponse>? {
        if (eventId in loadInvalidatedIds()) return null
        val listEntry = events?.events?.firstOrNull { it.eventId == eventId }?.toDetail()
        val saved = loadEntryOrNull<EventDetailResponse>("event_detail_v1_$eventId")
        val detailed = saved?.data
        val listed = listEntry?.copy(rounds = detailed?.rounds)
        val value = when {
            listed == null -> detailed
            detailed == null -> listed
            else -> {
                if (state().writes[eventId]?.detail == true) detailed else listed
            }
        } ?: return null
        // 一覧から本文を補っても、詳細APIの取得時刻を一覧の時刻で代用しない。
        return FetchedCache(value, saved?.fetchedAt)
    }

    private suspend fun loadInvalidatedIds(): Set<Int> = cache.load<Set<Int>>(InvalidatedEventsKey).orEmpty()

    // updated_atはDB復元や集合だけの変更で単調増加しない。取得順だけで競合を防ぐ。
    private fun keepSaved(eventId: Int, request: EventCacheRequest): Boolean =
        (state().writes[eventId]?.revision ?: 0L) > request.revision

    private fun recordWrite(eventId: Int, detail: Boolean, request: EventCacheRequest) {
        val state = state()
        state.writes[eventId] = EventCacheWrite(maxOf(state.writes[eventId]?.revision ?: 0L, request.revision), detail)
    }

    private suspend inline fun <reified T> saveOrIgnore(key: String, value: T, request: EventCacheRequest, fetchedAt: String?): Boolean {
        if (!request.generation.isCurrent) return false
        return try {
            cache.saveEntry(key, value, fetchedAt)
            request.generation.isCurrent
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
            request.generation.isCurrent
        }
    }

    private suspend inline fun <reified T> loadEntryOrNull(key: String): FetchedCache<T>? = try {
        cache.loadEntry<T>(key)
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
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

private fun EventsResponse.withoutEvents(ids: Set<Int>): EventsResponse {
    val remaining = events.filterNot { it.eventId in ids }
    return copy(events = remaining, total = remaining.size)
}
