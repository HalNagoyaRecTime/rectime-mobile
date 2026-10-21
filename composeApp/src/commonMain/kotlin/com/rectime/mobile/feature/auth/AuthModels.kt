package com.rectime.mobile.feature.auth

import kotlinx.serialization.Serializable

data class AuthUser(
    val id: String,
    val email: String,
    val displayName: String,
    val avatarUrl: String? = null,
    val avatarUpdatedAt: String? = null,
    val studentIdNumber: String? = null,
    val classRoomName: String? = null,
    val teamId: Int? = null,
    val classCode: String? = null,
    val isStudent: Boolean = false,
    val isStaff: Boolean = false,
    val isTeacher: Boolean = false,
    val teacher: AuthTeacher? = null,
    // 学生メールでログインしたのに学生情報が見つからなかった状態。
    val studentProfileMissing: Boolean = false,
) {
    val role: Role?
        get() = Role.fromCategories(isStudent, isStaff, isTeacher)
}

@Serializable
data class AuthTeacher(
    val teacherId: Int,
    val classRooms: List<AuthTeacherClassRoom> = emptyList(),
)

@Serializable
data class AuthTeacherClassRoom(
    val classRoomId: Int,
    val classCode: String,
    val classRoomName: String,
)

data class AuthSession(
    val accessToken: String,
    val refreshTokenId: String,
    val expiresIn: Long,
    val user: AuthUser,
)

data class PendingAuth(
    val state: String,
    val codeVerifier: String,
)

data class AuthUiState(
    val isRestoringSession: Boolean = false,
    val isLoading: Boolean = false,
    val session: AuthSession? = null,
    val pendingAuth: PendingAuth? = null,
    val message: String = "",
    val error: String? = null,
    // ログイン直後と、アプリ起動後に最初にユーザー情報を取得したときだけtrueにする。
    // 前面復帰では立てず、ログアウトで初期化される。
    val showStudentProfileMissingNotice: Boolean = false,
)
