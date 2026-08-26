package com.rectime.mobile.feature.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RoleTest {

    @Test
    fun fromCategoriesMapsEachSingleCategory() {
        assertEquals(
            Role.Student,
            Role.fromCategories(isStudent = true, isStaff = false, isTeacher = false),
        )
        assertEquals(
            Role.Staff,
            Role.fromCategories(isStudent = false, isStaff = true, isTeacher = false),
        )
        assertEquals(
            Role.Teacher,
            Role.fromCategories(isStudent = false, isStaff = false, isTeacher = true),
        )
    }

    @Test
    fun fromCategoriesPrefersTeacherOverStaff() {
        assertEquals(
            Role.Teacher,
            Role.fromCategories(isStudent = false, isStaff = true, isTeacher = true),
        )
    }

    @Test
    fun fromCategoriesPrefersStaffOverStudent() {
        assertEquals(
            Role.Staff,
            Role.fromCategories(isStudent = true, isStaff = true, isTeacher = false),
        )
    }

    @Test
    fun fromCategoriesReturnsNullWhenNoCategoryIsSet() {
        assertNull(Role.fromCategories(isStudent = false, isStaff = false, isTeacher = false))
    }

    @Test
    fun fromStoredNameRestoresEveryEnumName() {
        Role.entries.forEach { role ->
            assertEquals(role, Role.fromStoredName(role.name))
        }
    }

    @Test
    fun fromStoredNameReturnsNullForUnknownOrMissingValue() {
        assertNull(Role.fromStoredName(null))
        assertNull(Role.fromStoredName(""))
        assertNull(Role.fromStoredName("student"))
        assertNull(Role.fromStoredName("Admin"))
    }
}
