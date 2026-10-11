package com.rectime.mobile.ui.component

/** 寸法はdp。中央揃えと隣の文字に重ならない条件は設定によらず維持する。 */
internal data class BottomNavigationLayoutConfig(
    // 0で均等配置を優先、1で全項目に確保できる枠の広さを優先。
    val spaciousnessPriority: Float = 0.9f,
    val preferredHorizontalPadding: Float = 16f,
    val minimumHorizontalPadding: Float = 2f,
    val neighborLabelGap: Float = 2f,
    val minimumTouchWidth: Float = 44f,
    val equalLayoutMinBarWidth: Float = 480f,
    val maxBarWidth: Float = 640f,
)
