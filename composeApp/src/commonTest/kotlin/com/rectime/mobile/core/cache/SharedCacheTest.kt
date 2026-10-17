package com.rectime.mobile.core.cache

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SharedCacheTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setUp() {
        Dispatchers.setMain(dispatcher)
        CacheGeneration.resetForTest()
    }
    @AfterTest fun tearDown() { Dispatchers.resetMain() }

    @Test fun anotherCacheInstancePublishesLatestValueWithoutAnotherFetch() = runTest(dispatcher) {
        val storage = MemoryStore()
        val writer = LocalCache(storage)
        val reader = LocalCache(storage)
        val seen = mutableListOf<List<Int>?>()
        backgroundScope.launch(dispatcher) { reader.observe<List<Int>>("feed").collect { seen += it } }
        runCurrent()
        writer.saveFetched("feed", listOf(1))
        runCurrent()
        writer.saveFetched("feed", emptyList<Int>())
        runCurrent()
        assertEquals(listOf(null, listOf(1), emptyList()), seen)
        assertEquals(emptyList(), reader.load<List<Int>>("feed"))
        writer.clearAll()
        runCurrent()
        assertNull(seen.last())
    }

    @Test fun failedPersistenceStillPublishesAndRetainsTheLatestFetch() = runTest(dispatcher) {
        val storage = MemoryStore()
        val writer = LocalCache(storage)
        val reader = LocalCache(storage)
        writer.save("feed", "old")
        storage.failWrites = true
        val result = fetchWithCacheFirst(
            fetchLive = { "latest" }, loadCache = { reader.load<String>("feed") },
            saveCache = { writer.saveFetched("feed", it) }, onCached = {},
        )
        assertEquals(CachedFetchResult.Fresh("latest"), result)
        assertEquals("latest", reader.load<String>("feed"))
        val offline = fetchWithCacheFirst(
            fetchLive = { error("offline") }, loadCache = { reader.load<String>("feed") },
            saveCache = {}, onCached = {},
        )
        assertEquals("latest", assertIs<CachedFetchResult.Cached<String>>(offline).value)
    }

    @Test fun fallbackReadsChangesThatArriveDuringTheFailedRequest() = runTest(dispatcher) {
        val cache = LocalCache(MemoryStore())
        cache.save("feed", "initial")
        val gate = CompletableDeferred<Unit>()
        val fetch = async {
            fetchWithCacheFirst(fetchLive = { gate.await(); error("offline") },
                loadCache = { cache.load<String>("feed") }, saveCache = {}, onCached = {})
        }
        runCurrent()
        cache.save("feed", "updated-by-other-screen")
        gate.complete(Unit)
        assertEquals("updated-by-other-screen", assertIs<CachedFetchResult.Cached<String>>(fetch.await()).value)
    }

    @Test fun duplicateEntrySignalsJoinAndUpdatesDuringFetchAreCoalescedIntoOneRepeat() = runTest(dispatcher) {
        val queue = CacheRefreshQueue()
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        val action: suspend kotlinx.coroutines.CoroutineScope.() -> Unit = { calls++; gate.await() }
        val running = queue.launch(this, afterCurrent = false, block = action)
        queue.launch(this, afterCurrent = true, block = action)
        runCurrent()
        assertEquals(1, calls)
        repeat(10) { queue.launch(this, afterCurrent = true, block = action) }
        gate.complete(Unit)
        running.join()
        assertEquals(2, calls)
    }

    @Test fun logoutDropsQueuedRefreshAndClearsMemoryEvenWhenNothingWasPersisted() = runTest(dispatcher) {
        val storage = MemoryStore().also { it.failWrites = true }
        val cache = LocalCache(storage)
        runCatching { cache.save("feed", "old-user") }
        val queue = CacheRefreshQueue()
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        val action: suspend kotlinx.coroutines.CoroutineScope.() -> Unit = { calls++; gate.await() }
        val running = queue.launch(this, afterCurrent = false, block = action)
        runCurrent()
        queue.launch(this, afterCurrent = true, block = action)
        cache.clearAll()
        gate.complete(Unit)
        running.join()
        assertEquals(1, calls)
        assertNull(cache.load<String>("feed"))
    }

    @Test fun cancellingOneWaiterDoesNotCancelAnotherScreensSharedRequest() = runTest(dispatcher) {
        val cache = LocalCache(MemoryStore())
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        val first = async { cache.requests.run("feed") { calls++; gate.await(); "latest" } }
        val second = async { cache.requests.run("feed") { calls++; "duplicate" } }
        runCurrent()
        first.cancel()
        gate.complete(Unit)
        assertEquals("latest", second.await())
        assertEquals(1, calls)
    }

    @Test fun logoutCancelsSharedFetchBeforeItCanPublishIntoTheNextSession() = runTest(dispatcher) {
        val cache = LocalCache(MemoryStore())
        val gate = CompletableDeferred<Unit>()
        val fetch = async {
            fetchWithCacheFirst(
                fetchLive = { cache.requests.run("feed") { gate.await(); "old-user" } },
                loadCache = { cache.load<String>("feed") },
                saveCache = { cache.save("feed", it) }, onCached = {},
            )
        }
        runCurrent()
        cache.clearAll()
        gate.complete(Unit)
        assertIs<CachedFetchResult.Failed>(fetch.await())
        assertNull(cache.load<String>("feed"))
    }

    @Test fun separateStorageDoesNotShareEitherValuesOrRequests() = runTest(dispatcher) {
        val a = LocalCache(MemoryStore())
        val b = LocalCache(MemoryStore())
        a.save("feed", "a")
        assertNull(b.load<String>("feed"))
        assertEquals("b", b.requests.run("feed") { "b" })
        assertEquals("a", a.load<String>("feed"))
    }

    @Test fun failedCacheClearDoesNotRestoreEitherOpenedOrUnopenedPreviousUserData() = runTest(dispatcher) {
        val storage = MemoryStore()
        val cache = LocalCache(storage)
        cache.save("opened", "old-user")
        storage.values["unopened"] = kotlinx.serialization.json.Json.encodeToString("old-user")
        storage.failClear = true
        assertTrue(runCatching { cache.clearAll() }.isFailure)
        val nextSession = LocalCache(storage)
        assertNull(nextSession.load<String>("opened"))
        assertNull(nextSession.load<String>("unopened"))
        nextSession.save("opened", "new-user")
        assertEquals("new-user", cache.load<String>("opened"))
    }

    @Test fun closingAClientWaitsForAnotherScreensSharedRequest() = runTest(dispatcher) {
        val cache = LocalCache(MemoryStore())
        val gate = CompletableDeferred<Unit>()
        val first = async { cache.requests.run("feed") { gate.await(); "latest" } }
        val second = async { cache.requests.run("feed") { "duplicate" } }
        runCurrent()
        first.cancel()
        var closed = false
        cache.requests.afterRequests { closed = true }
        runCurrent()
        assertTrue(!closed)
        gate.complete(Unit)
        assertEquals("latest", second.await())
        runCurrent()
        assertTrue(closed)
    }

    @Test fun memoryOnlyDetailsAreBoundedWithoutEvictingPersistedResources() = runTest(dispatcher) {
        val storage = MemoryStore()
        val cache = LocalCache(storage)
        cache.save("feed", "persisted")
        repeat(101) { cache.saveMemory("detail:$it", "detail $it") }
        assertNull(cache.load<String>("detail:0"))
        assertEquals("detail 100", cache.load<String>("detail:100"))
        assertEquals("persisted", cache.load<String>("feed"))
        assertEquals(setOf("feed"), storage.values.keys)
    }

    private class MemoryStore : KeyValueStore {
        val values = mutableMapOf<String, String>()
        var failWrites = false
        var failClear = false
        override suspend fun getString(key: String) = values[key]
        override suspend fun putString(key: String, value: String) {
            if (failWrites) error("storage unavailable")
            values[key] = value
        }
        override suspend fun clear() {
            if (failClear) error("clear unavailable")
            values.clear()
        }
    }
}
