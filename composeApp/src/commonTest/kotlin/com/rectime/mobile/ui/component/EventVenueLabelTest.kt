package com.rectime.mobile.ui.component

import kotlin.test.Test
import kotlin.test.assertEquals

class EventVenueLabelTest {

    // 1文字=1幅、省略件数表記は「+N」の文字数+1(間隔)として判定する
    private fun fitsWithin(maxWidth: Int): (String, Int) -> Boolean = { label, hiddenCount ->
        val suffixWidth = if (hiddenCount > 0) "+$hiddenCount".length + 1 else 0
        label.length + suffixWidth <= maxWidth
    }

    @Test
    fun showsAllVenuesWhenTheyFit() {
        val result = fitEventVenueLabel(listOf("体育館", "グラウンド"), fitsWithin(20))

        assertEquals(EventVenueLabel(label = "体育館・グラウンド", hiddenCount = 0), result)
    }

    @Test
    fun showsSingleVenueWithoutHiddenCount() {
        val result = fitEventVenueLabel(listOf("体育館"), fitsWithin(20))

        assertEquals(EventVenueLabel(label = "体育館", hiddenCount = 0), result)
    }

    @Test
    fun omitsTrailingVenuesThatDoNotFit() {
        val result = fitEventVenueLabel(listOf("体育館", "グラウンド", "武道場"), fitsWithin(12))

        assertEquals(EventVenueLabel(label = "体育館・グラウンド", hiddenCount = 1), result)
    }

    @Test
    fun keepsFirstVenueEvenWhenNothingFits() {
        val result = fitEventVenueLabel(listOf("体育館", "グラウンド", "武道場"), fitsWithin(2))

        assertEquals(EventVenueLabel(label = "体育館", hiddenCount = 2), result)
    }

    @Test
    fun returnsEmptyLabelWhenNoVenues() {
        val result = fitEventVenueLabel(emptyList(), fitsWithin(20))

        assertEquals(EventVenueLabel(label = "", hiddenCount = 0), result)
    }
}
