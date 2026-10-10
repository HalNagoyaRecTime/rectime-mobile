package com.rectime.mobile.ui.component

import androidx.compose.ui.unit.dp
import com.rectime.mobile.feature.schedule.ScheduleHourHeight
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Issue #346: タイムラインの縦スケールで、所要時間ごとに想定どおりの
 * カードレイアウトになることを固定するテスト。
 */
class EventCardVariantTest {
    // 一般的なスマートフォンの画面幅
    private val dim = EventCardDimensions(390.dp)
    private val cardWidth = 300.dp

    private fun heightOf(minutes: Int) = ScheduleHourHeight * (minutes / 60f)

    @Test
    fun tenMinuteEventUsesSingleRowCompactLayout() {
        assertEquals(
            EventCardSizeVariant.Compact,
            resolveEventCardVariant(cardWidth, heightOf(10), dim),
        )
    }

    @Test
    fun fifteenMinuteEventUsesSingleRowCompactLayout() {
        assertEquals(
            EventCardSizeVariant.Compact,
            resolveEventCardVariant(cardWidth, heightOf(15), dim),
        )
    }

    @Test
    fun fifteenMinuteEventUsesCompactLayoutOnNarrowAndWideScreens() {
        for (screenWidth in listOf(360.dp, 412.dp, 430.dp)) {
            assertEquals(
                EventCardSizeVariant.Compact,
                resolveEventCardVariant(cardWidth, heightOf(15), EventCardDimensions(screenWidth)),
                "screenWidth=$screenWidth",
            )
        }
    }

    @Test
    fun seventeenMinuteEventUsesSmallLayout() {
        assertEquals(
            EventCardSizeVariant.Small,
            resolveEventCardVariant(cardWidth, heightOf(17), dim),
        )
    }

    @Test
    fun thirtyMinuteEventUsesMediumLayout() {
        assertEquals(
            EventCardSizeVariant.Medium,
            resolveEventCardVariant(cardWidth, heightOf(30), dim),
        )
    }
}
