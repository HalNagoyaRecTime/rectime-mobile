package com.rectime.mobile.feature.auth

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

object AuthDeepLinkHandler {
    private val _callbacks = Channel<String>(capacity = Channel.BUFFERED)
    val callbacks = _callbacks.receiveAsFlow()
    private val _dismissals = Channel<Boolean>(capacity = Channel.BUFFERED)
    val dismissals = _dismissals.receiveAsFlow()

    fun handle(url: String) {
        _callbacks.trySend(url)
    }

    /** アプリ内のログイン画面がコールバックなしで閉じたことを通知する。 */
    fun dismiss(canceledByUser: Boolean) {
        _dismissals.trySend(canceledByUser)
    }
}
