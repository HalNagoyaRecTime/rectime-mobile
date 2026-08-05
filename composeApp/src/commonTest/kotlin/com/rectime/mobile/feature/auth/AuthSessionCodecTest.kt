package com.rectime.mobile.feature.auth

import kotlin.test.Test
import kotlin.test.assertEquals

class AuthSessionCodecTest {
    @Test
    fun decodeRestoresAllFieldsAfterEncode() {
        val original = AuthSession(
            accessToken = "token123",
            refreshTokenId = "refresh456",
            expiresIn = 3600L,
            user = AuthUser(
                id = "6",
                email = "test@example.com",
                displayName = "テスト太郎",
                avatarUrl = "https://example.com/avatar.png",
                avatarUpdatedAt = "2026-07-31",
                studentIdNumber = "55024",
                classRoomName = "1年Aクラス",
                role = Role.Student,
            ),
        )

        val encoded = encodeAuthSession(original)
        val decoded = decodeAuthSession(encoded)

        assertEquals(original, decoded)
    }

    @Test
    fun decodeRestoresNullableFieldsAsNullWhenEmpty() {
        val original = AuthSession(
            accessToken = "token123",
            refreshTokenId = "refresh456",
            expiresIn = 3600L,
            user = AuthUser(
                id = "6",
                email = "test@example.com",
                displayName = "テスト太郎",
                avatarUrl = null,
                avatarUpdatedAt = null,
                studentIdNumber = null,
                classRoomName = null,
                role = null,
            ),
        )

        val encoded = encodeAuthSession(original)
        val decoded = decodeAuthSession(encoded)

        assertEquals(original, decoded)
    }

    @Test
    fun decodeRestoresTeacherRoleCorrectly() {
        val original = AuthSession(
            accessToken = "token123",
            refreshTokenId = "refresh456",
            expiresIn = 3600L,
            user = AuthUser(
                id = "6",
                email = "teacher@example.com",
                displayName = "先生太郎",
                studentIdNumber = null,
                classRoomName = null,
                role = Role.Teacher,
            ),
        )

        val encoded = encodeAuthSession(original)
        val decoded = decodeAuthSession(encoded)

        assertEquals(original, decoded)
    }
}