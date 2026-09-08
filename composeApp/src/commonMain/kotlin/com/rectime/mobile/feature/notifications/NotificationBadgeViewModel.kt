package com.rectime.mobile.feature.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NotificationBadgeViewModel(
    private val feedStore: NotificationFeedStore = NotificationFeedStore.shared,
    private val readStore: NotificationReadStore = NotificationReadStore.shared,
) : ViewModel() {
    val hasUnreadNotifications: StateFlow<Boolean> =
        combine(feedStore.notifications, readStore.readIds) { notifications, readIds ->
            notifications.any { it.id !in readIds }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private var loadedUserId: String? = null

    fun onSession(userId: String) {
        if (loadedUserId == userId) return
        loadedUserId = userId
        viewModelScope.launch {
            feedStore.reset()
            readStore.restore()
            feedStore.load()
        }
    }
}
