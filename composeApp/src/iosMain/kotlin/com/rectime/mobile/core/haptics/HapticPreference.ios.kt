package com.rectime.mobile.core.haptics

import com.rectime.mobile.core.cache.KeyValueStore
import platform.Foundation.NSUserDefaults

private fun hapticPreferenceKey(key: String) = "rectime_haptics_$key"

// SwiftUIの起動演出も、共通の振動設定と同じ保存値を参照する。
class IosSplashPreferences {
    fun isVibrationEnabled(): Boolean = NSUserDefaults.standardUserDefaults
        .stringForKey(hapticPreferenceKey(HapticEnabledKey))
        ?.toBooleanStrictOrNull() ?: HapticPreference.DefaultEnabled
}

internal actual fun createHapticPreferenceStore(): KeyValueStore = object : KeyValueStore {
    private val defaults = NSUserDefaults.standardUserDefaults
    private fun prefixed(key: String) = hapticPreferenceKey(key)
    override suspend fun getString(key: String): String? = defaults.stringForKey(prefixed(key))
    override suspend fun putString(key: String, value: String) {
        defaults.setObject(value, prefixed(key))
    }
    override suspend fun clear() { defaults.removeObjectForKey(prefixed(HapticEnabledKey)) }
}
