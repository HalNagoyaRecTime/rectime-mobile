package com.rectime.mobile.feature.auth

import androidx.compose.runtime.staticCompositionLocalOf
import com.rectime.mobile.core.model.UserProfile

val LocalUserProfile = staticCompositionLocalOf<UserProfile?> { null }

fun AuthUser.toUserProfile(): UserProfile {
    val display = displayName.ifBlank { email.ifBlank { "User" } }
    return UserProfile(
        id = id,
        name = display,
        initials = display.take(2).uppercase(),
        imageUrl = avatarUrl,
        department = null,
        studentId = null,
    )
}
