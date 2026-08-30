package com.rectime.mobile.feature.auth

import androidx.compose.runtime.staticCompositionLocalOf
import com.rectime.mobile.core.model.UserProfile

val LocalUserProfile = staticCompositionLocalOf<UserProfile?> { null }

fun AuthUser.toUserProfile(): UserProfile = UserProfile(id = id)
