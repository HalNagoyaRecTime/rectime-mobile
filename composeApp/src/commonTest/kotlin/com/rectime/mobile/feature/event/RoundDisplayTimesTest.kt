package com.rectime.mobile.feature.event

import kotlin.test.Test
import kotlin.test.assertEquals

class RoundDisplayTimesTest {
    @Test fun prototypeSeparatesStartAndGathering() {
        assertEquals(RoundDisplayTimes(670, 660), resolveRoundDisplayTimes("1100", null, prototype = true))
    }

    @Test fun publishedDisplayDoesNotInventMissingTimes() {
        val times = resolveRoundDisplayTimes("1100", null, prototype = false)
        assertEquals(RoundDisplayTimes(null, 660), times)
    }

    @Test fun receivedTimesOverridePrototypeOffsets() {
        assertEquals(RoundDisplayTimes(680, 660), resolveRoundDisplayTimes("1100", "1120", prototype = true))
    }

    @Test fun missingGatheringDoesNotHideReceivedStart() {
        val times = resolveRoundDisplayTimes("99:59", "0900", prototype = false)
        assertEquals(RoundDisplayTimes(540, null), times)
        assertEquals(RoundDisplayTimes(null, null), resolveRoundDisplayTimes(null, null, prototype = true))
    }
    @Test fun displayedTimesDoNotPadHours() {
        assertEquals("9:00", displayMinute(540))
        assertEquals("0:05", displayMinute(5))
        assertEquals("23:50", displayMinute(-10))
        assertEquals("0:10", displayMinute(1450))
    }
}
