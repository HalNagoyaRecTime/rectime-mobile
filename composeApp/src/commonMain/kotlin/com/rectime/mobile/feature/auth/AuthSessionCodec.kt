package com.rectime.mobile.feature.auth

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
    if (parts.size != 6 && parts.size != 8 && parts.size != 9) return null

    return runCatching {
        val avatarUrl = if (parts.size >= 8) {
            val s = parts[6].decodeBase64UrlToString()
            if (s.isEmpty()) null else s
        } else null
        val avatarUpdatedAt = if (parts.size >= 8) {
            val s = parts[7].decodeBase64UrlToString()
            if (s.isEmpty()) null else s
        } else null
        val studentIdNumber = if (parts.size >= 10) {
            val s = parts[8].decodeBase64UrlToString()
            if (s.isEmpty()) null else s
        } else null
        val classRoomName = if (parts.size >= 10) {
            val s = parts[9].decodeBase64UrlToString()
            if (s.isEmpty()) null else s
        } else null
        val role = if (parts.size == 11) {
            Role.fromStoredName(parts[10].decodeBase64UrlToString().ifEmpty { null })
        } else null

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
                role = role,
            ),
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
