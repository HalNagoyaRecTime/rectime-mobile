package com.rectime.mobile.feature.event

import com.rectime.mobile.core.cache.CacheGeneration
import com.rectime.mobile.core.cache.KeyValueStore
import com.rectime.mobile.core.cache.LocalCache
import com.rectime.mobile.core.network.EventDetailResponse
import com.rectime.mobile.feature.schedule.EventResponse
import com.rectime.mobile.feature.schedule.EventsResponse
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EventCacheTest {
    @BeforeTest
    fun resetGeneration() { CacheGeneration.resetForTest() }

    @Test
    fun scheduleUpdateIsUsedForDetailPreview() = runTest {
        val cache = EventCache(LocalCache(MemoryStore()))
        cache.saveDetail(detail("old"))
        cache.saveEvents(events("new"))
        assertEquals("new", cache.loadDetail(1)?.eventName)
    }

    @Test
    fun detailUpdateChangesExistingListEntryAndPreservesOtherEvents() = runTest {
        val cache = EventCache(LocalCache(MemoryStore()))
        val feed = events("old")
        cache.saveEvents(feed.copy(events = feed.events + feed.events.first().copy(eventId = 2)))
        cache.saveDetail(detail("new"))
        assertEquals(listOf("new", "old"), cache.loadEvents()?.events?.map { it.eventName })
        assertEquals("new", cache.loadDetail(1)?.eventName)
    }

    @Test
    fun detailAloneDoesNotCreateIncompleteScheduleFeed() = runTest {
        val cache = EventCache(LocalCache(MemoryStore()))
        cache.saveDetail(detail("new"))
        assertNull(cache.loadEvents())
        assertEquals("new", cache.loadDetail(1)?.eventName)
    }

    @Test
    fun corruptedListDoesNotHideValidSavedDetail() = runTest {
        val storage = MemoryStore()
        val cache = EventCache(LocalCache(storage))
        cache.saveDetail(detail("saved"))
        storage.putString("schedule_events_v1", "invalid JSON")
        assertEquals("saved", cache.loadDetail(1)?.eventName)
    }

    @Test
    fun logoutDuringListReadPreventsDetailMergeFromRestoringOldList() = runTest {
        val storage = MemoryStore()
        val local = LocalCache(storage)
        val cache = EventCache(local)
        cache.saveEvents(events("old"))
        storage.clearOnListRead = true
        cache.saveDetail(detail("new"))
        assertNull(cache.loadEvents())
        assertNull(local.load<EventDetailResponse>("event_detail_v1_1"))
    }

    private fun detail(name: String) = EventDetailResponse(1, name, emptyList(), "0900", "0930", null)
    private fun events(name: String) = EventsResponse(listOf(EventResponse(
        1, name, null, emptyList(), "0900", "0930", "2026-01-01", "2026-01-01",
    )), 1, 100, 0)

    private class MemoryStore : KeyValueStore {
        private val values = mutableMapOf<String, String>()
        var clearOnListRead = false
        override suspend fun getString(key: String): String? {
            val value = values[key]
            if (clearOnListRead && key == "schedule_events_v1") {
                clearOnListRead = false
                CacheGeneration.bump()
                values.clear()
            }
            return value
        }
        override suspend fun putString(key: String, value: String) { values[key] = value }
        override suspend fun clear() { values.clear() }
    }
}
