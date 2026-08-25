package com.rectime.mobile.feature.notifications

import kotlin.test.Test
import kotlin.test.assertEquals

class NotificationNavigationPayloadTest {
    @Test
    fun extractsSupportedBackendPayloadAndDropsFirebaseMetadata() {
        val payload = NotificationNavigationPayload.extract(
            mapOf(
                "type" to "event_reminder",
                "eventId" to "12",
                "google.message_id" to "message-id",
                "unexpected" to "value",
            ),
        )

        assertEquals(
            mapOf(
                "type" to "event_reminder",
                "eventId" to "12",
            ),
            payload,
        )
    }

    @Test
    fun backendEventReminderNavigatesToEventDetail() {
        val target = NotificationNavigationPayload.parse(
            mapOf(
                "type" to "event_reminder",
                "eventId" to "12",
            ),
        )

        assertEquals(NotificationNavigationTarget.EventDetail(12), target)
    }

    @Test
    fun issueContractScheduleReminderNavigatesToEventDetail() {
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
    fun backendTypeTakesPriorityDuringContractMigration() {
        val target = NotificationNavigationPayload.parse(
            mapOf(
                "type" to "event_reminder",
                "notificationType" to "manual",
                "eventId" to "5",
                "notificationId" to "3",
            ),
        )

        assertEquals(NotificationNavigationTarget.EventDetail(5), target)
    }

    @Test
    fun manualHomeNavigatesHomeEvenWithNotificationId() {
        val target = NotificationNavigationPayload.parse(
            mapOf(
                "type" to "manual",
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
                "type" to "manual",
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
