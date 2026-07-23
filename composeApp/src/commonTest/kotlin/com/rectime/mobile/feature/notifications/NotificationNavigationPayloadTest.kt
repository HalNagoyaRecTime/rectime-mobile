package com.rectime.mobile.feature.notifications

import kotlin.test.Test
import kotlin.test.assertEquals

class NotificationNavigationPayloadTest {
    @Test
    fun scheduleReminderNavigatesToEventDetail() {
        val target = NotificationNavigationPayload.parse(
            mapOf(
                "notificationType" to "schedule_reminder",
                "eventId" to "12",
            ),
        )

        assertEquals(NotificationNavigationTarget.EventDetail(12), target)
    }

    @Test
    fun scheduleUpdateNavigatesToEventDetail() {
        val target = NotificationNavigationPayload.parse(
            mapOf(
                "notificationType" to "schedule_update",
                "eventId" to "7",
            ),
        )

        assertEquals(NotificationNavigationTarget.EventDetail(7), target)
    }

    @Test
    fun manualHomeNavigatesHomeEvenWithNotificationId() {
        val target = NotificationNavigationPayload.parse(
            mapOf(
                "notificationType" to "manual",
                "navigationType" to "home",
                "notificationId" to "3",
            ),
        )

        assertEquals(NotificationNavigationTarget.Home, target)
    }

    @Test
    fun manualNotificationNavigatesToNotificationDetail() {
        val target = NotificationNavigationPayload.parse(
            mapOf(
                "notificationType" to "manual",
                "notificationId" to "3",
            ),
        )

        assertEquals(NotificationNavigationTarget.NotificationDetail(3), target)
    }

    @Test
    fun missingOrInvalidRequiredIdFallsBackHome() {
        val invalidPayloads = listOf(
            mapOf("notificationType" to "schedule_reminder"),
            mapOf("notificationType" to "schedule_update", "eventId" to "invalid"),
            mapOf("notificationType" to "manual", "notificationId" to "0"),
            mapOf("notificationType" to "unknown", "eventId" to "1"),
            emptyMap(),
        )

        invalidPayloads.forEach { payload ->
            assertEquals(
                NotificationNavigationTarget.Home,
                NotificationNavigationPayload.parse(payload),
            )
        }
    }
}
