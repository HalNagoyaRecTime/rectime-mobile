package com.rectime.mobile.feature.notifications

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.receiveAsFlow

sealed interface NotificationNavigationTarget {
    data object Home : NotificationNavigationTarget
    data class EventDetail(val eventId: Int) : NotificationNavigationTarget
    data class NotificationDetail(val notificationId: Int) : NotificationNavigationTarget
}

object NotificationNavigationPayload {
    private val supportedKeys = setOf(
        "type",
        "notificationId",
        "notificationSendScheduleId",
        "notificationType",
        "importance",
        "navigationType",
        "eventId",
    )

    fun extract(data: Map<String, String>): Map<String, String> =
        data.filterKeys(supportedKeys::contains)

    fun parse(data: Map<String, String>): NotificationNavigationTarget {
        return when (data["type"] ?: data["notificationType"]) {
            "event_reminder",
            "schedule_reminder",
            "schedule_update",
            -> data.positiveInt("eventId")
                ?.let(NotificationNavigationTarget::EventDetail)
                ?: NotificationNavigationTarget.Home

            "manual" -> when {
                data["navigationType"] == "home" -> NotificationNavigationTarget.Home
                else -> data.positiveInt("notificationId")
                    ?.let(NotificationNavigationTarget::NotificationDetail)
                    ?: NotificationNavigationTarget.Home
            }

            else -> NotificationNavigationTarget.Home
        }
    }
}

object NotificationNavigationHandler {
    private val _targets = Channel<NotificationNavigationTarget>(capacity = Channel.BUFFERED)
    val targets = _targets.receiveAsFlow()

    private val _updates = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val updates = _updates.asSharedFlow()

    fun onNotificationReceived() {
        _updates.tryEmit(Unit)
    }

    fun handle(data: Map<String, String>) {
        onNotificationReceived()
        val payload = NotificationNavigationPayload.extract(data)
        _targets.trySend(NotificationNavigationPayload.parse(payload))
    }
}

private fun Map<String, String>.positiveInt(key: String): Int? =
    get(key)?.toIntOrNull()?.takeIf { it > 0 }
