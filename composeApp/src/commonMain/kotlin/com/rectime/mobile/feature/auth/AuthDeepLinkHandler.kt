package com.rectime.mobile.feature.auth

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object AuthDeepLinkHandler {
    private val _callbacks = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val callbacks = _callbacks.asSharedFlow()

    fun handle(url: String) {
        _callbacks.tryEmit(url)
    }
}
