package com.rectime.mobile.feature.liveactivity

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

/** Activityのタップは通知の受信・再取得イベントを発火させず、対象イベントへ移動する。 */
object PersonalPlanNavigation {
    private val events = Channel<Int>(Channel.BUFFERED)
    val targets = events.receiveAsFlow()

    fun openEvent(eventId: Int) {
        if (eventId > 0) events.trySend(eventId)
    }
}
