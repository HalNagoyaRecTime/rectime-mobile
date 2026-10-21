package com.rectime.mobile.core.util

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay

private const val MinimumRefreshDurationMillis = 600L

/** Fetch immediately, but keep the previous content visible for a perceptible refresh. */
internal suspend fun <T> withMinimumRefreshDuration(
    isRefresh: Boolean,
    block: suspend () -> T,
): T {
    if (!isRefresh) return block()
    return coroutineScope {
        val result = async {
            try {
                Result.success(block())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
        delay(MinimumRefreshDurationMillis)
        result.await().getOrThrow()
    }
}
