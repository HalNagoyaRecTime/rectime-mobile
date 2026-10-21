package com.rectime.mobile.feature.event

import com.rectime.mobile.core.cache.LocalCache
import com.rectime.mobile.feature.schedule.EventsResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.*
import kotlin.time.Clock
import kotlin.time.Instant

internal val EventTimeZone = TimeZone.of("Asia/Tokyo")
internal enum class EventDay { Unknown, Before, Today, After }
internal enum class EventStatus(val label: String?) { Unknown(null), Before("開催前"), Running("開催中"), Ended("終了") }
internal data class EventTimeContext(val day: EventDay, val minute: Int) {
    fun status(start: String, end: String): EventStatus {
        val first = minuteOf(start) ?: return EventStatus.Unknown
        val last = minuteOf(end) ?: return EventStatus.Unknown
        if (last <= first || day == EventDay.Unknown) return EventStatus.Unknown
        return when {
            day == EventDay.Before || minute < first -> EventStatus.Before
            day == EventDay.After || minute >= last -> EventStatus.Ended
            else -> EventStatus.Running
        }
    }
}
internal fun eventTimeContext(date: String?, now: Instant): EventTimeContext {
    val day = date?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        ?: return EventTimeContext(EventDay.Unknown, -1)
    val current = now.toLocalDateTime(EventTimeZone)
    return when {
        current.date < day -> EventTimeContext(EventDay.Before, -1)
        current.date > day -> EventTimeContext(EventDay.After, 1441)
        else -> EventTimeContext(EventDay.Today, current.hour * 60 + current.minute)
    }
}
internal fun CoroutineScope.eventTimeStateFlow(cache: LocalCache, clock: Clock = Clock.System) =
    combine(cache.observe<EventsResponse>("schedule_events_v1"), flow {
        while (true) {
            val now = clock.now()
            emit(now)
            delay((60 - now.toLocalDateTime(EventTimeZone).second) * 1000L)
        }
    }) { schedule, now -> eventTimeContext(schedule?.eventDate, now) }
        .stateIn(this, SharingStarted.WhileSubscribed(5000), eventTimeContext(null, clock.now()))
