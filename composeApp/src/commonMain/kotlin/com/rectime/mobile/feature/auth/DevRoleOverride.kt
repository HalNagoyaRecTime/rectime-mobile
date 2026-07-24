package com.rectime.mobile.feature.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.rectime.mobile.core.config.isDebugBuild

/**
 * Backend doesn't send a role yet, so debug builds can force one here to exercise
 * role-gated UI ahead of the real account/student-teacher linkage landing.
 */
object DevRoleOverride {
    var role: Role? by mutableStateOf(null)
}

fun AuthUser.effectiveRole(): Role? =
    if (isDebugBuild) DevRoleOverride.role ?: role else role
