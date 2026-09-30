package com.rectime.mobile.core.haptics

import androidx.compose.runtime.staticCompositionLocalOf
import com.rectime.mobile.core.cache.KeyValueStore
import com.rectime.mobile.core.cache.PlatformKeyValueStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val HapticEnabledKey = "preference_haptic_enabled"

class HapticPreference(
    private val store: KeyValueStore = PlatformKeyValueStore(),
) {
    private val _enabled = MutableStateFlow(DefaultEnabled)

    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    suspend fun load() {
        _enabled.value = store.getString(HapticEnabledKey)
            ?.toBooleanStrictOrNull()
            ?: DefaultEnabled
    }

    suspend fun setEnabled(enabled: Boolean) {
        store.putString(HapticEnabledKey, enabled.toString())
        _enabled.value = enabled
    }

    companion object {
        const val DefaultEnabled = true
    }
}

val LocalHapticPreference = staticCompositionLocalOf { HapticPreference() }
