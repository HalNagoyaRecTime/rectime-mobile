package com.rectime.mobile.core.image

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

private const val SHARED_AVATAR_URL = "https://example.com/api/v1/auth/me/photo"

class AuthenticatedImageCacheKeyTest {

    @Test
    fun `the same url gets a different key for each user`() {
        val first = authenticatedImageCacheKey(SHARED_AVATAR_URL, "6")
        val second = authenticatedImageCacheKey(SHARED_AVATAR_URL, "7")

        assertNotEquals(first, second)
    }

    @Test
    fun `the same user and url get a stable key`() {
        assertEquals(
            authenticatedImageCacheKey(SHARED_AVATAR_URL, "6"),
            authenticatedImageCacheKey(SHARED_AVATAR_URL, "6"),
        )
    }

    @Test
    fun `different urls for the same user get different keys`() {
        assertNotEquals(
            authenticatedImageCacheKey(SHARED_AVATAR_URL, "6"),
            authenticatedImageCacheKey("$SHARED_AVATAR_URL?v=2026-08-30", "6"),
        )
    }

    @Test
    fun `no key is produced without a user id`() {
        assertNull(authenticatedImageCacheKey(SHARED_AVATAR_URL, null))
        assertNull(authenticatedImageCacheKey(SHARED_AVATAR_URL, ""))
        assertNull(authenticatedImageCacheKey(SHARED_AVATAR_URL, "   "))
    }

    @Test
    fun `the key contains the user id`() {
        val key = authenticatedImageCacheKey(SHARED_AVATAR_URL, "6")

        assertEquals("6|$SHARED_AVATAR_URL", key)
    }
}
