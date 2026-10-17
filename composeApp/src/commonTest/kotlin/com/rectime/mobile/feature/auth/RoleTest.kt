package com.rectime.mobile.feature.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RoleTest {
    @Test
    fun fromCategoriesMapsEachSingleCategory() {
        assertEquals(Role.Student, Role.fromCategories(true, false, false))
        assertEquals(Role.Staff, Role.fromCategories(false, true, false))
        assertEquals(Role.Teacher, Role.fromCategories(false, false, true))
    }

    @Test
    fun fromCategoriesUsesTeacherThenStaffThenStudentPriority() {
        assertEquals(Role.Teacher, Role.fromCategories(true, true, true))
        assertEquals(Role.Staff, Role.fromCategories(true, true, false))
        assertEquals(Role.Student, Role.fromCategories(true, false, false))
        assertNull(Role.fromCategories(false, false, false))
    }

    @Test
    fun authUserRoleIsDerivedFromCategoryFlags() {
        val base = AuthUser("1", "test@example.com", "Test")
        assertNull(base.role)
        assertEquals(Role.Student, base.copy(isStudent = true).role)
        assertEquals(Role.Staff, base.copy(isStudent = true, isStaff = true).role)
        assertEquals(Role.Teacher, base.copy(isStudent = true, isStaff = true, isTeacher = true).role)
    }
}
