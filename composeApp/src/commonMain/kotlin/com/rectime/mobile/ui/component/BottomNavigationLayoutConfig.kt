package com.rectime.mobile.ui.component

/** 寸法はdp。中央揃えと隣の文字・アイコンへの干渉防止は優先度によらず維持する。 */
internal data class BottomNavigationLayoutConfig(
    // 0は均等位置で各枠を最大限広げる。1は位置を動かして共通の左右余白を最大限確保する。
    // 中間値は均等位置からのずれと共通余白の不足を全体で評価する重み。
    val spaciousnessPriority: Float = 0.7f,
    val preferredHorizontalPadding: Float = 16f,
    val minimumHorizontalPadding: Float = 2f,
    // 選択枠の輪郭と隣の文字・アイコン（通知バッジを含む）の最小の隙間。
    val neighborContentGap: Float = 6f,
    val minimumTouchWidth: Float = 44f,
    val maxBarWidth: Float = 640f,
)
