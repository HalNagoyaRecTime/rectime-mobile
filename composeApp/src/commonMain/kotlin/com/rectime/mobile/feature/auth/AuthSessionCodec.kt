package com.rectime.mobile.feature.auth

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val SESSION_VERSION = 1

@OptIn(ExperimentalSerializationApi::class)
private val sessionJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

@Serializable
private data class StoredAuthSession(
    val version: Int,
    val accessToken: String,
    val refreshTokenId: String,
    val expiresIn: Long,
    val user: StoredAuthUser,
)

@Serializable
private data class StoredAuthUser(
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
)

fun encodeAuthSession(session: AuthSession): String = sessionJson.encodeToString(
    StoredAuthSession(
        version = SESSION_VERSION,
        accessToken = session.accessToken,
        refreshTokenId = session.refreshTokenId,
        expiresIn = session.expiresIn,
        user = StoredAuthUser(
            id = session.user.id,
            email = session.user.email,
            displayName = session.user.displayName,
            avatarUrl = session.user.avatarUrl,
            avatarUpdatedAt = session.user.avatarUpdatedAt,
            studentIdNumber = session.user.studentIdNumber,
            classRoomName = session.user.classRoomName,
            teamId = session.user.teamId,
            classCode = session.user.classCode,
            isStudent = session.user.isStudent,
            isStaff = session.user.isStaff,
            isTeacher = session.user.isTeacher,
            teacher = session.user.teacher.takeIf { session.user.isTeacher },
        ),
    ),
)

fun decodeAuthSession(value: String): AuthSession? = runCatching {
    val stored = sessionJson.decodeFromString<StoredAuthSession>(value)
    if (stored.version != SESSION_VERSION) return@runCatching null
    AuthSession(
        accessToken = stored.accessToken,
        refreshTokenId = stored.refreshTokenId,
        expiresIn = stored.expiresIn,
        user = AuthUser(
            id = stored.user.id,
            email = stored.user.email,
            displayName = stored.user.displayName,
            avatarUrl = stored.user.avatarUrl,
            avatarUpdatedAt = stored.user.avatarUpdatedAt,
            studentIdNumber = stored.user.studentIdNumber,
            classRoomName = stored.user.classRoomName,
            teamId = stored.user.teamId,
            classCode = stored.user.classCode,
            isStudent = stored.user.isStudent,
            isStaff = stored.user.isStaff,
            isTeacher = stored.user.isTeacher,
            teacher = stored.user.teacher.takeIf { stored.user.isTeacher },
        ),
    ).takeIf { session ->
        session.accessToken.isNotBlank() && session.refreshTokenId.isNotBlank()
    }
}.getOrNull()

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
