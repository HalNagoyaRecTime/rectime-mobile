package com.rectime.mobile.core.haptics

import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class HapticFeedbackTest {
    @Test
    fun delayedLoadCannotOverwriteASettingChange() = runTest {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var stored = "true"
        val store = object : com.rectime.mobile.core.cache.KeyValueStore {
            override suspend fun getString(key: String): String {
                val oldValue = stored
                started.complete(Unit)
                release.await()
                return oldValue
            }
            override suspend fun putString(key: String, value: String) { stored = value }
            override suspend fun clear() = Unit
        }
        val preference = HapticPreference(store)
        val loading = launch { preference.load() }
        started.await()
        val changing = launch { preference.setEnabled(false) }
        testScheduler.runCurrent()
        release.complete(Unit)
        loading.join()
        changing.join()
        assertFalse(preference.enabled.value)
        assertEquals("false", stored)
    }

    @Test
    fun storageCancellationIsNotSwallowed() = runTest {
        val store = object : com.rectime.mobile.core.cache.KeyValueStore {
            override suspend fun getString(key: String): String? = throw CancellationException()
            override suspend fun putString(key: String, value: String) = throw CancellationException()
            override suspend fun clear() = Unit
        }
        val preference = HapticPreference(store)
        assertFailsWith<CancellationException> { preference.load() }
        assertFailsWith<CancellationException> { preference.setEnabled(false) }
    }

    @Test
    fun storageFailureDoesNotPreventUsingThePreference() = runTest {
        val store = object : com.rectime.mobile.core.cache.KeyValueStore {
            override suspend fun getString(key: String): String? = error("読み込み失敗")
            override suspend fun putString(key: String, value: String) = error("保存失敗")
            override suspend fun clear() = Unit
        }
        val preference = HapticPreference(store)
        preference.load()
        assertTrue(preference.enabled.value)
        preference.setEnabled(false)
        assertFalse(preference.enabled.value)
        preference.load()
        assertFalse(preference.enabled.value)
    }

    @Test
    fun enabledThresholdCrossingRequestsHapticOnce() {
        var requestCount = 0
        val detector = HapticThresholdDetector(onThresholdReached = { requestCount++ })

        detector.onDistanceFractionChanged(0.99f, enabled = true)
        detector.onDistanceFractionChanged(1f, enabled = true)
        detector.onDistanceFractionChanged(1.4f, enabled = true)

        assertEquals(1, requestCount)
    }

    @Test
    fun returningBelowThresholdArmsTheNextRequest() {
        var requestCount = 0
        val detector = HapticThresholdDetector(onThresholdReached = { requestCount++ })

        detector.onDistanceFractionChanged(1f, enabled = true)
        detector.onDistanceFractionChanged(0.8f, enabled = true)
        detector.onDistanceFractionChanged(1f, enabled = true)

        assertEquals(2, requestCount)
    }

    @Test
    fun disabledHapticDoesNotRequestFeedback() {
        var requestCount = 0
        val detector = HapticThresholdDetector(onThresholdReached = { requestCount++ })

        detector.onDistanceFractionChanged(1f, enabled = false)
        detector.onDistanceFractionChanged(1.5f, enabled = false)

        assertEquals(0, requestCount)
    }

    @Test
    fun preferenceDefaultsToEnabled() = runTest {
        val preference = HapticPreference(InMemoryKeyValueStore())

        preference.load()

        assertTrue(preference.enabled.value)
    }

    @Test
    fun preferenceCanBeSavedAndRestored() = runTest {
        val store = InMemoryKeyValueStore()
        val preference = HapticPreference(store)

        preference.setEnabled(false)

        val restored = HapticPreference(store)
        restored.load()

        assertFalse(restored.enabled.value)
    }

    private class InMemoryKeyValueStore : com.rectime.mobile.core.cache.KeyValueStore {
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
