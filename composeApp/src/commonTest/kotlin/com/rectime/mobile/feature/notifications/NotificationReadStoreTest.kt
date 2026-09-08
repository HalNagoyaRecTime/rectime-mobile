package com.rectime.mobile.feature.notifications

import com.rectime.mobile.core.cache.KeyValueStore
import com.rectime.mobile.core.cache.LocalCache
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NotificationReadStoreTest {

    // ---- 正常系 ----

    @Test
    fun markedNotificationBecomesRead() = runTest {
        val store = NotificationReadStore(LocalCache(InMemoryKeyValueStore()))

        store.markRead(15)

        assertEquals(setOf(15), store.readIds.value)
    }

    @Test
    fun readIdsAreRestoredFromCache() = runTest {
        val keyValueStore = InMemoryKeyValueStore()
        NotificationReadStore(LocalCache(keyValueStore)).markRead(15)

        val restored = NotificationReadStore(LocalCache(keyValueStore))
        restored.restore()

        assertEquals(setOf(15), restored.readIds.value)
    }

    @Test
    fun markingSameNotificationTwiceKeepsSingleEntry() = runTest {
        val store = NotificationReadStore(LocalCache(InMemoryKeyValueStore()))

        store.markRead(15)
        store.markRead(15)

        assertEquals(setOf(15), store.readIds.value)
    }

    @Test
    fun markReadRestoresExistingIdsBeforeAdding() = runTest {
        val keyValueStore = InMemoryKeyValueStore()
        NotificationReadStore(LocalCache(keyValueStore)).markRead(15)

        val store = NotificationReadStore(LocalCache(keyValueStore))
        store.markRead(16)

        assertEquals(setOf(15, 16), store.readIds.value)
    }

    @Test
    fun readIdsAreDroppedWhenCacheIsCleared() = runTest {
        val keyValueStore = InMemoryKeyValueStore()
        val store = NotificationReadStore(LocalCache(keyValueStore))
        store.markRead(15)

        // ログアウト・ユーザー切替でLocalCacheごと消える状況
        keyValueStore.clear()
        store.restore()

        assertTrue(store.readIds.value.isEmpty())
    }

    // ---- 異常系 ----

    @Test
    fun restoreFallsBackToEmptyWhenCacheIsBroken() = runTest {
        val keyValueStore = InMemoryKeyValueStore()
        keyValueStore.putString("read_notification_ids_v1", "{\"broken\":true}")

        val store = NotificationReadStore(LocalCache(keyValueStore))
        store.restore()

        assertTrue(store.readIds.value.isEmpty())
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
}
