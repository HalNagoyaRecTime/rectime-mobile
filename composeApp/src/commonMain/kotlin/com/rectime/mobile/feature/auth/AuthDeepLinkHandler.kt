package com.rectime.mobile.feature.auth

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

object AuthDeepLinkHandler {
    private val _callbacks = Channel<String>(capacity = Channel.BUFFERED)
    val callbacks = _callbacks.receiveAsFlow()

    fun handle(url: String) {
        _callbacks.trySend(url)
    }
}
