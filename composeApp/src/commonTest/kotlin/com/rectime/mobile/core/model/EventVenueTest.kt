package com.rectime.mobile.core.model

import kotlin.test.Test
import kotlin.test.assertEquals

class EventVenueTest {

    @Test
    fun venueDisplayTextUsesVenuesWhenNotEmpty() {
        val venues = listOf(
            EventVenue(venueId = 1, venueName = "体育館"),
            EventVenue(venueId = 2, venueName = "グラウンド"),
        )

        val result = venueDisplayText(venue = "旧グラウンド", venues = venues)

        assertEquals("体育館・グラウンド", result)
    }

    @Test
    fun venueDisplayTextFallsBackToVenueWhenVenuesIsEmpty() {
        val result = venueDisplayText(venue = "体育館", venues = emptyList())

        assertEquals("体育館", result)
    }

    @Test
    fun venueDisplayTextHandlesSingleVenue() {
        val venues = listOf(EventVenue(venueId = 1, venueName = "体育館"))

        val result = venueDisplayText(venue = "旧体育館", venues = venues)

        assertEquals("体育館", result)
    }
}