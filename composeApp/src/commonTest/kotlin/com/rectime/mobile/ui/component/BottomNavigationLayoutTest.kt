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
        slots.forEach { assertEquals(152f, it.pillRight - it.pillLeft, 0.001f) }
    }

    @Test
    fun tabletPillsStayEqualDespiteDifferentLabelsAndWidthRequests() {
        for (priority in listOf(0f, 0.5f, 1f)) {
            val slots = bottomNavigationSlots(
                640f, labels, spaciousnessPriority = priority, neighborContentGap = 6f,
                preferredPillWidths = listOf(0f, 0f, 112f, 112f),
                iconWidths = List(4) { 22f }, iconOverhangs = listOf(0f, 0f, 2f, 0f),
                labelGeometry = BottomNavigationLabelGeometry(56f, 40f, 54f),
                iconGeometry = BottomNavigationLabelGeometry(56f, 7f, 34f),
            )
            slots.forEachIndexed { index, slot ->
                assertEquals((index + 0.5f) * 160f, slot.center, 0.001f)
                assertEquals(160f, slot.pillRight - slot.pillLeft, 0.001f)
                assertEquals(slot.center, (slot.pillLeft + slot.pillRight) / 2, 0.001f)
            }
        }
    }

    @Test
    fun equalLayoutUsesActualContentInsteadOfAWidthThreshold() {
        val slots = bottomNavigationSlots(
            448f, labels, spaciousnessPriority = 1f, neighborContentGap = 6f,
            preferredPillWidths = listOf(0f, 0f, 112f, 112f),
        )
        assertEquals(listOf(56f, 168f, 280f, 392f), slots.map { it.center })
    }

    @Test
    fun wideBarStillAdjustsWhenActualContentCannotFitAtEqualCenters() {
        val slots = bottomNavigationSlots(608f, listOf(170f, 22f, 22f, 22f), spaciousnessPriority = 0.7f)
        assertTrue(slots.first().center >= 87f)
        assertTrue(slots.first().pillLeft >= 0f)
    }

    @Test
    fun equalPriorityKeepsEqualCentersAndStillExpandsPills() {
        val slots = bottomNavigationSlots(360f, labels, spaciousnessPriority = 0f)
        assertEquals(listOf(45f, 135f, 225f, 315f), slots.map { it.center })
        slots.forEachIndexed { index, slot ->
            assertTrue(slot.contentWidth >= labels[index] + 4f - 0.001f)
            assertTrue(slot.pillRight - slot.pillLeft >= labels[index] + 4f - 0.001f)
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
        val slots = bottomNavigationSlots(240f, labels, spaciousnessPriority = 1f)
        assertEquals(listOf(38f, 114f, 174f, 218f), slots.map { it.center })
    }

    @Test
    fun selectionCanCrossTouchBoundaryButNeverReachNeighborText() {
        val slots = bottomNavigationSlots(360f, labels)
        val equalPriority = bottomNavigationSlots(360f, labels, spaciousnessPriority = 0f)
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
        val slots = bottomNavigationSlots(288f, labels, spaciousnessPriority = 1f)
        slots.forEachIndexed { index, slot ->
            assertEquals(28f, slot.contentWidth - labels[index], 0.001f)
        }
    }

    @Test
    fun maximumRoominessMovesCentersToUseAvailableSpace() {
        val equalPriority = bottomNavigationSlots(360f, labels, spaciousnessPriority = 0f)
        val slots = bottomNavigationSlots(360f, labels, spaciousnessPriority = 1f)
        assertTrue(slots.first().center > equalPriority.first().center)
        assertTrue(slots.first().pillRight - slots.first().pillLeft >
            equalPriority.first().pillRight - equalPriority.first().pillLeft)
        assertTrue(slots.last().center < equalPriority.last().center)
    }

    @Test
    fun paddingPriorityUsesSamePaddingForDifferentContentWidths() {
        val slots = bottomNavigationSlots(
            360f, labels, spaciousnessPriority = 1f, neighborContentGap = 6f,
            labelGeometry = BottomNavigationLabelGeometry(56f, 40f, 54f),
            iconWidths = listOf(22f, 22f, 22f, 22f),
            iconOverhangs = listOf(0f, 0f, 2f, 0f),
            iconGeometry = BottomNavigationLabelGeometry(56f, 7f, 34f),
        )
        val widths = slots.map { it.pillRight - it.pillLeft }
        val contentWidths = listOf(66f, 66f, 22f, 22f)
        val paddings = widths.indices.map { widths[it] - contentWidths[it] }
        paddings.forEach { assertEquals(paddings.first(), it, 0.001f) }
        assertTrue(widths.first() > widths.last())
        assertTrue(paddings.first() > 32f)
        // 通知バッジは安全判定に使うが、設定との余白・枠幅の差にはしない。
        assertEquals(widths[2], widths[3], 0.001f)
    }

    @Test
    fun intermediatePriorityEvaluatesTheWholeLayoutInsteadOfBlendingPositions() {
        val equal = bottomNavigationSlots(360f, labels, spaciousnessPriority = 0f, neighborContentGap = 6f)
        val padded = bottomNavigationSlots(360f, labels, spaciousnessPriority = 1f, neighborContentGap = 6f)
        val combined = bottomNavigationSlots(360f, labels, spaciousnessPriority = 0.5f, neighborContentGap = 6f)
        val targetPadding = padded.first().pillRight - padded.first().pillLeft - labels.first()
        fun cost(slots: List<BottomNavigationSlot>): Double = slots.indices.sumOf { index ->
            val positionError = (slots[index].center - (index + 0.5f) * 90f).toDouble()
            val actualPadding = slots[index].pillRight - slots[index].pillLeft - labels[index]
            val paddingError = ((targetPadding - actualPadding).coerceAtLeast(0f) / 2).toDouble()
            0.5 * positionError * positionError + 0.5 * paddingError * paddingError
        }
        assertTrue(cost(combined) <= minOf(cost(equal), cost(padded)) + 0.001)
        assertTrue(combined.indices.any { index ->
            kotlin.math.abs(combined[index].center - (equal[index].center + padded[index].center) / 2) > 0.01f
        })
    }

    @Test
    fun preferredWidthLetsShortItemsRequestMoreSpace() {
        val original = bottomNavigationSlots(360f, labels, spaciousnessPriority = 0.7f, neighborContentGap = 6f)
        val requested = bottomNavigationSlots(
            360f, labels, spaciousnessPriority = 0.7f, neighborContentGap = 6f,
            preferredPillWidths = listOf(0f, 0f, 112f, 112f),
        )
        assertTrue(requested.last().pillRight - requested.last().pillLeft >
            original.last().pillRight - original.last().pillLeft)
        assertTrue(requested.last().center < original.last().center)
    }

    @Test
    fun impossiblePreferredWidthsNeverOverrideSafetyOrEqualPriority() {
        for (priority in listOf(0f, 0.7f, 1f)) {
            val slots = bottomNavigationSlots(
                360f, labels, spaciousnessPriority = priority, neighborContentGap = 6f,
                preferredPillWidths = List(4) { 1000f },
            )
            slots.forEachIndexed { index, slot ->
                assertTrue(slot.pillLeft >= 0f && slot.pillRight <= 360f)
                assertEquals(slot.center, (slot.pillLeft + slot.pillRight) / 2, 0.001f)
                if (index > 0) assertTrue(slot.pillLeft >= slots[index - 1].center + labels[index - 1] / 2 + 6f - 0.001f)
                if (index < slots.lastIndex) assertTrue(slot.pillRight <= slots[index + 1].center - labels[index + 1] / 2 - 6f + 0.001f)
            }
            if (priority == 0f) assertEquals(listOf(45f, 135f, 225f, 315f), slots.map { it.center })
        }
    }

    @Test
    fun roundedEdgeUsesMoreSpaceWithoutCoveringNeighborLabel() {
        val geometry = BottomNavigationLabelGeometry(height = 56f, top = 40f, bottom = 54f)
        val flat = bottomNavigationSlots(360f, labels, spaciousnessPriority = 1f)
        val rounded = bottomNavigationSlots(360f, labels, spaciousnessPriority = 1f, labelGeometry = geometry)
        assertTrue(rounded.first().pillRight - rounded.first().pillLeft >
            flat.first().pillRight - flat.first().pillLeft + 1f)
        rounded.forEachIndexed { index, slot ->
            val width = slot.pillRight - slot.pillLeft
            val inset = geometry.sideInset(width)
            assertEquals(slot.center, (slot.pillLeft + slot.pillRight) / 2, 0.001f)
            if (index > 0) {
                assertTrue(slot.pillLeft + inset >= rounded[index - 1].center + labels[index - 1] / 2 + 2f - 0.001f)
            }
            if (index < rounded.lastIndex) {
                assertTrue(slot.pillRight - inset <= rounded[index + 1].center - labels[index + 1] / 2 - 2f + 0.001f)
            }
        }
    }

    @Test
    fun narrowTallPillGetsNoExtraSpaceAlongItsStraightSide() {
        val geometry = BottomNavigationLabelGeometry(height = 70f, top = 35f, bottom = 50f)
        assertEquals(0f, geometry.sideInset(44f))
    }

    @Test
    fun spaciousnessNeverOverridesConfiguredGapOrIconBounds() {
        val textWidths = listOf(66f, 66f, 22f, 22f)
        val iconWidths = listOf(22f, 22f, 36f, 22f)
        val labelGeometry = BottomNavigationLabelGeometry(56f, 40f, 54f)
        val iconGeometry = BottomNavigationLabelGeometry(56f, 7f, 34f)
        for (priority in listOf(0f, 0.5f, 1f)) {
            for (gap in listOf(2f, 6f, 10f)) {
                val slots = bottomNavigationSlots(
                    360f, textWidths, spaciousnessPriority = priority, neighborContentGap = gap,
                    labelGeometry = labelGeometry, iconWidths = iconWidths, iconGeometry = iconGeometry,
                )
                slots.forEachIndexed { index, slot ->
                    val width = slot.pillRight - slot.pillLeft
                    assertEquals(slot.center, (slot.pillLeft + slot.pillRight) / 2, 0.001f)
                    for (neighbor in listOf(index - 1, index + 1).filter { it in slots.indices }) {
                        val protectedHalf = maxOf(
                            textWidths[neighbor] / 2 - labelGeometry.sideInset(width),
                            iconWidths[neighbor] / 2 - iconGeometry.sideInset(width),
                        )
                        if (neighbor < index) {
                            assertTrue(slot.pillLeft >= slots[neighbor].center + protectedHalf + gap - 0.001f)
                        } else {
                            assertTrue(slot.pillRight <= slots[neighbor].center - protectedHalf - gap + 0.001f)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun equalPriorityExpandsPillsAndPaddingPriorityStillRespectsSafeLimit() {
        for (priority in listOf(0f, 0.5f, 1f)) {
            val gap = 6f
            val slots = bottomNavigationSlots(360f, labels, spaciousnessPriority = priority, neighborContentGap = gap)
            slots.forEachIndexed { index, slot ->
                val leftLimit = if (index == 0) 0f else slots[index - 1].center + labels[index - 1] / 2 + gap
                val rightLimit = if (index == slots.lastIndex) 360f else slots[index + 1].center - labels[index + 1] / 2 - gap
                val expectedWidth = 2 * minOf(slot.center - leftLimit, rightLimit - slot.center)
                val width = slot.pillRight - slot.pillLeft
                assertTrue(width <= expectedWidth + 0.001f)
                if (priority == 0f) assertEquals(expectedWidth, width, 0.001f)
            }
        }
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
