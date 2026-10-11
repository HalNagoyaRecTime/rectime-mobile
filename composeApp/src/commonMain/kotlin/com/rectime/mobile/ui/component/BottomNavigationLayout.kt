package com.rectime.mobile.ui.component

import kotlin.math.sqrt

/** 実際の枠の高さと保護する内容の上下位置。角丸の外側に残る空白を計算する。 */
internal data class BottomNavigationLabelGeometry(val height: Float, val top: Float, val bottom: Float) {
    fun sideInset(width: Float): Float {
        val radius = minOf(width, height) / 2
        val distance = when {
            bottom < radius -> radius - bottom
            top > height - radius -> top - (height - radius)
            else -> 0f
        }.coerceIn(0f, radius)
        return radius - sqrt((radius * radius - distance * distance).coerceAtLeast(0f))
    }
}

internal data class BottomNavigationSlot(
    val center: Float,
    val left: Float,
    val right: Float,
    val pillLeft: Float,
    val pillRight: Float,
    val contentWidth: Float,
)

/** 0は均等位置、1は共通の左右余白を優先。どちらも干渉しない範囲に収める。 */
internal fun bottomNavigationSlots(
    barWidth: Float,
    labelWidths: List<Float>,
    preferredPadding: Float = 32f,
    minimumPadding: Float = 4f,
    minimumTouchWidth: Float = 44f,
    spaciousnessPriority: Float = 0.9f,
    neighborContentGap: Float = 2f,
    labelGeometry: BottomNavigationLabelGeometry? = null,
    iconWidths: List<Float> = List(labelWidths.size) { 0f },
    iconGeometry: BottomNavigationLabelGeometry? = null,
    iconOverhangs: List<Float> = List(labelWidths.size) { 0f },
    preferredPillWidths: List<Float> = List(labelWidths.size) { 0f },
): List<BottomNavigationSlot> {
    require(labelWidths.isNotEmpty() && barWidth.isFinite() && barWidth >= 0)
    require(labelWidths.all { it.isFinite() && it >= 0 })
    require(spaciousnessPriority in 0f..1f && neighborContentGap.isFinite() && neighborContentGap >= 0)
    require(listOf(preferredPadding, minimumPadding, minimumTouchWidth).all {
        it.isFinite() && it >= 0f
    })
    require(iconWidths.size == labelWidths.size && iconWidths.all { it.isFinite() && it >= 0f })
    require(iconOverhangs.size == labelWidths.size && iconOverhangs.all { it.isFinite() && it >= 0f })
    require(preferredPillWidths.size == labelWidths.size && preferredPillWidths.all { it.isFinite() && it >= 0f })
    // バッジは衝突判定だけに含める。余白を揃える基準の内容幅には含めない。
    val protectedIconWidths = iconWidths.indices.map { iconWidths[it] + iconOverhangs[it] * 2 }
    val equalWidth = barWidth / labelWidths.size
    val touchWidth = minOf(minimumTouchWidth, equalWidth)
    val measuredContentWidths = labelWidths.indices.map { maxOf(labelWidths[it], iconWidths[it]) }
    val minimumContentPadding = maxOf(minimumPadding, neighborContentGap)
    // 余白は全項目で同じ量だけ減らす。長いラベルだけを窮屈にしない。
    var padding = maxOf(preferredPadding, minimumContentPadding)
    for (attempt in 0..labelWidths.size) {
        val overflow = measuredContentWidths.sumOf { maxOf(touchWidth, it + padding).toDouble() }.toFloat() - barWidth
        val flexibleCount = measuredContentWidths.count { it + padding > touchWidth }
        if (overflow <= 0f || flexibleCount == 0 || padding <= minimumContentPadding) break
        padding = (padding - overflow / flexibleCount).coerceAtLeast(minimumContentPadding)
    }
    val required = measuredContentWidths.map { maxOf(touchWidth, it + padding) }
    // 全文が収まらない場合のみ、タップ幅を残して省略表示にする。
    val requiredWidths = if (required.sum() <= barWidth) required else {
        val extras = required.map { it - touchWidth }
        val remaining = (barWidth - touchWidth * labelWidths.size).coerceAtLeast(0f)
        extras.map { touchWidth + remaining * it / extras.sum() }
    }
    val visibleLabelWidths = requiredWidths.mapIndexed { index, width ->
        minOf(labelWidths[index], (width - minimumPadding).coerceAtLeast(0f))
    }
    // 幅が高さ以上なら角丸の半径は一定。探索中も同じ形状の計算を繰り返さない。
    val fullRadiusWidth = maxOf(labelGeometry?.height ?: 0f, iconGeometry?.height ?: 0f)
    val labelSideInset = labelGeometry?.sideInset(fullRadiusWidth) ?: 0f
    val iconSideInset = iconGeometry?.sideInset(fullRadiusWidth) ?: 0f
    fun protectedHalfWidth(index: Int, pillWidth: Float, textWidth: Float): Float = maxOf(
        textWidth / 2 - if (pillWidth >= fullRadiusWidth) labelSideInset else (labelGeometry?.sideInset(pillWidth) ?: 0f),
        protectedIconWidths[index] / 2 - if (pillWidth >= fullRadiusWidth) iconSideInset else (iconGeometry?.sideInset(pillWidth) ?: 0f),
    )
    fun pillFits(positions: List<Float>, index: Int, width: Float, textWidths: List<Float>): Boolean {
        val left = positions[index] - width / 2
        val right = positions[index] + width / 2
        return left >= 0f && right <= barWidth &&
            (index == 0 || left >= positions[index - 1] +
                protectedHalfWidth(index - 1, width, textWidths[index - 1]) + neighborContentGap) &&
            (index == positions.lastIndex || right <= positions[index + 1] -
                protectedHalfWidth(index + 1, width, textWidths[index + 1]) - neighborContentGap)
    }
    fun fitCenters(
        gaps: List<Float>,
        leftInset: Float,
        rightInset: Float,
    ): List<Float> {
        val offsets = FloatArray(labelWidths.size)
        for (index in 1 until offsets.size) offsets[index] = offsets[index - 1] + gaps[index - 1]
        val targets = labelWidths.indices.map { (it + 0.5f) * equalWidth - offsets[it] }
        val upper = (barWidth - rightInset - offsets.last()).coerceAtLeast(leftInset)
        return nearestNondecreasing(targets).mapIndexed { index, value ->
            value.coerceIn(leftInset, upper) + offsets[index]
        }
    }
    // 同じ幅の枠が安全に収まるなら、位置・タップ領域・選択枠をすべて等分する。
    // 広い画面では隣の文字の長さによって枠幅を変えず、位置の探索も行わない。
    val evenlySpacedCenters = labelWidths.indices.map { (it + 0.5f) * equalWidth }
    val equalLayoutFits = labelWidths.indices.all { index ->
        val wantedWidth = maxOf(measuredContentWidths[index] + preferredPadding, preferredPillWidths[index], minimumTouchWidth)
        wantedWidth <= equalWidth && pillFits(evenlySpacedCenters, index, equalWidth, labelWidths)
    }
    if (equalLayoutFits) return evenlySpacedCenters.mapIndexed { index, center ->
        val left = index * equalWidth
        val right = if (index == labelWidths.lastIndex) barWidth else (index + 1) * equalWidth
        BottomNavigationSlot(center, left, right, left, right, equalWidth)
    }
    // 均等側では希望の余白のために動かさず、内容の干渉防止に必要な分だけ動かす。
    val equalRequiredWidths = requiredWidths.mapIndexed { index, width ->
        minOf(width, maxOf(touchWidth, measuredContentWidths[index] + minimumContentPadding))
    }
    val equalCenters = fitCenters(
        List(labelWidths.size - 1) { (equalRequiredWidths[it] + equalRequiredWidths[it + 1]) / 2 },
        equalRequiredWidths.first() / 2,
        equalRequiredWidths.last() / 2,
    )
    val priority = spaciousnessPriority
    val visibleContentWidths = visibleLabelWidths.indices.map { maxOf(visibleLabelWidths[it], iconWidths[it]) }
    // 枠幅ではなく内容から枠までの左右余白を共通化する。長い内容の枠はその分広くなる。
    fun paddedWidths(totalPadding: Float) = visibleContentWidths.map { it + totalPadding }
    fun roomyGaps(widths: List<Float>) = List(labelWidths.size - 1) { index ->
        maxOf(
            (requiredWidths[index] + requiredWidths[index + 1]) / 2,
            widths[index] / 2 + protectedHalfWidth(index + 1, widths[index], visibleLabelWidths[index + 1]) + neighborContentGap,
            widths[index + 1] / 2 + protectedHalfWidth(index, widths[index + 1], visibleLabelWidths[index]) + neighborContentGap,
        )
    }
    val sharedPadding = if (priority > 0f) largestFittingValue(barWidth) { candidate ->
        val widths = paddedWidths(candidate)
        val occupied = maxOf(requiredWidths.first(), widths.first()) / 2 + roomyGaps(widths).sum() +
            maxOf(requiredWidths.last(), widths.last()) / 2
        occupied <= barWidth
    } else 0f
    val roomyWidths = paddedWidths(sharedPadding)
    val roomyCenters = if (priority > 0f) fitCenters(
        roomyGaps(roomyWidths),
        maxOf(requiredWidths.first(), roomyWidths.first()) / 2,
        maxOf(requiredWidths.last(), roomyWidths.last()) / 2,
    ) else equalCenters
    fun safePillWidth(positions: List<Float>, index: Int, textWidths: List<Float>): Float {
        val center = positions[index]
        val upper = 2 * minOf(center, barWidth - center).coerceAtLeast(0f)
        var halfWidth = upper / 2
        if (index > 0) halfWidth = minOf(halfWidth, center - positions[index - 1] -
            protectedHalfWidth(index - 1, fullRadiusWidth, textWidths[index - 1]) - neighborContentGap)
        if (index < positions.lastIndex) halfWidth = minOf(halfWidth, positions[index + 1] - center -
            protectedHalfWidth(index + 1, fullRadiusWidth, textWidths[index + 1]) - neighborContentGap)
        val directWidth = 2 * halfWidth.coerceAtLeast(0f)
        // 通常の横長の枠は隣の内容との距離から直接求められる。
        // 丸に近い細い枠・浮動小数点の誤差が出た場合だけ従来の安全な探索を使う。
        if (directWidth >= fullRadiusWidth && pillFits(positions, index, directWidth, textWidths)) {
            return directWidth
        }
        return largestFittingValue(upper) { width ->
            pillFits(positions, index, width, textWidths)
        }
    }
    // 希望幅は安全条件に混ぜず、評価の目標だけを引き上げる。
    // 全部の希望が収まらなくても、内容・タップ幅を壊さず不足を分配する。
    val desiredWidths = roomyWidths.indices.map { maxOf(roomyWidths[it], preferredPillWidths[it]) }
    val hasAdditionalWidthRequests = desiredWidths != roomyWidths
    // 位置のずれと、共通余白・希望幅からの不足を全項目で評価する。
    // 0.5は位置の半分移動ではなく、この二つの評価に同じ重みを与える。
    fun layoutCost(positions: List<Float>): Double = positions.indices.sumOf { index ->
        val positionError = (positions[index] - (index + 0.5f) * equalWidth).toDouble()
        val availableWidth = safePillWidth(positions, index, visibleLabelWidths)
        val paddingError = ((desiredWidths[index] - availableWidth).coerceAtLeast(0f) / 2).toDouble()
        (1 - priority) * positionError * positionError + priority * paddingError * paddingError
    }
    val centers = when {
        priority == 0f -> equalCenters
        priority == 1f && !hasAdditionalWidthRequests -> roomyCenters
        else -> minimizeLayoutCost(
            initial = if (layoutCost(equalCenters) <= layoutCost(roomyCenters)) equalCenters else roomyCenters,
            minimumWidths = equalRequiredWidths,
            barWidth = barWidth,
            cost = ::layoutCost,
        )
    }
    val contentWidths = if (priority == 1f && !hasAdditionalWidthRequests) requiredWidths else equalRequiredWidths
    val boundaries = List(centers.size + 1) { index ->
        when (index) {
            0 -> 0f
            centers.size -> barWidth
            else -> (centers[index - 1] + contentWidths[index - 1] / 2 +
                centers[index] - contentWidths[index] / 2) / 2
        }
    }
    val slots = centers.mapIndexed { index, center ->
        val left = boundaries[index]
        val right = boundaries[index + 1]
        val contentWidth = 2 * minOf(center - left, right - center).coerceAtLeast(0f)
        BottomNavigationSlot(center, left, right, left, right, contentWidth)
    }
    // 実際に表示できる文字幅で最後に安全上限を再確認する。
    val textWidths = slots.mapIndexed { index, slot ->
        minOf(labelWidths[index], (slot.contentWidth - minimumPadding).coerceAtLeast(0f))
    }
    return slots.mapIndexed { index, slot ->
        val safeWidth = safePillWidth(centers, index, textWidths)
        val pillWidth = if (priority == 0f) safeWidth else minOf(desiredWidths[index], safeWidth)
        val pillLeft = slot.center - pillWidth / 2
        slot.copy(pillLeft = pillLeft, pillRight = pillLeft + pillWidth)
    }
}

