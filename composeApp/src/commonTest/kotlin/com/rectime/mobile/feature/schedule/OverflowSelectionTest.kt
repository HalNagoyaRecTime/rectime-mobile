package com.rectime.mobile.feature.schedule

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OverflowSelectionTest {
    private fun event(id: Int, participating: Boolean = false) = TimelineEvent(
        id, "競技$id", emptyList(), 540, 30, 0, 1, "9:00", "9:30", participating,
    )

    @Test
    fun openSelectionReflectsParticipationAddedAndRemovedAfterOpening() {
        val old = listOf(event(1), event(2, true))
        val selectedIds = old.map { it.eventId }
        val group = event(0).copy(overflowCount = 2, overflowEvents = old.map {
            it.copy(isParticipating = !it.isParticipating, title = "更新${it.eventId}")
        })
        val current = resolveOverflowSelection(listOf(group), selectedIds)
        assertTrue(current[0].isParticipating)
        assertFalse(current[1].isParticipating)
        assertEquals(listOf("更新1", "更新2"), current.map { it.title })
    }

    @Test
    fun laneChangesKeepSelectionOrderAndDiscardDeletedEvents() {
        val current = resolveOverflowSelection(listOf(event(2), event(1)), listOf(1, 3, 2, 1))
        assertEquals(listOf(1, 2), current.map { it.eventId })
        assertTrue(resolveOverflowSelection(emptyList(), listOf(1)).isEmpty())
        assertTrue(resolveOverflowSelection(listOf(event(1)), null).isEmpty())
    }
}
