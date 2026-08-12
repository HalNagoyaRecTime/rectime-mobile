package com.rectime.mobile.core.cache

import platform.Foundation.NSUserDefaults

actual class PlatformKeyValueStore : KeyValueStore {
    private val defaults = NSUserDefaults.standardUserDefaults

    override suspend fun getString(key: String): String? =
        defaults.stringForKey(prefixed(key))

    override suspend fun putString(key: String, value: String) {
        defaults.setObject(value, prefixed(key))
    }

    private fun prefixed(key: String): String = "rectime_cache_$key"
}
