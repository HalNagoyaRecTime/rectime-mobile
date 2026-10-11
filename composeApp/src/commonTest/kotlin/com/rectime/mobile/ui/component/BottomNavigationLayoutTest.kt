package com.rectime.mobile.ui.component

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BottomNavigationLayoutTest {
    private val labels = listOf(66f, 66f, 22f, 22f)

    @Test
    fun wideBarUsesEqualWidths() {
        val slots = bottomNavigationSlots(608f, labels)
        assertEquals(listOf(76f, 228f, 380f, 532f), slots.map { it.center })
    }

    @Test
    fun phonePrioritizesRoomyPaddingWithMinimalMovement() {
        val slots = bottomNavigationSlots(360f, labels, spaciousnessPriority = 0f)
        assertEquals(listOf(49f, 147f, 225f, 315f), slots.map { it.center })
        slots.forEachIndexed { index, slot ->
            assertTrue(slot.contentWidth >= labels[index] + 32f - 0.001f)
            assertTrue(slot.pillRight - slot.pillLeft >= labels[index] + 32f - 0.001f)
        }
    }

    @Test
    fun longLabelBorrowsTouchSpaceWithoutMovingEqualCenters() {
        val slots = bottomNavigationSlots(360f, listOf(22f, 100f, 22f, 22f), preferredPadding = 4f, spaciousnessPriority = 0f)
        assertEquals(listOf(45f, 135f, 225f, 315f), slots.map { it.center })
        assertTrue(slots[1].contentWidth >= 104f)
    }

    @Test
    fun crowdedPhoneMovesOnlyAsFarAsRequired() {
        val slots = bottomNavigationSlots(240f, labels)
        assertEquals(listOf(38f, 114f, 174f, 218f), slots.map { it.center })
    }

    @Test
    fun selectionCanCrossTouchBoundaryButNeverReachNeighborText() {
        val slots = bottomNavigationSlots(360f, labels)
        val equalPriority = bottomNavigationSlots(360f, labels, spaciousnessPriority = 0f)
        assertTrue(slots.last().pillRight - slots.last().pillLeft >
            equalPriority.last().pillRight - equalPriority.last().pillLeft)
        assertTrue(slots.last().center < equalPriority.last().center)
        assertTrue(slots.any { it.pillLeft < it.left || it.pillRight > it.right })
        slots.forEachIndexed { index, slot ->
            if (index > 0) {
                assertTrue(slot.pillLeft > slots[index - 1].center + labels[index - 1] / 2)
            }
            if (index < slots.lastIndex) {
                assertTrue(slot.pillRight < slots[index + 1].center - labels[index + 1] / 2)
            }
            assertTrue(slot.pillLeft >= 0f && slot.pillRight <= 360f)
        }
    }

    @Test
    fun narrowBarReducesPaddingBeforeTruncatingLabels() {
        val slots = bottomNavigationSlots(240f, labels)
        slots.forEachIndexed { index, slot ->
            assertTrue(slot.contentWidth >= labels[index] + 4f - 0.001f)
            assertTrue(slot.contentWidth >= 44f)
        }
    }

    @Test
    fun narrowPhoneReducesPaddingEquallyForAllLabels() {
        val slots = bottomNavigationSlots(288f, labels)
        slots.forEachIndexed { index, slot ->
            assertEquals(28f, slot.contentWidth - labels[index], 0.001f)
        }
    }

    @Test
    fun maximumRoominessKeepsShortLabelsAsSpaciousAsLongLabels() {
        val slots = bottomNavigationSlots(360f, labels, spaciousnessPriority = 1f)
        val widths = slots.map { it.pillRight - it.pillLeft }
        assertTrue(widths.min() > 100f)
        assertTrue(widths.max() - widths.min() < 0.01f)
    }

    @Test
    fun regionsNeverOverlapAndFillTheBarForDifferentWidthsAndTabCounts() {
        for (width in listOf(120f, 220f, 240f, 288f, 360f, 608f, 640f)) {
            for (count in 1..5) {
                val slots = bottomNavigationSlots(width, List(count) { if (it < 2) 66f else 22f })
                assertEquals(0f, slots.first().left)
                assertEquals(width, slots.last().right)
                slots.zipWithNext().forEach { (left, right) -> assertEquals(left.right, right.left) }
                slots.forEachIndexed { index, slot ->
                    if (index > 0) {
                        val previous = slots[index - 1]
                        val previousLabel = if (index - 1 < 2) 66f else 22f
                        val textRight = previous.center + minOf(previousLabel, (previous.contentWidth - 4f).coerceAtLeast(0f)) / 2
                        assertTrue(slot.pillLeft >= textRight + 2f - 0.001f)
                    }
                    if (index < slots.lastIndex) {
                        val next = slots[index + 1]
                        val nextLabel = if (index + 1 < 2) 66f else 22f
                        val textLeft = next.center - minOf(nextLabel, (next.contentWidth - 4f).coerceAtLeast(0f)) / 2
                        assertTrue(slot.pillRight <= textLeft - 2f + 0.001f)
                    }
                    assertEquals(slot.center, (slot.pillLeft + slot.pillRight) / 2, 0.001f)
                    assertTrue(slot.center - slot.contentWidth / 2 >= slot.left - 0.001f)
                    assertTrue(slot.center + slot.contentWidth / 2 <= slot.right + 0.001f)
                    assertTrue(slot.contentWidth >= minOf(44f, width / count) - 0.001f)
                }
            }
        }
    }
}
