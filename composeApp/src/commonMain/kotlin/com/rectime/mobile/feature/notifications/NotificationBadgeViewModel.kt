package com.rectime.mobile.feature.notifications

import com.rectime.mobile.core.cache.CacheRefreshQueue
import com.rectime.mobile.core.cache.CacheRequestGeneration
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class NotificationBadgeViewModel(
    private val feedStore: NotificationFeedStore = NotificationFeedStore.shared,
    private val readStore: NotificationReadStore = NotificationReadStore.shared,
) : ViewModel() {
    val hasUnreadNotifications: StateFlow<Boolean> =
        combine(feedStore.notifications, readStore.readIds) { notifications, readIds ->
            notifications.take(NotificationHistoryLimit).any { it.id !in readIds }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private var loadedUserId: String? = null
    private var loadedSession: CacheRequestGeneration? = null
    private var refreshJob: Job? = null
    private val refreshQueue = CacheRefreshQueue()

    fun onSession(userId: String) {
        if (loadedUserId == userId && loadedSession?.isCurrent == true) return
        refreshJob?.cancel()
        loadedUserId = userId
        loadedSession = CacheRequestGeneration()
        startRefresh(userId)
    }

    fun onForeground(userId: String) {
        requestLatest(userId)
    }

    fun onPush(userId: String) = requestLatest(userId)

    private fun requestLatest(userId: String) {
        if (loadedUserId != userId || loadedSession?.isCurrent != true) {
            onSession(userId)
        } else {
            startRefresh(userId, afterCurrentRequest = true)
        }
    }

    private fun startRefresh(userId: String, afterCurrentRequest: Boolean = false) {
        refreshJob = refreshQueue.launch(viewModelScope, afterCurrent = afterCurrentRequest) {
            feedStore.bindSession(userId)
            readStore.restore()
            if (afterCurrentRequest) feedStore.refreshAfterCurrentRequest()
            else feedStore.load(force = true)
        }
    }
}
