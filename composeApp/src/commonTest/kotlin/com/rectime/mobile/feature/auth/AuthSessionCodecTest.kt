package com.rectime.mobile.feature.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AuthSessionCodecTest {
    @Test
    fun decodeRejectsSessionWithoutRequiredTokens() {
        val sessionWithoutAccessToken = createTestSession(accessToken = "")
        val sessionWithoutRefreshToken = createTestSession(refreshTokenId = "")

        assertNull(decodeAuthSession(encodeAuthSession(sessionWithoutAccessToken)))
        assertNull(decodeAuthSession(encodeAuthSession(sessionWithoutRefreshToken)))
    }

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

    @Test
    fun decodeRestoresLegacyNinePartSessionWithRoleOnly() {
        val legacyEncoded = listOf(
            "token123", "refresh456", "3600",
            "6", "test@example.com", "テスト太郎",
            "https://example.com/avatar.png", "2026-07-31",
            "Student",
        ).joinToString(".") { it.encodeToByteArray().toBase64Url() }

        val decoded = decodeAuthSession(legacyEncoded)

        assertEquals("6", decoded?.user?.id)
        assertEquals(Role.Student, decoded?.user?.role)
        assertEquals(null, decoded?.user?.studentIdNumber)
        assertEquals(null, decoded?.user?.classRoomName)
    }
    @Test
    fun decodeReturnsNullForUnsupportedPartCount() {
        val sevenParts = listOf("a", "b", "3600", "6", "e", "f", "g")
            .joinToString(".") { it.encodeToByteArray().toBase64Url() }

        assertNull(decodeAuthSession(sevenParts))
        assertNull(decodeAuthSession(""))
        assertNull(decodeAuthSession("garbage"))
    }

    @Test
    fun decodeReturnsNullWhenExpiresInIsNotANumber() {
        val broken = listOf("token123", "refresh456", "not-a-number", "6", "e", "f")
            .joinToString(".") { it.encodeToByteArray().toBase64Url() }

        assertNull(decodeAuthSession(broken))
    }

    @Test
    fun decodeRestoresLegacySixPartSessionWithoutAvatar() {
        val legacyEncoded = listOf("token123", "refresh456", "3600", "6", "test@example.com", "テスト太郎")
            .joinToString(".") { it.encodeToByteArray().toBase64Url() }

        val decoded = decodeAuthSession(legacyEncoded)

        assertEquals("token123", decoded?.accessToken)
        assertEquals(3600L, decoded?.expiresIn)
        assertEquals("テスト太郎", decoded?.user?.displayName)
        assertNull(decoded?.user?.avatarUrl)
        assertNull(decoded?.user?.role)
    }

    @Test
    fun pendingAuthSurvivesEncodeDecodeRoundTrip() {
        val original = PendingAuth(state = "state-abc_123", codeVerifier = "verifier-xyz_456")

        assertEquals(original, decodePendingAuth(encodePendingAuth(original)))
    }

    @Test
    fun decodePendingAuthReturnsNullForWrongPartCount() {
        val oneValue = "state-abc".encodeToByteArray().toBase64Url()
        val threeValues = listOf("a", "b", "c").joinToString(".") { it.encodeToByteArray().toBase64Url() }

        assertNull(decodePendingAuth(oneValue))
        assertNull(decodePendingAuth(threeValues))
        assertNull(decodePendingAuth(""))
    }
}

// Test fixture only; no account or token from this helper is included in the app binary.
private fun createTestSession(
    accessToken: String = "token123",
    refreshTokenId: String = "refresh456",
) = AuthSession(
    accessToken = accessToken,
    refreshTokenId = refreshTokenId,
    expiresIn = 3600L,
    user = AuthUser(
        id = "6",
        email = "test@example.com",
        displayName = "テスト太郎",
    ),
)
