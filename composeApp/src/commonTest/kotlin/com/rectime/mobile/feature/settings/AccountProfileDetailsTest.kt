package com.rectime.mobile.feature.settings

import com.rectime.mobile.feature.auth.AuthUser
import com.rectime.mobile.feature.auth.AuthTeacher
import com.rectime.mobile.feature.auth.AuthTeacherClassRoom
import com.rectime.mobile.feature.auth.AuthSession
import com.rectime.mobile.feature.auth.encodeAuthSession
import com.rectime.mobile.feature.auth.decodeAuthSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AccountProfileDetailsTest {
    private val teacher = AuthTeacher(12, listOf(
        AuthTeacherClassRoom(3, "1-A", "1年A組"),
        AuthTeacherClassRoom(7, "2-B", "2年B組"),
    ))
    private fun user() = AuthUser("1", "test@example.com", "テスト", studentIdNumber = "12345", classCode = "1-A")

    @Test
    fun falseFlagsHideEvenPopulatedValues() {
        assertTrue(accountProfileDetails(user().copy(teacher = teacher)).isEmpty())
    }

    @Test
    fun studentAndTeacherFlagsRemainIndependent() {
        assertEquals(listOf(
            AccountProfileDetail("学籍番号", "12345"),
            AccountProfileDetail("所属クラス", "1-A"),
            AccountProfileDetail("担当クラス", "1-A、2-B"),
        ), accountProfileDetails(user().copy(isStudent = true, isTeacher = true, teacher = teacher)))
    }

    @Test
    fun teacherUsesOneDetailForAllClasses() {
        assertEquals(listOf(AccountProfileDetail("担当クラス", "1-A、2-B")),
            accountProfileDetails(user().copy(isTeacher = true, teacher = teacher)))
    }

    @Test
    fun missingValuesAndEmptyClassesHaveNoRows() {
        assertTrue(accountProfileDetails(user().copy(isStudent = true, studentIdNumber = " ", classCode = null,
            isTeacher = true, teacher = AuthTeacher(12))).isEmpty())
    }

    @Test
    fun categoriesAndTeacherSurviveSessionRestore() {
        val session = AuthSession("token", "refresh", 3600,
            user().copy(isStudent = true, isTeacher = true, teacher = teacher))
        assertEquals(session, decodeAuthSession(encodeAuthSession(session)))
    }

    @Test
    fun previousProfileSchemaStillRestoresSession() {
        val original = AuthSession("token", "refresh", 3600, user())
        val fields = encodeAuthSession(original).split(".").take(14).toMutableList()
        fields[11] = "cHJvZmlsZS12Mg"
        assertEquals(original, decodeAuthSession(fields.joinToString(".")))
    }
}
