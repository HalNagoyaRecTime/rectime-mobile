package com.rectime.mobile.core.haptics

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HapticFeedbackTest {
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
