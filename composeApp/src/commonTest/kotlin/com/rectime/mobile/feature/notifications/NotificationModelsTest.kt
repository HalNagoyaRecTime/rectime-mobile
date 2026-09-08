package com.rectime.mobile.feature.notifications

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class NotificationModelsTest {

    private val json = Json { ignoreUnknownKeys = true }

    // ---- デシリアライズ 正常系 ----

    @Test
    fun listResponseIsDecodedFromSnakeCaseFields() {
        val response = json.decodeFromString<NotificationListResponse>(notificationListJson)

        assertEquals(1, response.total)
        assertEquals(20, response.limit)
        assertEquals(40, response.offset)

        val notification = response.notifications.single()
        assertEquals(12, notification.notificationId)
        assertEquals("event_reminder", notification.notificationType)
        assertEquals("2026-07-31T09:00:00+09:00", notification.scheduledAt)
        assertEquals(7, notification.relatedEvent?.eventId)
        assertEquals("玉入れ", notification.relatedEvent?.eventName)
    }

    @Test
    fun relatedEventIsNullWhenFieldIsAbsent() {
        val response = json.decodeFromString<NotificationResponse>(
            """
            {
              "notification_id": 15,
              "notification_type": "manual",
              "title": "全体連絡",
              "body": "本日の競技は予定どおり実施します。",
              "scheduled_at": "2026-07-31T08:00:00+09:00"
            }
            """.trimIndent(),
        )

        assertNull(response.relatedEvent)
    }

    @Test
    fun unknownFieldsAreIgnored() {
        val response = json.decodeFromString<NotificationResponse>(
            """
            {
              "notification_id": 15,
              "notification_type": "manual",
              "title": "全体連絡",
              "body": "本文",
              "scheduled_at": "2026-07-31T08:00:00+09:00",
              "read_at": null,
              "created_at": "2026-07-30T08:00:00+09:00"
            }
            """.trimIndent(),
        )

        assertEquals(15, response.notificationId)
    }

    // ---- デシリアライズ 異常系 ----

    @Test
    fun decodingFailsWhenRequiredFieldIsMissing() {
        assertFailsWith<Exception> {
            json.decodeFromString<NotificationResponse>(
                """{"notification_id":15,"notification_type":"manual","title":"全体連絡"}""",
            )
        }
    }

    // ---- モデル変換 ----

    @Test
    fun responseIsMappedToDomainModel() {
        val page = json.decodeFromString<NotificationListResponse>(notificationListJson).toModel()

        assertEquals(1, page.total)
        assertEquals(20, page.limit)
        assertEquals(40, page.offset)

        val notification = page.notifications.single()
        assertEquals(
            UserNotification(
                id = 12,
                type = "event_reminder",
                title = "競技開始のお知らせ",
                body = "開始時間が近づいています。",
                scheduledAt = "2026-07-31T09:00:00+09:00",
                relatedEvent = NotificationRelatedEvent(
                    id = 7,
                    name = "玉入れ",
                    venue = "体育館",
                    startTime = "0915",
                    endTime = "0945",
                ),
            ),
            notification,
        )
    }

    @Test
    fun emptyListResponseIsMappedToEmptyPage() {
        val page = json
            .decodeFromString<NotificationListResponse>(
                """{"notifications":[],"total":0,"limit":100,"offset":0}""",
            )
            .toModel()

        assertEquals(emptyList(), page.notifications)
        assertEquals(0, page.total)
    }

    private companion object {
        val notificationListJson = """
            {
              "notifications": [
                {
                  "notification_id": 12,
                  "notification_type": "event_reminder",
                  "title": "競技開始のお知らせ",
                  "body": "開始時間が近づいています。",
                  "scheduled_at": "2026-07-31T09:00:00+09:00",
                  "related_event": {
                    "event_id": 7,
                    "event_name": "玉入れ",
                    "venue": "体育館",
                    "start_time": "0915",
                    "end_time": "0945"
                  }
                }
              ],
              "total": 1,
              "limit": 20,
              "offset": 40
            }
        """.trimIndent()
    }
}
