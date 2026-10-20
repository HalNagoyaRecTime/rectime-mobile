package com.rectime.mobile.feature.event

import kotlin.test.Test
import kotlin.test.assertEquals

class RoundTagLayoutTest {
    @Test fun roundsInsideBandKeepTheirPositions() {
        assertEquals(listOf(120f, 200f), fitRoundTagTops(listOf(120f, 200f), 40f, 10f, 100f, 400f))
    }
    @Test fun roundBeforeEventStartMovesBelowBandTop() {
        assertEquals(listOf(110f, 200f), fitRoundTagTops(listOf(30f, 200f), 40f, 10f, 100f, 400f))
    }
    @Test fun roundsBeforeEventStartStackWithoutOverlapping() {
        assertEquals(listOf(110f, 160f, 210f), fitRoundTagTops(listOf(30f, 60f, 200f), 40f, 10f, 100f, 400f))
    }
    @Test fun roundsAfterEventEndMoveAboveBandBottom() {
        assertEquals(listOf(300f, 350f), fitRoundTagTops(listOf(380f, 420f), 40f, 10f, 100f, 400f))
    }
    @Test fun roundsFillBandJustTallEnoughForAllTags() {
        assertEquals(listOf(110f, 160f, 210f), fitRoundTagTops(listOf(30f, 60f, 390f), 40f, 10f, 100f, 260f))
    }
}
