package com.rectime.mobile.core.network

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class EventDetailResponseTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun decode(venueImage: String, spotImage: String) = json.decodeFromString<EventDetailResponse>(
        """{"event_id":1,"event_name":"競技","venues":[{"venue_id":1,"venue_name":"体育館"$venueImage}],
        "start_time":"0900","end_time":"1000","rule_text":null,
        "rounds":[{"round":1,"gatherings":[{"gathering_id":10,"gathering_time":"08:45",
        "gathering_spot":{"gathering_spot_id":5,"gathering_spot_name":"中庭"$spotImage}}]}]}""",
    )

    @Test
    fun placeImageUrlsAreKeptOnVenueAndGathering() {
        val response = decode(
            venueImage = ""","image_url":"https://r2.example.com/venue.webp"""",
            spotImage = ""","image_url":"https://r2.example.com/spot.webp"""",
        )

        assertEquals("https://r2.example.com/venue.webp", response.toModel().venues.single().imageUrl)
        assertEquals("https://r2.example.com/spot.webp", response.toGatherings()!!.single().gatheringSpotImageUrl)
    }

    @Test
    fun placeWithoutImageHasNoImageUrl() {
        val response = decode(
            venueImage = ""","image_url":null""",
            spotImage = "",
        )

        assertEquals(null, response.toModel().venues.single().imageUrl)
        assertEquals(null, response.toGatherings()!!.single().gatheringSpotImageUrl)
    }
}
