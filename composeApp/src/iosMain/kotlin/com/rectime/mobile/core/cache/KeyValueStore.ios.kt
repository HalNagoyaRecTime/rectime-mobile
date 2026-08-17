package com.rectime.mobile.core.cache

import platform.Foundation.NSUserDefaults

actual class PlatformKeyValueStore : KeyValueStore {
    private val defaults = NSUserDefaults.standardUserDefaults

    override suspend fun getString(key: String): String? =
        defaults.stringForKey(prefixed(key))

    override suspend fun putString(key: String, value: String) {
        defaults.setObject(value, prefixed(key))
    }

    // NSUserDefaultsはアプリ全体で共有される名前空間のため、プレフィックスが付いたキーのみ削除する。
    override suspend fun clear() {
        val keysToRemove = defaults.dictionaryRepresentation().keys
            .filterIsInstance<String>()
            .filter { it.startsWith(PREFIX) }
        keysToRemove.forEach { defaults.removeObjectForKey(it) }
    }

    private fun prefixed(key: String): String = "$PREFIX$key"

    private companion object {
        const val PREFIX = "rectime_cache_"
    }
}
