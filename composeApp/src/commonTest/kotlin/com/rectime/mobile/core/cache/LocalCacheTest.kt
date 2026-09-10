package com.rectime.mobile.core.cache

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@Serializable
private data class Sample(val id: Int, val name: String)

class LocalCacheTest {
    @BeforeTest
    fun resetSharedState() {
        // CacheGenerationはプロセス全体で共有されるため、テスト間で値が
        // 漏れないようリセットする。
        CacheGeneration.resetForTest()
    }

    @Test
    fun saveThenLoadRoundTripsAValue() = runTest {
        val cache = LocalCache(InMemoryKeyValueStore())
        val value = Sample(id = 1, name = "test")

        cache.save("key", value)

        assertEquals(value, cache.load<Sample>("key"))
    }

    @Test
    fun loadReturnsNullForAMissingKey() = runTest {
        val cache = LocalCache(InMemoryKeyValueStore())

        assertNull(cache.load<Sample>("missing"))
    }

    @Test
    fun loadReturnsNullInsteadOfThrowingForCorruptJson() = runTest {
        val store = InMemoryKeyValueStore()
        store.putString("key", "not valid json")
        val cache = LocalCache(store)

        assertNull(cache.load<Sample>("key"))
    }

    @Test
    fun loadReturnsNullInsteadOfThrowingForSchemaMismatchedJson() = runTest {
        val store = InMemoryKeyValueStore()
        // Sampleとは異なる形のJSON(旧スキーマ相当)
        store.putString("key", """{"unrelated":"field"}""")
        val cache = LocalCache(store)

        assertNull(cache.load<Sample>("key"))
    }

    @Test
    fun clearAllRemovesPreviouslySavedValues() = runTest {
        val cache = LocalCache(InMemoryKeyValueStore())
        cache.save("key", Sample(id = 1, name = "test"))

        cache.clearAll()

        assertNull(cache.load<Sample>("key"))
    }

    @Test
    fun clearAllBumpsCacheGenerationSoInFlightFetchesDoNotWriteStaleDataBack() = runTest {
        val cache = LocalCache(InMemoryKeyValueStore())
        val generationBefore = CacheGeneration.value

        cache.clearAll()

        assertEquals(generationBefore + 1, CacheGeneration.value)
    }
}

private class InMemoryKeyValueStore : KeyValueStore {
    private val values = mutableMapOf<String, String>()

    override suspend fun getString(key: String): String? = values[key]

    override suspend fun putString(key: String, value: String) {
        values[key] = value
    }

    override suspend fun clear() {
        values.clear()
    }
}
