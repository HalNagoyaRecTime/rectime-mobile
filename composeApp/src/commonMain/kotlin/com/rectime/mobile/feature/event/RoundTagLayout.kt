package com.rectime.mobile.feature.event

/** 時刻どおりの位置より、競技の時間帯の枠に収まることを優先する。開始時刻が無いラウンドは開始前の集合時刻に並ぶため。 */
internal fun fitRoundTagTops(tops: List<Float>, height: Float, gap: Float, bandTop: Float, bandBottom: Float): List<Float> {
    val step = height + gap
    var next = bandTop + gap
    return tops.map { top -> maxOf(top, next).also { next = it + step } }
        .mapIndexed { index, top -> minOf(top, bandBottom - (tops.size - index) * step) }
}
