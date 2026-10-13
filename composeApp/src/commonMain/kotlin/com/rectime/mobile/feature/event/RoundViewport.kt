package com.rectime.mobile.feature.event

/** 注目するラウンドを上へ寄せ、時間軸の上下端からはみ出さない位置に制限する。 */
internal fun roundViewportOffset(focus: Float, topInset: Float, contentHeight: Float, viewportHeight: Float): Float =
    (focus - topInset).coerceIn(0f, (contentHeight - viewportHeight).coerceAtLeast(0f))
