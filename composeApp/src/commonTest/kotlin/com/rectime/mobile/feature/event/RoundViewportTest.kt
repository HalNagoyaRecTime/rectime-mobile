package com.rectime.mobile.feature.event

import kotlin.test.Test
import kotlin.test.assertEquals

class RoundViewportTest {
    @Test fun firstRoundRemainsAtTop() {
        assertEquals(0f, roundViewportOffset(30f, 30f, 600f, 180f))
    }
    @Test fun ownRoundMovesToTopInset() {
        assertEquals(200f, roundViewportOffset(230f, 30f, 600f, 180f))
    }
    @Test fun finalRoundDoesNotLeaveEmptySpaceBelowTimeline() {
        assertEquals(420f, roundViewportOffset(580f, 30f, 600f, 180f))
    }
    @Test fun timeIndicatorCentersOnlyWithinScrollableBounds() {
        assertEquals(210f, roundViewportOffset(300f, 90f, 600f, 180f))
        assertEquals(0f, roundViewportOffset(30f, 90f, 600f, 180f))
        assertEquals(420f, roundViewportOffset(590f, 90f, 600f, 180f))
        assertEquals(0f, roundViewportOffset(300f, 300f, 600f, 600f))
    }
    @Test fun shortTimelineAndOutOfRangeFocusRemainWithinBounds() {
        assertEquals(0f, roundViewportOffset(80f, 30f, 100f, 180f))
        assertEquals(0f, roundViewportOffset(-20f, 30f, 600f, 180f))
        assertEquals(420f, roundViewportOffset(1000f, 30f, 600f, 180f))
    }
}
