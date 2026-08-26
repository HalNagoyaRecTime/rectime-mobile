package com.rectime.mobile.feature.notifications

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

internal fun String.toNotificationListDateTime(
    now: Instant = Clock.System.now(),
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): String = runCatching {
    val instant = Instant.parse(this)
    val elapsed = now - instant
    when {
        elapsed < 1.minutes -> "今"
        elapsed < 1.hours -> "${elapsed.inWholeMinutes}分前"
        else -> {
            val dateTime = instant.toLocalDateTime(timeZone)
            "${dateTime.year}/${(dateTime.month.ordinal + 1).pad2()}/${dateTime.day.pad2()} " +
                "${dateTime.hour}:${dateTime.minute.pad2()}"
        }
    }
}.getOrElse { this }

private fun Int.pad2(): String = toString().padStart(2, '0')
