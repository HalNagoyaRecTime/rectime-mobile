package com.rectime.mobile.feature.settings

import platform.Foundation.NSUserDefaults

internal actual suspend fun readAvatarPreference(userId: String): String? =
    NSUserDefaults.standardUserDefaults.stringForKey("rectime_avatar_preference_$userId")
internal actual suspend fun writeAvatarPreference(userId: String, value: String) {
    NSUserDefaults.standardUserDefaults.setObject(value, "rectime_avatar_preference_$userId")
}
