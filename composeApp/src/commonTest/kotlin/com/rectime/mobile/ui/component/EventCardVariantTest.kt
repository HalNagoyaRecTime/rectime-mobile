package com.rectime.mobile.ui.component

import androidx.compose.ui.unit.dp
import com.rectime.mobile.feature.schedule.MIN_DISPLAY_DURATION_MINUTES
import com.rectime.mobile.feature.schedule.ScheduleHourHeight
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Issue #346: タイムラインの縦スケールと最小表示時間で、
 * 想定どおりのカードレイアウトになることを固定するテスト。
 */
class EventCardVariantTest {
    // 一般的なスマートフォンの画面幅
    private val dim = EventCardDimensions(390.dp)
    private val cardWidth = 300.dp

    private fun heightOf(minutes: Int) = ScheduleHourHeight * (minutes / 60f)

    @Test
    fun minimumSizedCardUsesFullLayoutOnCommonScreenWidths() {
        // 10分イベントなど最小表示時間まで引き伸ばされたカードは、
        // 時間・イベント名・会場を表示できる Medium レイアウトになる
        for (screenWidth in listOf(360.dp, 390.dp, 412.dp, 430.dp)) {
            assertEquals(
                EventCardSizeVariant.Medium,
                resolveEventCardVariant(
                    cardWidth,
                    heightOf(MIN_DISPLAY_DURATION_MINUTES),
                    EventCardDimensions(screenWidth),
                ),
                "screenWidth=$screenWidth",
            )
        }
    }

    @Test
    fun thirtyMinuteEventUsesLargeLayout() {
        assertEquals(
            EventCardSizeVariant.Large,
            resolveEventCardVariant(cardWidth, heightOf(30), dim),
        )
    }
}
