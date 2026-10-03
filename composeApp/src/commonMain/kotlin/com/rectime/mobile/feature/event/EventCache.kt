package com.rectime.mobile.feature.event

import com.rectime.mobile.core.cache.CacheRequestGeneration
import com.rectime.mobile.core.cache.LocalCache
import com.rectime.mobile.core.network.EventDetailResponse
import com.rectime.mobile.feature.schedule.EventsResponse
import kotlinx.coroutines.CancellationException

/** 一覧と詳細でイベントの保存データを共有する。集合情報は別に管理する。 */
internal class EventCache(private val cache: LocalCache) {
    suspend fun loadEvents(): EventsResponse? = loadOrNull("schedule_events_v1")

    suspend fun saveEvents(events: EventsResponse) {
        cache.save("schedule_events_v1", events)
    }

    suspend fun loadDetail(eventId: Int): EventDetailResponse? {
        // 最新の一覧を優先し、スケジュールの更新を詳細のキャッシュ表示にも反映する。
        val event = loadEvents()?.events?.firstOrNull { it.eventId == eventId }
        return event?.let {
            EventDetailResponse(it.eventId, it.eventName, it.venues, it.startTime, it.endTime, it.ruleText)
        } ?: loadOrNull("event_detail_v1_$eventId")
    }

    suspend fun saveDetail(detail: EventDetailResponse) {
        val request = CacheRequestGeneration()
        cache.save("event_detail_v1_${detail.eventId}", detail)
        val events = loadEvents() ?: return
        if (!request.isCurrent || events.events.none { it.eventId == detail.eventId }) return
        // 詳細1件の応答で一覧全体を置き換えず、一覧に存在するイベントだけ更新する。
        cache.save("schedule_events_v1", events.copy(events = events.events.map {
            if (it.eventId != detail.eventId) it else it.copy(
                eventName = detail.eventName, venues = detail.venues,
                startTime = detail.startTime, endTime = detail.endTime, ruleText = detail.ruleText,
            )
        }))
    }

    private suspend inline fun <reified T> loadOrNull(key: String): T? = try {
        cache.load<T>(key)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }
}
