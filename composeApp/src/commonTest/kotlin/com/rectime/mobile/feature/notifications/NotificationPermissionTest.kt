package com.rectime.mobile.feature.notifications

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class NotificationPermissionTest {
    @Test
    fun permissionIsRequestedOnlyOnce() = runTest {
        val store = InMemoryKeyValueStore()
        val controller = FakeNotificationPermissionController()
        val startup = NotificationPermissionStartup(controller, store)

        startup.requestIfNeeded()
        startup.requestIfNeeded()

        assertEquals(1, controller.requestCount)
        assertTrue(store.requested)
    }

    private class FakeNotificationPermissionController : NotificationPermissionController {
        var status = NotificationPermissionStatus.NotDetermined
        var requestCount = 0

        override suspend fun getStatus(): NotificationPermissionStatus = status

        override suspend fun requestPermission(): NotificationPermissionStatus {
            requestCount += 1
            status = NotificationPermissionStatus.Granted
            return status
        }
    }

    private class InMemoryKeyValueStore : NotificationPermissionRequestStore {
        var requested = false

        override suspend fun wasRequested(): Boolean = requested

        override suspend fun markRequested() {
            requested = true
        }

    }
}
