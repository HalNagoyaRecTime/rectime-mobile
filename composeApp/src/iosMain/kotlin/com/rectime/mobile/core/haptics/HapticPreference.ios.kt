package com.rectime.mobile.core.haptics

import com.rectime.mobile.core.cache.KeyValueStore
import platform.Foundation.NSUserDefaults

internal actual fun createHapticPreferenceStore(): KeyValueStore = object : KeyValueStore {
    private val defaults = NSUserDefaults.standardUserDefaults
    private fun prefixed(key: String) = "rectime_haptics_$key"
    override suspend fun getString(key: String): String? = defaults.stringForKey(prefixed(key))
    override suspend fun putString(key: String, value: String) {
        defaults.setObject(value, prefixed(key))
    }
    override suspend fun clear() { defaults.removeObjectForKey(prefixed(HapticEnabledKey)) }
}
