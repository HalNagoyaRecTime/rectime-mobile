package com.rectime.mobile.feature.event

import com.rectime.mobile.core.cache.FetchedCache
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.time.Duration.Companion.seconds

/** 明示更新・初回表示・対象追加は必ず確認し、短い前面復帰だけ通信を省く。 */
internal enum class EventRefreshReason {
    Required,
    Foreground,
}

internal fun <T> FetchedCache<T>?.canReuse(reason: EventRefreshReason, clock: Clock): Boolean {
    if (reason != EventRefreshReason.Foreground || this == null) return false
    val fetched = fetchedAt?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return false
    val age = clock.now() - fetched
    // 時刻不明・端末時計の巻き戻りでは鮮度を断定しない。
    return age >= 0.seconds && age < 30.seconds
}
