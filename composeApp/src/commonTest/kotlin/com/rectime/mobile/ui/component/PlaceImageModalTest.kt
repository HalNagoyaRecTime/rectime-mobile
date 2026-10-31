package com.rectime.mobile.ui.component

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlaceImageModalTest {
    @Test fun missingOrBlankImageUrlIsTreatedAsUnavailable() {
        assertNull(placeImageUrlOrNull(null))
        assertNull(placeImageUrlOrNull(""))
        assertNull(placeImageUrlOrNull("   "))
    }

    @Test fun registeredImageUrlIsKept() {
        assertEquals("https://example.com/spot.png", placeImageUrlOrNull("https://example.com/spot.png"))
    }
}
