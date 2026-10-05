package com.rectime.mobile.feature.settings

import java.util.prefs.Preferences

private val avatarPreferences = Preferences.userRoot().node("com/rectime/mobile/avatars")
internal actual suspend fun readAvatarPreference(userId: String): String? = avatarPreferences.get(userId, null)
internal actual suspend fun writeAvatarPreference(userId: String, value: String) {
    avatarPreferences.put(userId, value)
}
