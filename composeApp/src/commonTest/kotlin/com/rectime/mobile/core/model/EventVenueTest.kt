package com.rectime.mobile.core.model

import kotlin.test.Test
import kotlin.test.assertEquals

class EventVenueTest {

    @Test
    fun venueDisplayTextJoinsMultipleVenuesWithNakaguro() {
        val venues = listOf(
            EventVenue(venueId = 1, venueName = "体育館"),
            EventVenue(venueId = 2, venueName = "グラウンド"),
        )

        val result = venueDisplayText(venues)

        assertEquals("体育館・グラウンド", result)
    }

    @Test
    fun venueDisplayTextHandlesSingleVenue() {
        val venues = listOf(EventVenue(venueId = 1, venueName = "体育館"))

        val result = venueDisplayText(venues)

        assertEquals("体育館", result)
    }

    @Test
    fun venueDisplayTextReturnsEmptyStringWhenNoVenues() {
        val result = venueDisplayText(emptyList())

        assertEquals("", result)
    }
}
