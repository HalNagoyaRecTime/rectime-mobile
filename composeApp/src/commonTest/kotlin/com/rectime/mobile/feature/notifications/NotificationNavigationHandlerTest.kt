package com.rectime.mobile.feature.notifications

import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals

class NotificationNavigationHandlerTest {

    @Test
    fun handleEmitsParsedTargetForBackendPayload() = runTest {
        NotificationNavigationHandler.handle(
            mapOf(
                "type" to "event_reminder",
                "eventId" to "12",
                "google.message_id" to "message-id",
            ),
        )

        val target = withTimeout(TIMEOUT_MILLIS) {
            NotificationNavigationHandler.targets.take(1).toList().single()
        }

        assertEquals(NotificationNavigationTarget.EventDetail(12), target)
    }

    @Test
    fun handleBuffersTargetsInOrderUntilCollected() = runTest {
        NotificationNavigationHandler.handle(
            mapOf("type" to "manual", "notificationId" to "3"),
        )
        NotificationNavigationHandler.handle(
            mapOf("notificationType" to "schedule_update", "eventId" to "7"),
        )

        val targets = withTimeout(TIMEOUT_MILLIS) {
            NotificationNavigationHandler.targets.take(2).toList()
        }

        assertEquals(
            listOf(
                NotificationNavigationTarget.NotificationDetail(3),
                NotificationNavigationTarget.EventDetail(7),
            ),
            targets,
        )
    }

    @Test
    fun handleFallsBackHomeForUnsupportedPayload() = runTest {
        NotificationNavigationHandler.handle(mapOf("type" to "unknown", "eventId" to "12"))

        val target = withTimeout(TIMEOUT_MILLIS) {
            NotificationNavigationHandler.targets.take(1).toList().single()
        }

        assertEquals(NotificationNavigationTarget.Home, target)
    }

    private companion object {
        const val TIMEOUT_MILLIS = 5_000L
    }
}