/** 内容が重ならない区間内で、全体の評価が改善する移動だけを採用する。 */
private fun minimizeLayoutCost(
    initial: List<Float>,
    minimumWidths: List<Float>,
    barWidth: Float,
    cost: (List<Float>) -> Double,
): List<Float> {
    val centers = initial.toMutableList()
    var bestCost = cost(centers)
    for (pass in 0 until 12) {
        val previousCost = bestCost
        // 一方向の項目だけが先に空間を使わないよう、処理方向を交互にする。
        val indices = if (pass % 2 == 0) centers.indices.toList() else centers.indices.reversed().toList()
        for (index in indices) {
            val original = centers[index]
            var left = if (index == 0) minimumWidths.first() / 2 else centers[index - 1] +
                (minimumWidths[index - 1] + minimumWidths[index]) / 2
            var right = if (index == centers.lastIndex) barWidth - minimumWidths.last() / 2 else centers[index + 1] -
                (minimumWidths[index] + minimumWidths[index + 1]) / 2
            if (right <= left) continue
            repeat(24) {
                val first = left + (right - left) / 3
                val second = right - (right - left) / 3
                centers[index] = first
                val firstCost = cost(centers)
                centers[index] = second
                val secondCost = cost(centers)
                if (firstCost <= secondCost) right = second else left = first
            }
            centers[index] = (left + right) / 2
            val candidateCost = cost(centers)
            if (candidateCost < bestCost) bestCost = candidateCost else centers[index] = original
        }
        if (previousCost - bestCost < 0.0001) break
    }
    return centers
}

/** 必ず収まる側の値を返す。共通余白と個別の安全上限で同じ探索を使う。 */
private inline fun largestFittingValue(upperBound: Float, fits: (Float) -> Boolean): Float {
    var lower = 0f
    var upper = upperBound
    repeat(20) {
        val candidate = (lower + upper) / 2
        if (fits(candidate)) lower = candidate else upper = candidate
    }
    return lower
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
