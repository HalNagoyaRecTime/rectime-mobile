package com.rectime.mobile.ui.component

import kotlin.test.Test
import kotlin.test.assertEquals

class MapModalTest {
    @Test
    fun buildsMapImageUrlFromBaseUrl() {
        assertEquals(
            "https://api.example.com/map/recmap.png",
            venueMapImageUrl(baseUrl = "https://api.example.com"),
        )
    }

    @Test
    fun buildsMapImageUrlWithoutDuplicatingSlash() {
        assertEquals(
            "https://api.example.com/map/recmap.png",
            venueMapImageUrl(baseUrl = "https://api.example.com/"),
        )
    }
}
