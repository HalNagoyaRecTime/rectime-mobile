package com.rectime.mobile.core.haptics

import com.rectime.mobile.core.cache.KeyValueStore
import java.util.prefs.Preferences

internal actual fun createHapticPreferenceStore(): KeyValueStore = object : KeyValueStore {
    private val preferences = Preferences.userRoot().node("com/rectime/mobile/haptics")
    override suspend fun getString(key: String): String? = preferences.get(key, null)
    override suspend fun putString(key: String, value: String) { preferences.put(key, value) }
    override suspend fun clear() { preferences.clear() }
}
