package com.rectime.mobile.core.haptics

import androidx.compose.runtime.staticCompositionLocalOf
import com.rectime.mobile.core.cache.KeyValueStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal const val HapticEnabledKey = "preference_haptic_enabled"

class HapticPreference(
    private val store: KeyValueStore = createHapticPreferenceStore(),
) {
    private val mutex = Mutex()
    private val _enabled = MutableStateFlow(DefaultEnabled)

    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    suspend fun load() = mutex.withLock {
        _enabled.value = store.getString(HapticEnabledKey)
            ?.toBooleanStrictOrNull()
            ?: DefaultEnabled
    }

    suspend fun setEnabled(enabled: Boolean) = mutex.withLock {
        store.putString(HapticEnabledKey, enabled.toString())
        _enabled.value = enabled
    }

    companion object {
        const val DefaultEnabled = true
    }
}

val LocalHapticPreference = staticCompositionLocalOf { HapticPreference() }

// Device preferences must survive clearing the account cache on logout.
internal expect fun createHapticPreferenceStore(): KeyValueStore
