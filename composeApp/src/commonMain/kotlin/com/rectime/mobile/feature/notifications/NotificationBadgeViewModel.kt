package com.rectime.mobile.feature.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
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
            notifications.take(NotificationPageSize).any { it.id !in readIds }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private var loadedUserId: String? = null
    private var refreshJob: Job? = null
    private var pendingPushRefresh = false

    fun onSession(userId: String) {
        if (loadedUserId == userId) return
        refreshJob?.cancel()
        loadedUserId = userId
        pendingPushRefresh = false
        startRefresh(userId)
    }

    fun onForeground(userId: String) {
        if (loadedUserId != userId) onSession(userId) else startRefresh(userId)
    }

    fun onPush(userId: String) {
        if (refreshJob?.isActive == true) {
            // 取得中に届いた新着は、現在の応答に含まれない可能性がある。
            pendingPushRefresh = true
        } else {
            if (loadedUserId != userId) onSession(userId) else startRefresh(userId, afterPush = true)
        }
    }

    private fun startRefresh(userId: String, afterPush: Boolean = false) {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            feedStore.bindSession(userId)
            readStore.restore()
            var waitForPreviousRequest = afterPush
            do {
                pendingPushRefresh = false
                if (waitForPreviousRequest) {
                    feedStore.refreshAfterPush()
                } else {
                    feedStore.load(force = true)
                }
                waitForPreviousRequest = false
            } while (pendingPushRefresh && loadedUserId == userId)
        }
    }
}
