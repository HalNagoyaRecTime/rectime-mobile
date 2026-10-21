package com.rectime.mobile.core.cache

import java.util.prefs.Preferences

actual class PlatformKeyValueStore : KeyValueStore {
    private val preferences = Preferences.userRoot().node("com/rectime/mobile/cache")

    override suspend fun getString(key: String): String? = preferences.get(key, null)

    override suspend fun putString(key: String, value: String) {
        preferences.put(key, value)
    }

    override suspend fun clear() {
        preferences.clear()
        // 旧版の専用領域に保存したアバター設定も削除する。
        Preferences.userRoot().node("com/rectime/mobile/avatars").clear()
    }
}
