package com.rectime.mobile.ui.component

internal data class BottomNavigationSlot(
    val center: Float,
    val left: Float,
    val right: Float,
    val pillLeft: Float,
    val pillRight: Float,
    val contentWidth: Float,
)

/** 共通の左右余白を確保し、その範囲で均等位置からの移動量を最小にする。 */
internal fun bottomNavigationSlots(
    barWidth: Float,
    labelWidths: List<Float>,
    preferredPadding: Float = 32f,
    minimumPadding: Float = 4f,
    minimumTouchWidth: Float = 44f,
    preferredPillWidth: Float = 120f,
    spaciousnessPriority: Float = 0.9f,
    neighborLabelGap: Float = 2f,
    equalLayoutMinBarWidth: Float = 480f,
): List<BottomNavigationSlot> {
    require(labelWidths.isNotEmpty() && barWidth >= 0)
    require(labelWidths.all { it >= 0 })
    require(spaciousnessPriority in 0f..1f && neighborLabelGap >= 0)
    val equalWidth = barWidth / labelWidths.size
    val touchWidth = minOf(minimumTouchWidth, equalWidth)
    // 余白は全項目で同じ量だけ減らす。長いラベルだけを窮屈にしない。
    var padding = preferredPadding
    for (attempt in 0..labelWidths.size) {
        val overflow = labelWidths.sumOf { maxOf(touchWidth, it + padding).toDouble() }.toFloat() - barWidth
        val flexibleCount = labelWidths.count { it + padding > touchWidth }
        if (overflow <= 0f || flexibleCount == 0 || padding <= minimumPadding) break
        padding = (padding - overflow / flexibleCount).coerceAtLeast(minimumPadding)
    }
    val required = labelWidths.map { maxOf(touchWidth, it + padding) }
    // 全文が収まらない場合のみ、タップ幅を残して省略表示にする。
    val requiredWidths = if (required.sum() <= barWidth) required else {
        val extras = required.map { it - touchWidth }
        val remaining = (barWidth - touchWidth * labelWidths.size).coerceAtLeast(0f)
        extras.map { touchWidth + remaining * it / extras.sum() }
    }
    val visibleLabelWidths = requiredWidths.mapIndexed { index, width ->
        minOf(labelWidths[index], (width - minimumPadding).coerceAtLeast(0f))
    }
    fun centerGaps(pillWidth: Float) = List(labelWidths.size - 1) { index ->
        maxOf(
            (requiredWidths[index] + requiredWidths[index + 1]) / 2,
            pillWidth / 2 + maxOf(visibleLabelWidths[index], visibleLabelWidths[index + 1]) / 2 + neighborLabelGap,
        )
    }
    // 全項目に同じ枠幅を確保できる上限を探し、短いラベルの枠だけが小さくなるのを防ぐ。
    var widestPill = 0f
    var upperPill = maxOf(preferredPillWidth, equalWidth)
    repeat(16) {
        val candidate = (widestPill + upperPill) / 2
        val occupied = maxOf(requiredWidths.first(), candidate) / 2 + centerGaps(candidate).sum() +
            maxOf(requiredWidths.last(), candidate) / 2
        if (occupied <= barWidth) widestPill = candidate else upperPill = candidate
    }
    fun fitCenters(gaps: List<Float>, leftInset: Float, rightInset: Float): List<Float> {
        val offsets = FloatArray(labelWidths.size)
        for (index in 1 until offsets.size) offsets[index] = offsets[index - 1] + gaps[index - 1]
        val targets = labelWidths.indices.map { (it + 0.5f) * equalWidth - offsets[it] }
        val upper = (barWidth - rightInset - offsets.last()).coerceAtLeast(leftInset)
        return nearestNondecreasing(targets).mapIndexed { index, value ->
            value.coerceIn(leftInset, upper) + offsets[index]
        }
    }
    val initialCenters = fitCenters(
        List(labelWidths.size - 1) { (requiredWidths[it] + requiredWidths[it + 1]) / 2 },
        requiredWidths.first() / 2,
        requiredWidths.last() / 2,
    )
    // 均等寄りの配置で確保できる枠幅と、最大の枠幅の間を優先度で調整する。
    val balancedPill = minOf(widestPill, initialCenters.indices.minOf { index ->
        val leftLimit = if (index == 0) 0f
            else initialCenters[index - 1] + visibleLabelWidths[index - 1] / 2 + neighborLabelGap
        val rightLimit = if (index == initialCenters.lastIndex) barWidth
            else initialCenters[index + 1] - visibleLabelWidths[index + 1] / 2 - neighborLabelGap
        2 * minOf(initialCenters[index] - leftLimit, rightLimit - initialCenters[index]).coerceAtLeast(0f)
    })
    val priority = if (barWidth >= equalLayoutMinBarWidth && equalWidth >= requiredWidths.max()) 0f
        else spaciousnessPriority
    val commonPillWidth = balancedPill + (widestPill - balancedPill) * priority
    // 確保した枠幅の条件下で、均等位置への二乗距離を最小化する。
    val centers = fitCenters(
        centerGaps(commonPillWidth),
        maxOf(requiredWidths.first(), commonPillWidth) / 2,
        maxOf(requiredWidths.last(), commonPillWidth) / 2,
    )
    val boundaries = List(centers.size + 1) { index ->
        when (index) {
            0 -> 0f
            centers.size -> barWidth
            else -> (centers[index - 1] + requiredWidths[index - 1] / 2 +
                centers[index] - requiredWidths[index] / 2) / 2
        }
    }
    val slots = centers.mapIndexed { index, center ->
        val left = boundaries[index]
        val right = boundaries[index + 1]
        val contentWidth = 2 * minOf(center - left, right - center).coerceAtLeast(0f)
        BottomNavigationSlot(center, left, right, left, right, contentWidth)
    }
    // 枠は隣の文字との最小の隙間まで広げる。文字とタップ領域は動かさない。
    val textHalfWidths = slots.mapIndexed { index, slot ->
        minOf(labelWidths[index], (slot.contentWidth - minimumPadding).coerceAtLeast(0f)) / 2
    }
    return slots.mapIndexed { index, slot ->
        val limitLeft = if (index == 0) 0f else {
            val previousTextRight = slots[index - 1].center + textHalfWidths[index - 1]
            previousTextRight + neighborLabelGap
        }
        val limitRight = if (index == slots.lastIndex) barWidth else {
            val nextTextLeft = slots[index + 1].center - textHalfWidths[index + 1]
            nextTextLeft - neighborLabelGap
        }
        // 片側へ寄せず、アイコンの中心を基準に左右対称の幅にする。
        val maxPillWidth = 2 * minOf(slot.center - limitLeft, limitRight - slot.center).coerceAtLeast(0f)
        val pillWidth = maxOf(slot.contentWidth, commonPillWidth).coerceAtMost(maxPillWidth)
        val pillLeft = slot.center - pillWidth / 2
        slot.copy(pillLeft = pillLeft, pillRight = pillLeft + pillWidth)
    }
}

/** 順序が逆転した隣接グループを平均化する。各要素は一度ずつ処理する。 */
private fun nearestNondecreasing(values: List<Float>): List<Float> {
    val averages = mutableListOf<Float>()
    val counts = mutableListOf<Int>()
    for (value in values) {
        averages.add(value)
        counts.add(1)
        while (averages.size > 1 && averages[averages.lastIndex - 1] > averages.last()) {
            val rightAverage = averages.removeAt(averages.lastIndex)
            val rightCount = counts.removeAt(counts.lastIndex)
            val leftIndex = averages.lastIndex
            val totalCount = counts[leftIndex] + rightCount
            averages[leftIndex] = (averages[leftIndex] * counts[leftIndex] + rightAverage * rightCount) / totalCount
            counts[leftIndex] = totalCount
        }
    }
    return averages.indices.flatMap { index -> List(counts[index]) { averages[index] } }
}
