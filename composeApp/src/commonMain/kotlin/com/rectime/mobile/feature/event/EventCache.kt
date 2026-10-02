package com.rectime.mobile.feature.event

import com.rectime.mobile.core.cache.CacheRequestGeneration
import com.rectime.mobile.core.cache.LocalCache
import com.rectime.mobile.core.network.EventDetailResponse
import com.rectime.mobile.feature.schedule.EventsResponse
import kotlinx.coroutines.CancellationException

/** List and detail share the same saved event fields; gathering data remains separate. */
internal class EventCache(private val cache: LocalCache) {
    suspend fun loadEvents(): EventsResponse? = loadOrNull("schedule_events_v1")

    suspend fun saveEvents(events: EventsResponse) {
        cache.save("schedule_events_v1", events)
    }

    suspend fun loadDetail(eventId: Int): EventDetailResponse? {
        // Prefer the latest full list, so a schedule update also updates detail previews.
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
        // Update only existing list entries; a single detail response is never a complete feed.
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
