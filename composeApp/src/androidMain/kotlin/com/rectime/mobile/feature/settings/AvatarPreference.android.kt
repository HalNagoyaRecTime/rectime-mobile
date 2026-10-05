package com.rectime.mobile.feature.settings

import android.content.Context
import com.rectime.mobile.core.platform.getPlatformContext

private fun avatarPreferences() = requireNotNull(getPlatformContext())
    .getSharedPreferences("rectime_avatar_preferences", Context.MODE_PRIVATE)

internal actual suspend fun readAvatarPreference(userId: String): String? = avatarPreferences().getString(userId, null)
internal actual suspend fun writeAvatarPreference(userId: String, value: String) {
    avatarPreferences().edit().putString(userId, value).apply()
}
