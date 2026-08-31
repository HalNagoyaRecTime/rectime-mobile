package com.rectime.mobile.feature.notifications

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

    @Test
    fun determinedStatusesDoNotRequestOrPersist() = runTest {
        listOf(
            NotificationPermissionStatus.Granted,
            NotificationPermissionStatus.Denied,
            NotificationPermissionStatus.Unavailable,
        ).forEach { status ->
            val store = InMemoryKeyValueStore()
            val controller = FakeNotificationPermissionController(status)
            val startup = NotificationPermissionStartup(controller, store)

            assertEquals(status, startup.requestIfNeeded())
            assertEquals(0, controller.requestCount)
            assertFalse(store.requested)
        }
    }

    @Test
    fun requestedInstallDoesNotRequestAgainWhenStatusIsNotDetermined() = runTest {
        val store = InMemoryKeyValueStore(requested = true)
        val controller = FakeNotificationPermissionController(NotificationPermissionStatus.NotDetermined)
        val startup = NotificationPermissionStartup(controller, store)

        assertEquals(NotificationPermissionStatus.NotDetermined, startup.requestIfNeeded())
        assertEquals(0, controller.requestCount)
    }

    @Test
    fun requestIsPersistedBeforeOpeningSystemDialog() = runTest {
        val events = mutableListOf<String>()
        val store = InMemoryKeyValueStore(onMarkRequested = { events += "persisted" })
        val controller = FakeNotificationPermissionController(
            initialStatus = NotificationPermissionStatus.NotDetermined,
            onRequest = { events += "requested" },
        )
        val startup = NotificationPermissionStartup(controller, store)

        startup.requestIfNeeded()

        assertEquals(listOf("persisted", "requested"), events)
    }

    private class FakeNotificationPermissionController(
        initialStatus: NotificationPermissionStatus = NotificationPermissionStatus.NotDetermined,
        private val onRequest: () -> Unit = {},
    ) : NotificationPermissionController {
        var status = initialStatus
        var requestCount = 0

        override suspend fun getStatus(): NotificationPermissionStatus = status

        override suspend fun requestPermission(): NotificationPermissionStatus {
            requestCount += 1
            onRequest()
            status = NotificationPermissionStatus.Granted
            return status
        }
    }

    private class InMemoryKeyValueStore(
        var requested: Boolean = false,
        private val onMarkRequested: () -> Unit = {},
    ) : NotificationPermissionRequestStore {

        override suspend fun wasRequested(): Boolean = requested

        override suspend fun markRequested() {
            requested = true
            onMarkRequested()
        }
    }
}
