package com.rectime.mobile.core.cache

import android.content.Context
import com.rectime.mobile.core.platform.getPlatformContext

actual class PlatformKeyValueStore : KeyValueStore {
    override suspend fun getString(key: String): String? {
        val context = getPlatformContext() ?: return null
        return context
            .getSharedPreferences("rectime_cache", Context.MODE_PRIVATE)
            .getString(key, null)
    }

    override suspend fun putString(key: String, value: String) {
        val context = getPlatformContext() ?: return
        context
            .getSharedPreferences("rectime_cache", Context.MODE_PRIVATE)
            .edit()
            .putString(key, value)
            .apply()
    }

    override suspend fun clear() {
        val context = getPlatformContext() ?: return
        context
            .getSharedPreferences("rectime_cache", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }
}
