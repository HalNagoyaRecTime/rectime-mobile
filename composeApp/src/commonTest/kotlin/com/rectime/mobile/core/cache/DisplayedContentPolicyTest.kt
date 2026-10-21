package com.rectime.mobile.core.cache

import com.rectime.mobile.core.network.HttpStatusException
import io.ktor.http.HttpStatusCode
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DisplayedContentPolicyTest {
    @BeforeTest
    fun resetGeneration() {
        CacheGeneration.resetForTest()
    }

    @Test
    fun communicationFailureKeepsContentFromSameSession() {
        val content = CacheRequestGeneration()
        assertTrue(CacheRequestGeneration().canRetainDisplayedContent(IllegalStateException("通信失敗"), content))
    }

    @Test
    fun unconfirmedUnauthorizedKeepsContentFromSameSession() {
        val content = CacheRequestGeneration()
        assertTrue(CacheRequestGeneration().canRetainDisplayedContent(HttpStatusException(HttpStatusCode.Unauthorized), content))
    }

    @Test
    fun forbiddenAndNotFoundDoNotKeepContent() {
        val content = CacheRequestGeneration()
        for (status in listOf(HttpStatusCode.Forbidden, HttpStatusCode.NotFound)) {
            assertFalse(CacheRequestGeneration().canRetainDisplayedContent(HttpStatusException(status), content))
        }
    }

    @Test
    fun explicitDeletionCodeDoesNotKeepContent() {
        val content = CacheRequestGeneration()
        assertFalse(CacheRequestGeneration().canRetainDisplayedContent(
            HttpStatusException(HttpStatusCode.BadRequest, code = "NOTIFICATION_NOT_FOUND"), content,
        ))
    }

    @Test
    fun freshRequestCannotKeepContentFromPreviousSession() {
        val oldContent = CacheRequestGeneration()
        CacheGeneration.bump()
        assertFalse(CacheRequestGeneration().canRetainDisplayedContent(IllegalStateException("通信失敗"), oldContent))
    }

    @Test
    fun oldRequestCannotKeepContentAfterLogout() {
        val oldRequest = CacheRequestGeneration()
        CacheGeneration.bump()
        assertFalse(oldRequest.canRetainDisplayedContent(IllegalStateException("通信失敗"), CacheRequestGeneration()))
    }
}
