package com.rectime.mobile.feature.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AuthSessionCodecTest {
    @Test
    fun decodeRejectsSessionWithoutRequiredTokens() {
        assertNull(decodeAuthSession(encodeAuthSession(createTestSession(accessToken = ""))))
        assertNull(decodeAuthSession(encodeAuthSession(createTestSession(refreshTokenId = ""))))
    }

    @Test
    fun sessionJsonRestoresAllFields() {
        val teacher = AuthTeacher(
            teacherId = 12,
            classRooms = listOf(AuthTeacherClassRoom(3, "1-A", "1年A組")),
        )
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
                teamId = 34,
                classCode = "IH22A",
                isStudent = true,
                isStaff = true,
                isTeacher = true,
                teacher = teacher,
            ),
        )

        assertEquals(original, decodeAuthSession(encodeAuthSession(original)))
    }

    @Test
    fun missingOptionalFieldsUseDefaults() {
        val encoded = """
            {
              "version": 1,
              "accessToken": "token123",
              "refreshTokenId": "refresh456",
              "expiresIn": 3600,
              "user": {
                "id": "6",
                "email": "test@example.com",
                "displayName": "テスト太郎"
              }
            }
        """.trimIndent()

        val user = decodeAuthSession(encoded)?.user
        assertNull(user?.role)
        assertFalse(user?.isStudent ?: true)
        assertFalse(user?.isStaff ?: true)
        assertFalse(user?.isTeacher ?: true)
        assertNull(user?.teacher)
    }

    @Test
    fun unknownFieldsAreIgnoredForForwardCompatibleAdditions() {
        val encoded = """
            {
              "version": 1,
              "futureSessionField": "ignored",
              "accessToken": "token123",
              "refreshTokenId": "refresh456",
              "expiresIn": 3600,
              "user": {
                "id": "6",
                "email": "test@example.com",
                "displayName": "テスト太郎",
                "isStudent": true,
                "futureUserField": 123
              }
            }
        """.trimIndent()

        val decoded = decodeAuthSession(encoded)
        assertEquals("6", decoded?.user?.id)
        assertTrue(decoded?.user?.isStudent == true)
        assertEquals(Role.Student, decoded?.user?.role)
    }

    @Test
    fun unsupportedVersionAndLegacyDotFormatAreRejected() {
        val unsupported = """
            {
              "version": 2,
              "accessToken": "token123",
              "refreshTokenId": "refresh456",
              "expiresIn": 3600,
              "user": {"id":"6","email":"e","displayName":"f"}
            }
        """.trimIndent()
        val legacy = listOf("token123", "refresh456", "3600", "6", "e", "f")
            .joinToString(".") { it.encodeToByteArray().toBase64Url() }

        assertNull(decodeAuthSession(unsupported))
        assertNull(decodeAuthSession(legacy))
        assertNull(decodeAuthSession("garbage"))
    }

    @Test
    fun teacherDetailsAreDroppedWhenTeacherFlagIsFalse() {
        val encoded = """
            {
              "version": 1,
              "accessToken": "token123",
              "refreshTokenId": "refresh456",
              "expiresIn": 3600,
              "user": {
                "id": "6",
                "email": "test@example.com",
                "displayName": "テスト太郎",
                "isTeacher": false,
                "teacher": {"teacherId":12,"classRooms":[]}
              }
            }
        """.trimIndent()

        assertNull(decodeAuthSession(encoded)?.user?.teacher)
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
