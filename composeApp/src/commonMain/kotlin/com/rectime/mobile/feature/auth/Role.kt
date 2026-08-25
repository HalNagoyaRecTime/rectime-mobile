package com.rectime.mobile.feature.auth

// バックエンド（rectime-api）の is_student/is_staff/is_teacher に対応する。
// staff と teacher は排他ではない（同一アカウントが両方を持ちうる）ため、
// 両方 true の場合は teacher を優先する。
enum class Role {
    Student,
    Staff,
    Teacher;

    companion object {
        fun fromCategories(isStudent: Boolean, isStaff: Boolean, isTeacher: Boolean): Role? = when {
            isTeacher -> Teacher
            isStaff -> Staff
            isStudent -> Student
            else -> null
        }

        // ローカル永続化（AuthSessionCodec）に保存した enum 名を復元する用途。
        fun fromStoredName(name: String?): Role? = entries.firstOrNull { it.name == name }
    }
}
