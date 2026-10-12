package com.rectime.mobile.core.haptics

import com.rectime.mobile.core.cache.KeyValueStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class HapticCapabilityTest {
    @Test
    fun initialStateHidesSettingWithoutBlockingOtherWork() = runTest {
        val release = CompletableDeferred<Boolean>()
        val state = HapticCapabilityState { release.await() }
        val loading = launch { state.load() }
        testScheduler.runCurrent()
        assertEquals(HapticSupportStatus.Loading, state.status.value)
        assertFalse(state.status.value.showSetting)
        assertFalse(state.status.value.canVibrate)
        release.complete(true)
        loading.join()
        assertTrue(state.status.value.showSetting)
        assertTrue(state.status.value.canVibrate)
    }

    @Test
    fun supportedSettingRemainsVisibleRegardlessOfSavedPreference() = runTest {
        for (saved in listOf(null, "true", "false")) {
            val preference = HapticPreference(object : KeyValueStore {
                override suspend fun getString(key: String): String? = saved
                override suspend fun putString(key: String, value: String) = Unit
                override suspend fun clear() = Unit
            })
            val state = HapticCapabilityState { true }
            preference.load()
            state.load()
            assertTrue(state.status.value.showSetting)
            assertEquals(saved?.toBooleanStrict() ?: HapticPreference.DefaultEnabled, preference.enabled.value)
            preference.setEnabled(!preference.enabled.value)
            assertTrue(state.status.value.showSetting)
        }
    }

    @Test
    fun unsupportedDeviceHidesSettingEvenWhenPreferenceIsEnabled() = runTest {
        val state = HapticCapabilityState { false }
        state.load()
        assertEquals(HapticSupportStatus.Unsupported, state.status.value)
        assertFalse(state.status.value.showSetting)
        assertFalse(state.status.value.canVibrate)
    }

    @Test
    fun successfulResultIsSharedByConcurrentAndLaterCalls() = runTest {
        for (supported in listOf(true, false)) {
            var calls = 0
            val release = CompletableDeferred<Unit>()
            val state = HapticCapabilityState {
                calls++
                release.await()
                supported
            }
            val first = launch { state.load() }
            val second = launch { state.load() }
            testScheduler.runCurrent()
            assertEquals(1, calls)
            release.complete(Unit)
            first.join()
            second.join()
            repeat(3) { state.load() }
            assertEquals(1, calls)
        }
    }

    @Test
    fun failedDetectionShowsDisabledSettingAndCanRetry() = runTest {
        var calls = 0
        val release = CompletableDeferred<Boolean>()
        val state = HapticCapabilityState {
            if (++calls == 1) error("hardware query failed")
            release.await()
        }
        state.load()
        assertEquals(HapticSupportStatus.Failed, state.status.value)
        assertTrue(state.status.value.showSetting)
        assertFalse(state.status.value.canVibrate)
        val retry = launch { state.load() }
        testScheduler.runCurrent()
        assertEquals(HapticSupportStatus.Failed, state.status.value)
        release.complete(true)
        retry.join()
        assertEquals(HapticSupportStatus.Supported, state.status.value)
        state.load()
        assertEquals(2, calls)
    }

    @Test
    fun cancellationIsNotRecordedAsUnsupportedOrFailed() = runTest {
        var cancelled = true
        val state = HapticCapabilityState {
            if (cancelled) throw CancellationException()
            false
        }
        assertFailsWith<CancellationException> { state.load() }
        assertEquals(HapticSupportStatus.Loading, state.status.value)
        cancelled = false
        state.load()
        assertEquals(HapticSupportStatus.Unsupported, state.status.value)
    }

    @Test
    fun persistedResultSkipsHardwareQueryAfterRestart() = runTest {
        for (supported in listOf(true, false)) {
            var cache: String? = null
            var detections = 0
            var writes = 0
            val loader = {
                loadCachedHapticSupport(
                    readCache = { cache },
                    detectSupport = { detections++; supported },
                    writeCache = { cache = it; writes++ },
                )
            }
            val firstLaunch = HapticCapabilityState { loader() }
            firstLaunch.load()
            val nextLaunch = HapticCapabilityState { loader() }
            nextLaunch.load()
            assertEquals(firstLaunch.status.value, nextLaunch.status.value)
            assertEquals(supported.toString(), cache)
            assertEquals(1, detections)
            assertEquals(1, writes)
        }
    }

    @Test
    fun missingOrInvalidCacheRequiresDetection() {
        for (cache in listOf(null, "", "invalid")) {
            var saved: String? = null
            var detections = 0
            assertTrue(loadCachedHapticSupport(
                readCache = { cache },
                detectSupport = { detections++; true },
                writeCache = { saved = it },
            ))
            assertEquals(1, detections)
            assertEquals("true", saved)
        }
    }

    @Test
    fun cacheReadFailureDoesNotHideSupportedSetting() = runTest {
        val state = HapticCapabilityState {
            loadCachedHapticSupport(
                readCache = { error("read failed") },
                detectSupport = { true },
                writeCache = {},
            )
        }
        state.load()
        assertEquals(HapticSupportStatus.Supported, state.status.value)
    }

    @Test
    fun cacheWriteFailureDoesNotDiscardSuccessfulDetection() = runTest {
        var detections = 0
        val state = HapticCapabilityState {
            loadCachedHapticSupport(
                readCache = { null },
                detectSupport = { detections++; true },
                writeCache = { error("write failed") },
            )
        }
        state.load()
        state.load()
        assertEquals(HapticSupportStatus.Supported, state.status.value)
        assertEquals(1, detections)
    }

    @Test
    fun failedHardwareQueryIsNeverSavedAsUnsupported() = runTest {
        var saved: String? = null
        var fail = true
        val state = HapticCapabilityState {
            loadCachedHapticSupport(
                readCache = { saved },
                detectSupport = { if (fail) error("query failed") else false },
                writeCache = { saved = it },
            )
        }
        state.load()
        assertEquals(HapticSupportStatus.Failed, state.status.value)
        assertEquals(null, saved)
        fail = false
        state.load()
        assertEquals(HapticSupportStatus.Unsupported, state.status.value)
        assertEquals("false", saved)
    }

    @Test
    fun cacheCancellationIsPropagated() {
        assertFailsWith<CancellationException> {
            loadCachedHapticSupport(
                readCache = { throw CancellationException() },
                detectSupport = { error("must not query hardware") },
                writeCache = {},
            )
        }
        assertFailsWith<CancellationException> {
            loadCachedHapticSupport(
                readCache = { null },
                detectSupport = { true },
                writeCache = { throw CancellationException() },
            )
        }
    }
}
