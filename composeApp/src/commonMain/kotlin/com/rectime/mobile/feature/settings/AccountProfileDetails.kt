package com.rectime.mobile.feature.settings

import com.rectime.mobile.feature.auth.AuthUser

internal data class AccountProfileDetail(val label: String, val value: String)

/** 1要素が画面上の1行。学籍番号と所属クラスは横に並べ、担当クラスは別の行にする。 */
internal fun accountProfileDetailRows(user: AuthUser): List<List<AccountProfileDetail>> = buildList {
    if (user.isStudent) {
        val student = buildList {
            user.studentIdNumber?.trim()?.takeIf { it.isNotEmpty() }?.let { add(AccountProfileDetail("学籍番号", it)) }
            user.classCode?.trim()?.takeIf { it.isNotEmpty() }?.let { add(AccountProfileDetail("所属クラス", it)) }
        }
        if (student.isNotEmpty()) add(student)
    }
    if (user.isTeacher) {
        val codes = user.teacher?.classRooms.orEmpty().map { it.classCode.trim() }
            .filter { it.isNotEmpty() }.distinct()
        if (codes.isNotEmpty()) add(listOf(AccountProfileDetail("担当クラス", codes.joinToString("、"))))
    }
}
