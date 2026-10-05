package com.rectime.mobile.feature.liveactivity

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.toArgb
import com.rectime.mobile.ui.theme.AppTheme
import com.rectime.mobile.feature.schedule.TimelineEvent
import com.rectime.mobile.core.model.venueDisplayText
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import platform.Foundation.NSNotificationCenter

@Serializable
private data class LiveEvent(val id: Int, val title: String, val venue: String, val start: Int, val end: Int, val participating: Boolean)

@Serializable
private data class LiveSchedule(val accountId: String, val events: List<LiveEvent>, val eventDate: String? = null,
    val backgroundColor: Int, val textColor: Int)

@Composable
internal actual fun ScheduleLiveActivityEffect(events: List<TimelineEvent>, accountId: String, ready: Boolean) {
    val backgroundColor = AppTheme.colors.commonBackground.toArgb()
    val textColor = AppTheme.colors.textPrimary.toArgb()
    DisposableEffect(accountId) {
        onDispose { publish(null) }
    }
    LaunchedEffect(events, accountId, ready, backgroundColor, textColor) {
        if (!ready) return@LaunchedEffect
        fun flatten(event: TimelineEvent): List<TimelineEvent> =
            if (event.overflowEvents.isEmpty()) listOf(event) else event.overflowEvents.flatMap(::flatten)
        val payload = LiveSchedule(accountId, events.flatMap(::flatten).distinctBy { it.eventId }.map {
            LiveEvent(it.eventId, it.title, venueDisplayText(it.venues), it.startMinuteOfDay,
                it.startMinuteOfDay + it.durationMinutes, it.isParticipating)
        }, backgroundColor = backgroundColor, textColor = textColor)
        // eventDateはAPIの開催日が提供されるまでは未設定。本番では表示しない。
        publish(Json.encodeToString(payload))
    }
}

private fun publish(json: String?) {
    NSNotificationCenter.defaultCenter.postNotificationName(
        aName = "RecreationLiveSchedule", `object` = null,
        userInfo = json?.let { mapOf("payload" to it) },
    )
}
