package com.rectime.mobile.core.haptics

/**
 * 引っ張り更新の閾値に到達した時だけ通知する。
 * 閾値未満へ戻ったら、次の到達を再び通知できるようにする。
 */
internal class HapticThresholdDetector(
    private val onThresholdReached: () -> Unit,
    private val threshold: Float = 1f,
) {
    private var wasReady = false

    fun onDistanceFractionChanged(distanceFraction: Float, enabled: Boolean) {
        if (distanceFraction.isNaN() || distanceFraction < threshold) {
            wasReady = false
            return
        }

        if (!wasReady) {
            wasReady = true
            if (enabled) {
                onThresholdReached()
            }
        }
    }
}
