package com.rectime.mobile.feature.event

import com.rectime.mobile.core.cache.CacheGeneration
import com.rectime.mobile.core.cache.KeyValueStore
import com.rectime.mobile.core.cache.LocalCache
import com.rectime.mobile.core.network.EventDetailResponse
import com.rectime.mobile.feature.schedule.EventResponse
import com.rectime.mobile.feature.schedule.EventsResponse
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.serialization.json.Json
import kotlin.test.assertFalse
import kotlinx.coroutines.CancellationException
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
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

    @Test
    fun delayedListCannotOverwriteDetailSavedByALaterRequest() = runTest {
        val local = LocalCache(MemoryStore())
        val listCache = EventCache(local)
        val detailCache = EventCache(local)
        val listRequest = listCache.beginRequest()
        val detailRequest = detailCache.beginRequest()
        detailCache.saveDetail(detail("B").copy(updatedAt = "2026-01-01T10:00:01Z"), detailRequest)
        val displayed = listCache.saveEvents(events("A").copy(events = events("A").events.map {
            it.copy(updatedAt = "2026-01-01T10:00:00Z")
        }), listRequest)
        assertEquals("B", displayed.events.single().eventName)
        assertEquals("B", listCache.loadEvents()?.events?.single()?.eventName)
        assertEquals("B", detailCache.loadDetail(1)?.eventName)
    }

    @Test
    fun sameSecondUsesTheLaterStartedRequestInBothCompletionOrders() = runTest {
        for (detailFirst in listOf(false, true)) {
            val cache = EventCache(LocalCache(MemoryStore()))
            val listRequest = cache.beginRequest()
            val detailRequest = cache.beginRequest()
            val list = events("A").copy(events = events("A").events.map { it.copy(updatedAt = "2026-01-01T10:00:00Z") })
            val detail = detail("B").copy(updatedAt = "2026-01-01T10:00:00Z")
            if (detailFirst) {
                cache.saveDetail(detail, detailRequest)
                cache.saveEvents(list, listRequest)
            } else {
                cache.saveEvents(list, listRequest)
                cache.saveDetail(detail, detailRequest)
            }
            assertEquals("B", cache.loadDetail(1)?.eventName)
            assertEquals("B", cache.loadEvents()?.events?.single()?.eventName)
        }
    }

    @Test
    fun delayedOldDetailCannotOverwriteANewerList() = runTest {
        val cache = EventCache(LocalCache(MemoryStore()))
        val detailRequest = cache.beginRequest()
        val listRequest = cache.beginRequest()
        cache.saveEvents(events("B"), listRequest)
        val displayed = cache.saveDetail(detail("A"), detailRequest)
        assertEquals("B", displayed.eventName)
        assertEquals("B", cache.loadDetail(1)?.eventName)
    }

    @Test
    fun timestampsTakePriorityOverRequestOrderAndSqlDatesAreComparable() = runTest {
        val cache = EventCache(LocalCache(MemoryStore()))
        cache.saveDetail(detail("B").copy(updatedAt = "2026-01-01 10:00:02"))
        cache.saveEvents(events("A").copy(events = events("A").events.map {
            it.copy(updatedAt = "2026-01-01T10:00:01Z")
        }))
        assertEquals("B", cache.loadDetail(1)?.eventName)
        cache.saveEvents(events("C").copy(events = events("C").events.map {
            it.copy(updatedAt = "2026-01-01T10:00:03Z")
        }))
        cache.saveDetail(detail("A").copy(updatedAt = "2026-01-01T10:00:01Z"))
        assertEquals("C", cache.loadDetail(1)?.eventName)
    }

    @Test
    fun simultaneousDetailsFromDifferentCacheInstancesPreserveBothEvents() = runTest {
        val storage = MemoryStore()
        val local = LocalCache(storage)
        val first = EventCache(local)
        val second = EventCache(local)
        first.saveEvents(events("old").copy(events = events("old").events + events("old").events.single().copy(eventId = 2)))
        val firstRequest = first.beginRequest()
        val secondRequest = second.beginRequest()
        val gate = CompletableDeferred<Unit>()
        storage.pauseNextListRead = gate
        val a = async { first.saveDetail(detail("A"), firstRequest) }
        runCurrent()
        val b = async { second.saveDetail(detail("B").copy(eventId = 2), secondRequest) }
        runCurrent()
        assertFalse(b.isCompleted)
        gate.complete(Unit)
        a.await()
        b.await()
        assertEquals(listOf("A", "B"), first.loadEvents()?.events?.map { it.eventName })
    }

    @Test
    fun requestFromBeforeLogoutCannotSaveIntoTheNextSession() = runTest {
        val local = LocalCache(MemoryStore())
        val cache = EventCache(local)
        val request = cache.beginRequest()
        local.clearAll()
        cache.saveEvents(events("old-user"), request)
        cache.saveDetail(detail("old-user"), request)
        assertNull(cache.loadEvents())
        assertNull(cache.loadDetail(1))
    }

    @Test
    fun cacheWriteFailureStillReturnsTheSuccessfulNetworkData() = runTest {
        val cache = EventCache(LocalCache(object : KeyValueStore {
            override suspend fun getString(key: String): String? = null
            override suspend fun putString(key: String, value: String): Unit = error("storage failed")
            override suspend fun clear() = Unit
        }))
        assertEquals("fresh", cache.saveEvents(events("fresh")).events.single().eventName)
        assertEquals("fresh", cache.saveDetail(detail("fresh")).eventName)
    }

    @Test
    fun detailWithoutUpdatedAtRemainsReadableAfterUpgrade() = runTest {
        val saved = Json.decodeFromString<EventDetailResponse>(
            """{"event_id":1,"event_name":"old","venues":[],"start_time":"0900","end_time":"0930","rule_text":null}""",
        )
        assertNull(saved.updatedAt)
        assertEquals("old", saved.eventName)
    }

    private fun detail(name: String) = EventDetailResponse(1, name, emptyList(), "0900", "0930", null)
    private fun events(name: String) = EventsResponse(listOf(EventResponse(
        1, name, null, emptyList(), "0900", "0930", "2026-01-01", "2026-01-01",
    )), 1, 100, 0)

    private class MemoryStore : KeyValueStore {
        private val values = mutableMapOf<String, String>()
        var clearOnListRead = false
        var pauseNextListRead: CompletableDeferred<Unit>? = null
        override suspend fun getString(key: String): String? {
            if (key == "schedule_events_v1") {
                pauseNextListRead?.let { gate -> pauseNextListRead = null; gate.await() }
            }
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
