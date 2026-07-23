package com.rectime.mobile.feature.notifications

import kotlin.test.Test
import kotlin.test.assertEquals

class IncomingPushNotificationTest {
    @Test
    fun notificationPayloadTakesPriorityOverDataPayload() {
        val notification = IncomingPushNotification.from(
            notificationTitle = "通知タイトル",
            notificationBody = "通知本文",
            data = mapOf(
                "title" to "データタイトル",
                "body" to "データ本文",
                "importance" to "2",
            ),
        )

        assertEquals("通知タイトル", notification.title)
        assertEquals("通知本文", notification.body)
        assertEquals(2, notification.importance)
    }

    @Test
    fun dataPayloadIsUsedWhenNotificationPayloadIsMissing() {
        val notification = IncomingPushNotification.from(
            notificationTitle = null,
            notificationBody = null,
            data = mapOf(
                "title" to "集合のお知らせ",
                "body" to "集合場所へ移動してください。",
                "importance" to "2",
            ),
        )

        assertEquals("集合のお知らせ", notification.title)
        assertEquals("集合場所へ移動してください。", notification.body)
        assertEquals(2, notification.importance)
    }

    @Test
    fun invalidOrMissingValuesUseMvpDefaults() {
        val notification = IncomingPushNotification.from(
            notificationTitle = "",
            notificationBody = " ",
            data = mapOf("importance" to "4"),
        )

        assertEquals("REC TIME", notification.title)
        assertEquals("新しい通知があります。", notification.body)
        assertEquals(2, notification.importance)
    }
}
