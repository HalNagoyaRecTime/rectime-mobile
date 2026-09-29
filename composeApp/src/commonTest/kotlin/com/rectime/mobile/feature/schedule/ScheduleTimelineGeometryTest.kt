package com.rectime.mobile.feature.schedule

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class ScheduleTimelineGeometryTest {
    private val geometry = ScheduleTimelineGeometry(
        topPadding = 80.dp,
        bottomPadding = 200.dp,
        hourHeight = 72.dp,
        visibleStartHour = 9,
        visibleEndHour = 20,
    )

    @Test
    fun calculatesFullDayWithoutChangingVisibleHeight() {
        assertEquals(792.dp, geometry.contentHeight)
        assertEquals(1072.dp, geometry.totalHeight)
        assertEquals((-568).dp, geometry.minuteY(0))
        assertEquals((-64).dp, geometry.minuteY(7 * 60))
        assertEquals(80.dp, geometry.minuteY(9 * 60))
        assertEquals(404.dp, geometry.minuteY(13 * 60 + 30))
        assertEquals(872.dp, geometry.minuteY(20 * 60))
        assertEquals(1160.dp, geometry.minuteY(24 * 60))
    }

    @Test
    fun pastAreaEndsAtCurrentMinuteEvenAfterVisibleHours() {
        assertEquals(0.dp, geometry.pastHeight(7 * 60))
        assertEquals(872.dp, geometry.pastHeight(20 * 60))
        assertEquals(1016.dp, geometry.pastHeight(22 * 60))
        assertEquals(1072.dp, geometry.pastHeight(23 * 60))
    }

    @Test
    fun paintExtendsAtLeastToMidnightAndToViewportEdges() {
        assertEquals(568.dp, geometry.paintTop(400.dp))
        assertEquals(800.dp, geometry.paintTop(800.dp))
        assertEquals(88.dp, geometry.paintBottom(40.dp))
        assertEquals(400.dp, geometry.paintBottom(400.dp))
    }

    @Test
    fun pastColorContinuesAcrossBothScrollEdges() {
        val top = geometry.paintTop(400.dp)
        val bottom = geometry.paintBottom(40.dp)
        assertEquals(504.dp, geometry.pastAboveHeight(7 * 60, top))
        assertEquals(568.dp, geometry.pastAboveHeight(20 * 60, top))
        assertEquals(0.dp, geometry.pastBelowHeight(20 * 60, bottom))
        assertEquals(0.dp, geometry.pastBelowHeight(22 * 60, bottom))
        assertEquals(16.dp, geometry.pastBelowHeight(23 * 60, bottom))
        assertEquals(88.dp, geometry.pastBelowHeight(24 * 60, bottom))
    }
}
