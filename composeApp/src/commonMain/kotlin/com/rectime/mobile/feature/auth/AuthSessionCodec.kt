package com.rectime.mobile.feature.auth

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val PROFILE_SCHEMA = "profile-v3"
private val accountDetailsJson = Json { ignoreUnknownKeys = true }

fun encodeAuthSession(session: AuthSession): String =
    listOf(
        session.accessToken,
        session.refreshTokenId,
        session.expiresIn.toString(),
        session.user.id,
        session.user.email,
        session.user.displayName,
        session.user.avatarUrl ?: "",
        session.user.avatarUpdatedAt ?: "",
        session.user.studentIdNumber ?: "",
        session.user.classRoomName ?: "",
        session.user.role?.name ?: "",
        PROFILE_SCHEMA,
        session.user.classCode ?: "",
        session.user.teamId?.toString() ?: "",
        accountDetailsJson.encodeToString(
            StoredAccountDetails(session.user.isStudent, session.user.isTeacher, session.user.teacher),
        ),
    ).joinToString(separator = ".") { it.encodeToByteArray().toBase64Url() }

fun encodePendingAuth(pending: PendingAuth): String =
    listOf(pending.state, pending.codeVerifier)
        .joinToString(separator = ".") { it.encodeToByteArray().toBase64Url() }

fun decodePendingAuth(value: String): PendingAuth? {
    val parts = value.split(".")
    if (parts.size != 2) return null
    return runCatching {
        PendingAuth(
            state = parts[0].decodeBase64UrlToString(),
            codeVerifier = parts[1].decodeBase64UrlToString(),
        )
    }.getOrNull()
}

fun decodeAuthSession(value: String): AuthSession? {
    val parts = value.split(".")
    if (parts.size !in setOf(6, 8, 9, 11, 12, 14, 15)) return null

    return runCatching {
        if (parts.size == 14 && parts[11].decodeBase64UrlToString() != "profile-v2") return null
        if (parts.size == 15 && parts[11].decodeBase64UrlToString() != PROFILE_SCHEMA) return null
        val avatarUrl = if (parts.size >= 8) {
            val s = parts[6].decodeBase64UrlToString()
            if (s.isEmpty()) null else s
        } else null
        val avatarUpdatedAt = if (parts.size >= 8) {
            val s = parts[7].decodeBase64UrlToString()
            if (s.isEmpty()) null else s
        } else null
        val studentIdNumber = if (parts.size >= 11) {
            val s = parts[8].decodeBase64UrlToString()
            if (s.isEmpty()) null else s
        } else null
        val classRoomName = if (parts.size >= 11) {
            val s = parts[9].decodeBase64UrlToString()
            if (s.isEmpty()) null else s
        } else null
        // developとdevelop-v2の旧12要素形式は末尾がclassCode/teamIdで衝突する。
        // 数字はどちらか判定できないため、認証情報を維持して/auth/meで再取得する。
        val classCode = when (parts.size) {
            14, 15 -> parts[12].decodeBase64UrlToString().ifEmpty { null }
            12 -> parts[11].decodeBase64UrlToString()
                .takeIf { it.isNotEmpty() && it.toIntOrNull() == null }
            else -> null
        }
        val teamId = if (parts.size >= 14) {
            parts[13].decodeBase64UrlToString().ifEmpty { null }?.toInt()
        } else null
        val role = when (parts.size) {
            9 -> Role.fromStoredName(parts[8].decodeBase64UrlToString().ifEmpty { null })
            11, 12, 14, 15 -> Role.fromStoredName(parts[10].decodeBase64UrlToString().ifEmpty { null })
            else -> null
        }

        val accountDetails = if (parts.size == 15) {
            accountDetailsJson.decodeFromString<StoredAccountDetails>(parts[14].decodeBase64UrlToString())
        } else StoredAccountDetails(role == Role.Student, role == Role.Teacher)

        AuthSession(
            accessToken = parts[0].decodeBase64UrlToString(),
            refreshTokenId = parts[1].decodeBase64UrlToString(),
            expiresIn = parts[2].decodeBase64UrlToString().toLong(),
            user = AuthUser(
                id = parts[3].decodeBase64UrlToString(),
                email = parts[4].decodeBase64UrlToString(),
                displayName = parts[5].decodeBase64UrlToString(),
                avatarUrl = avatarUrl,
                avatarUpdatedAt = avatarUpdatedAt,
                studentIdNumber = studentIdNumber,
                classRoomName = classRoomName,
                classCode = classCode,
                teamId = teamId,
                role = role,
                isStudent = accountDetails.isStudent,
                isTeacher = accountDetails.isTeacher,
                teacher = accountDetails.teacher.takeIf { accountDetails.isTeacher },
            ),
        ).takeIf { session ->
            session.accessToken.isNotBlank() && session.refreshTokenId.isNotBlank()
        }
    }.getOrNull()
}

internal fun String.decodeBase64UrlToString(): String {
    val normalized = replace('-', '+').replace('_', '/')
    val padded = normalized + "=".repeat((4 - normalized.length % 4) % 4)
    val bytes = decodeBase64(padded)
    return bytes.decodeToString()
}

private fun decodeBase64(value: String): ByteArray {
    val table = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    val clean = value.trimEnd('=')
    val output = ArrayList<Byte>((clean.length * 3) / 4)
    var buffer = 0
    var bits = 0

    for (char in clean) {
        val index = table.indexOf(char)
        if (index < 0) continue
        buffer = (buffer shl 6) or index
        bits += 6
        if (bits >= 8) {
            bits -= 8
            output += ((buffer shr bits) and 0xff).toByte()
        }
    }

    return output.toByteArray()
}
