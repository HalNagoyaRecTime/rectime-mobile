package com.rectime.mobile.core.util

import kotlinx.coroutines.delay
import kotlin.time.TimeSource

internal const val MinimumRefreshDurationMillis = 600L

/** Fetch immediately, but keep the previous content visible for a perceptible refresh. */
internal suspend fun <T> withMinimumRefreshDuration(
    isRefresh: Boolean,
    block: suspend () -> T,
): T {
    val started = TimeSource.Monotonic.markNow()
    try {
        return block()
    } finally {
        if (isRefresh) {
            val remaining = MinimumRefreshDurationMillis - started.elapsedNow().inWholeMilliseconds
            if (remaining > 0) delay(remaining)
        }
    }
}
