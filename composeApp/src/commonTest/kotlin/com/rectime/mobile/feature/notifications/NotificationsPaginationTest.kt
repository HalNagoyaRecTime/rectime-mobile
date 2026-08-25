package com.rectime.mobile.feature.notifications

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

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

    @Test
    fun fetchAllNotificationsPassesPageSizeAsLimit() = runTest {
        val requestedLimits = mutableListOf<Int>()
        val gateway = FakeNotificationGateway { limit, offset ->
            requestedLimits += limit
            NotificationPage(
                notifications = listOf(notification(1)),
                total = 1,
                limit = limit,
                offset = offset,
            )
        }

        fetchAllNotifications(gateway = gateway, pageSize = 25)

        assertEquals(listOf(25), requestedLimits)
    }

    @Test
    fun fetchAllNotificationsStopsWhenBackendReportsSmallerTotal() = runTest {
        val requestedOffsets = mutableListOf<Int>()
        val gateway = FakeNotificationGateway { limit, offset ->
            requestedOffsets += offset
            NotificationPage(
                notifications = listOf(notification(1), notification(2)),
                total = 1,
                limit = limit,
                offset = offset,
            )
        }

        val result = fetchAllNotifications(gateway = gateway, pageSize = 2)

        assertEquals(listOf(0), requestedOffsets)
        assertEquals(listOf(1, 2), result.map(UserNotification::id))
    }

    @Test
    fun fetchAllNotificationsReturnsEmptyListWhenBackendHasNoNotification() = runTest {
        val gateway = FakeNotificationGateway { limit, offset ->
            NotificationPage(
                notifications = emptyList(),
                total = 0,
                limit = limit,
                offset = offset,
            )
        }

        val result = fetchAllNotifications(gateway = gateway, pageSize = 100)

        assertEquals(emptyList(), result)
    }

    @Test
    fun fetchAllNotificationsRejectsNonPositivePageSize() = runTest {
        val gateway = FakeNotificationGateway { _, _ ->
            error("Notification list must not be requested")
        }

        assertFailsWith<IllegalArgumentException> {
            fetchAllNotifications(gateway = gateway, pageSize = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            fetchAllNotifications(gateway = gateway, pageSize = -1)
        }
    }

    @Test
    fun fetchAllNotificationsPropagatesGatewayFailure() = runTest {
        val gateway = FakeNotificationGateway { _, _ ->
            throw NotificationApiException(statusCode = 401)
        }

        val error = assertFailsWith<NotificationApiException> {
            fetchAllNotifications(gateway = gateway, pageSize = 100)
        }

        assertEquals(401, error.statusCode)
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
