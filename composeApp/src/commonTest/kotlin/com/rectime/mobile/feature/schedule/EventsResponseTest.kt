package com.rectime.mobile.feature.schedule

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EventsResponseTest {

    private val json = Json { ignoreUnknownKeys = true }

    // ---- 正常系 ----

    @Test
    fun decodesSnakeCasePayload() {
        val decoded = json.decodeFromString<EventsResponse>(
            """
            {
              "events": [
                {
                  "event_id": 3,
                  "event_name": "綱引き",
                  "rule_text": "8人1組で綱を引く",
                  "venue": "グラウンド",
                  "start_time": "1030",
                  "end_time": "1100",
                  "created_at": "2026-04-01T00:00:00Z",
                  "updated_at": "2026-04-02T09:00:00Z"
                }
              ],
              "total": 1,
              "limit": 50,
              "offset": 0
            }
            """.trimIndent(),
        )

        assertEquals(1, decoded.total)
        assertEquals(50, decoded.limit)
        assertEquals(0, decoded.offset)

        val event = decoded.events.single()
        assertEquals(3, event.eventId)
        assertEquals("綱引き", event.eventName)
        assertEquals("8人1組で綱を引く", event.ruleText)
        assertEquals("グラウンド", event.venue)
        assertEquals("1030", event.startTime)
        assertEquals("1100", event.endTime)
        assertEquals("2026-04-01T00:00:00Z", event.createdAt)
        assertEquals("2026-04-02T09:00:00Z", event.updatedAt)
    }

    @Test
    fun decodesNullRuleText() {
        val decoded = json.decodeFromString<EventsResponse>(eventsJson(ruleText = "null"))

        assertNull(decoded.events.single().ruleText)
    }

    @Test
    fun decodesPayloadWithoutRuleTextKey() {
        val decoded = json.decodeFromString<EventsResponse>(
            """
            {
              "events": [
                {
                  "event_id": 3,
                  "event_name": "綱引き",
                  "venue": "グラウンド",
                  "start_time": "1030",
                  "end_time": "1100",
                  "created_at": "2026-04-01T00:00:00Z",
                  "updated_at": "2026-04-02T09:00:00Z"
                }
              ],
              "total": 1,
              "limit": 50,
              "offset": 0
            }
            """.trimIndent(),
        )

        assertNull(decoded.events.single().ruleText)
    }

    @Test
    fun decodesEmptyEventList() {
        val decoded = json.decodeFromString<EventsResponse>(
            """{"events":[],"total":0,"limit":50,"offset":0}""",
        )

        assertTrue(decoded.events.isEmpty())
        assertEquals(0, decoded.total)
    }

    @Test
    fun ignoresUnknownFields() {
        val decoded = json.decodeFromString<EventsResponse>(
            """
            {
              "events": [
                {
                  "event_id": 3,
                  "event_name": "綱引き",
                  "venue": "グラウンド",
                  "start_time": "1030",
                  "end_time": "1100",
                  "created_at": "2026-04-01T00:00:00Z",
                  "updated_at": "2026-04-02T09:00:00Z",
                  "capacity": 24
                }
              ],
              "total": 1,
              "limit": 50,
              "offset": 0,
              "has_next": false
            }
            """.trimIndent(),
        )

        assertEquals(3, decoded.events.single().eventId)
    }

    // ---- 異常系 ----

    @Test
    fun rejectsPayloadMissingRequiredField() {
        assertFailsWith<SerializationException> {
            json.decodeFromString<EventsResponse>(
                """
                {
                  "events": [
                    {
                      "event_id": 3,
                      "event_name": "綱引き",
                      "start_time": "1030",
                      "end_time": "1100",
                      "created_at": "2026-04-01T00:00:00Z",
                      "updated_at": "2026-04-02T09:00:00Z"
                    }
                  ],
                  "total": 1,
                  "limit": 50,
                  "offset": 0
                }
                """.trimIndent(),
            )
        }
    }

    @Test
    fun rejectsPayloadMissingPaginationField() {
        assertFailsWith<SerializationException> {
            json.decodeFromString<EventsResponse>("""{"events":[],"total":0,"limit":50}""")
        }
    }

    @Test
    fun rejectsCamelCaseKeys() {
        assertFailsWith<SerializationException> {
            json.decodeFromString<EventsResponse>(
                """
                {
                  "events": [
                    {
                      "eventId": 3,
                      "eventName": "綱引き",
                      "venue": "グラウンド",
                      "startTime": "1030",
                      "endTime": "1100",
                      "createdAt": "2026-04-01T00:00:00Z",
                      "updatedAt": "2026-04-02T09:00:00Z"
                    }
                  ],
                  "total": 1,
                  "limit": 50,
                  "offset": 0
                }
                """.trimIndent(),
            )
        }
    }

    @Test
    fun rejectsNullEventId() {
        assertFailsWith<SerializationException> {
            json.decodeFromString<EventsResponse>(eventsJson(eventId = "null"))
        }
    }

    @Test
    fun rejectsEventsAsObjectInsteadOfArray() {
        assertFailsWith<SerializationException> {
            json.decodeFromString<EventsResponse>("""{"events":{},"total":0,"limit":50,"offset":0}""")
        }
    }

    @Test
    fun rejectsErrorPayload() {
        assertFailsWith<SerializationException> {
            json.decodeFromString<EventsResponse>("""{"error":{"message":"Internal Server Error"}}""")
        }
    }

    private fun eventsJson(eventId: String = "3", ruleText: String = "\"8人1組で綱を引く\"") =
        """
        {
          "events": [
            {
              "event_id": $eventId,
              "event_name": "綱引き",
              "rule_text": $ruleText,
              "venue": "グラウンド",
              "start_time": "1030",
              "end_time": "1100",
              "created_at": "2026-04-01T00:00:00Z",
              "updated_at": "2026-04-02T09:00:00Z"
            }
          ],
          "total": 1,
          "limit": 50,
          "offset": 0
        }
        """.trimIndent()
}
