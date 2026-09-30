package com.rectime.mobile.core.haptics

/**
 * Emits once when a pull distance first reaches the refresh threshold.
 * Crossing back below the threshold arms the next emission.
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
