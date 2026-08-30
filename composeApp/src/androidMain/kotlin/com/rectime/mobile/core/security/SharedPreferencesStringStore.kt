package com.rectime.mobile.core.security

import android.content.SharedPreferences

internal class SharedPreferencesStringStore(
    private val prefs: SharedPreferences,
) : StringStore {
    override fun get(key: String): String? = prefs.getString(key, null)

    override fun put(key: String, value: String): Boolean =
        prefs.edit().putString(key, value).commit()

    override fun remove(key: String): Boolean =
        if (prefs.contains(key)) prefs.edit().remove(key).commit() else true

    override fun clearAll(): Boolean = prefs.edit().clear().commit()
}
