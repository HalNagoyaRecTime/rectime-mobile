package com.rectime.mobile.core.haptics

import android.content.Context
import com.rectime.mobile.core.cache.KeyValueStore
import com.rectime.mobile.core.platform.getPlatformContext

internal actual fun createHapticPreferenceStore(): KeyValueStore = object : KeyValueStore {
    private fun preferences() = requireNotNull(getPlatformContext())
        .getSharedPreferences("rectime_haptics", Context.MODE_PRIVATE)
    override suspend fun getString(key: String): String? = preferences().getString(key, null)
    override suspend fun putString(key: String, value: String) {
        preferences().edit().putString(key, value).apply()
    }
    override suspend fun clear() { preferences().edit().clear().apply() }
}
