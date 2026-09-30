package com.rectime.mobile.ui.component

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.abs
import kotlin.math.sign

/** One coordinate for top/bottom bounce, refresh holding, and interrupted return animations. */
internal class PullRefreshGestureState(private val holdDistance: Float) {
    var offset by mutableFloatStateOf(0f)
    var refreshRequested by mutableStateOf(false)
        private set
    var viewportHeight = holdDistance * 4
    var isDragging = false
        private set
    var distance = 0f
        private set
    private var dragOrigin = 0f
    private var dragCanRefresh = false

    fun restingOffset(refreshing: Boolean) = if (refreshing || refreshRequested) holdDistance else 0f

    fun beginDrag(refreshing: Boolean, enabled: Boolean = true) {
        if (isDragging) {
            if (!enabled || refreshing) dragCanRefresh = false
            return
        }
        isDragging = true
        dragCanRefresh = enabled && !refreshing && !refreshRequested
        dragOrigin = restingOffset(refreshing)
        val displacement = offset - dragOrigin
        val extent = viewportHeight.coerceAtLeast(holdDistance * 2)
        distance = displacement.sign * abs(displacement) * extent /
            (0.55f * (extent - abs(displacement)).coerceAtLeast(1f))
    }

    fun disableRefreshForCurrentDrag() {
        dragCanRefresh = false
    }

    fun dragBy(delta: Float) {
        distance += delta
        val extent = viewportHeight.coerceAtLeast(holdDistance * 2)
        val stretch = abs(distance) * 0.55f
        offset = dragOrigin + distance.sign * extent * stretch / (extent + stretch)
    }

    fun reverseBy(delta: Float): Float {
        val consumed = if (delta > 0f) delta.coerceAtMost(-distance) else delta.coerceAtLeast(-distance)
        dragBy(consumed)
        return consumed
    }

    fun resistedVelocity(velocity: Float): Float {
        val extent = viewportHeight.coerceAtLeast(holdDistance * 2)
        val ratio = extent / (extent + abs(distance) * 0.55f)
        return velocity * 0.55f * ratio * ratio
    }

    fun release(refreshing: Boolean, enabled: Boolean): PullRefreshRelease {
        val request = enabled && dragCanRefresh && !refreshing && !refreshRequested && distance > 0f && offset >= holdDistance
        if (request) refreshRequested = true
        isDragging = false
        distance = 0f
        return PullRefreshRelease(request, restingOffset(refreshing))
    }

    fun updateRefreshing(refreshing: Boolean) {
        if (!refreshing) refreshRequested = false
    }
}

internal data class PullRefreshRelease(val requestRefresh: Boolean, val targetOffset: Float)
