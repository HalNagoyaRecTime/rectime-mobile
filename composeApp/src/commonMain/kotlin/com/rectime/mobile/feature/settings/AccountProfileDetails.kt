package com.rectime.mobile.feature.settings

import com.rectime.mobile.feature.auth.AuthUser

internal data class AccountProfileDetail(val label: String, val value: String)

internal fun accountProfileDetails(user: AuthUser): List<AccountProfileDetail> = buildList {
    if (user.isStudent) {
        user.studentIdNumber?.trim()?.takeIf { it.isNotEmpty() }?.let { add(AccountProfileDetail("学籍番号", it)) }
        user.classCode?.trim()?.takeIf { it.isNotEmpty() }?.let { add(AccountProfileDetail("所属クラス", it)) }
    }
    if (user.isTeacher) {
        val codes = user.teacher?.classRooms.orEmpty().map { it.classCode.trim() }
            .filter { it.isNotEmpty() }.distinct()
        if (codes.isNotEmpty()) add(AccountProfileDetail("担当クラス", codes.joinToString("、")))
    }
}
