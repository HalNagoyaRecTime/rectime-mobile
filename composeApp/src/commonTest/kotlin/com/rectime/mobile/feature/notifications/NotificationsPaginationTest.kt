package com.rectime.mobile.feature.notifications

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class NotificationsPaginationTest {
    @Test
    fun fetchAllNotificationsRequestsPagesUntilTotalIsReached() = runTest {
        val requestedOffsets = mutableListOf<Int>()
        val gateway = FakeNotificationGateway { limit, offset ->
            requestedOffsets += offset
            val notifications = when (offset) {
                0 -> listOf(notification(1), notification(2))
                2 -> listOf(notification(3), notification(4))
                4 -> listOf(notification(5))
                else -> error("Unexpected offset: $offset")
            }
            NotificationPage(
                notifications = notifications,
                total = 5,
                limit = limit,
                offset = offset,
            )
        }

        val result = fetchAllNotifications(gateway = gateway, pageSize = 2)

        assertEquals(listOf(0, 2, 4), requestedOffsets)
        assertEquals(listOf(1, 2, 3, 4, 5), result.map(UserNotification::id))
    }

    @Test
    fun fetchAllNotificationsStopsWhenBackendReturnsEmptyPage() = runTest {
        val requestedOffsets = mutableListOf<Int>()
        val gateway = FakeNotificationGateway { limit, offset ->
            requestedOffsets += offset
            NotificationPage(
                notifications = if (offset == 0) listOf(notification(1)) else emptyList(),
                total = 3,
                limit = limit,
                offset = offset,
            )
        }

        val result = fetchAllNotifications(gateway = gateway, pageSize = 2)

        assertEquals(listOf(0, 1), requestedOffsets)
        assertEquals(listOf(1), result.map(UserNotification::id))
    }

    private fun notification(id: Int) = UserNotification(
        id = id,
        type = "manual",
        title = "通知$id",
        body = "本文$id",
        scheduledAt = "2026-07-31T09:00:00+09:00",
        relatedEvent = null,
    )
}

private class FakeNotificationGateway(
    private val pageProvider: suspend (limit: Int, offset: Int) -> NotificationPage,
) : NotificationGateway {
    override suspend fun getNotifications(limit: Int, offset: Int): NotificationPage =
        pageProvider(limit, offset)

    override suspend fun getNotification(notificationId: Int): UserNotification =
        error("Notification detail is not used in pagination tests")
}
