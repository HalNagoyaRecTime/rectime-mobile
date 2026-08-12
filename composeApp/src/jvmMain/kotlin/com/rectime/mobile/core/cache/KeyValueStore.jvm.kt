package com.rectime.mobile.core.cache

import java.util.prefs.Preferences

actual class PlatformKeyValueStore : KeyValueStore {
    private val preferences = Preferences.userRoot().node("com/rectime/mobile/cache")

    override suspend fun getString(key: String): String? = preferences.get(key, null)

    override suspend fun putString(key: String, value: String) {
        preferences.put(key, value)
    }
}
