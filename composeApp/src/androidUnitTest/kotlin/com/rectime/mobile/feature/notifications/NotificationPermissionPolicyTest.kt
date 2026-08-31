package com.rectime.mobile.feature.notifications

import kotlin.test.Test
import kotlin.test.assertEquals

class NotificationPermissionPolicyTest {
    @Test
    fun `pre Android 13 uses notification manager status`() {
        assertEquals(
            NotificationPermissionStatus.Granted,
            resolveAndroidNotificationPermissionStatus(
                runtimePermissionRequired = false,
                runtimePermissionGranted = true,
                notificationsEnabled = true,
                shouldShowRationale = false,
                wasRequested = false,
            ),
        )
        assertEquals(
            NotificationPermissionStatus.Denied,
            resolveAndroidNotificationPermissionStatus(
                runtimePermissionRequired = false,
                runtimePermissionGranted = true,
                notificationsEnabled = false,
                shouldShowRationale = false,
                wasRequested = false,
            ),
        )
    }

    @Test
    fun `granted runtime permission still respects notification manager status`() {
        assertEquals(
            NotificationPermissionStatus.Granted,
            resolveAndroidNotificationPermissionStatus(
                runtimePermissionRequired = true,
                runtimePermissionGranted = true,
                notificationsEnabled = true,
                shouldShowRationale = false,
                wasRequested = true,
            ),
        )
        assertEquals(
            NotificationPermissionStatus.Denied,
            resolveAndroidNotificationPermissionStatus(
                runtimePermissionRequired = true,
                runtimePermissionGranted = true,
                notificationsEnabled = false,
                shouldShowRationale = false,
                wasRequested = true,
            ),
        )
    }

    @Test
    fun `first runtime permission request is not determined`() {
        assertEquals(
            NotificationPermissionStatus.NotDetermined,
            resolveAndroidNotificationPermissionStatus(
                runtimePermissionRequired = true,
                runtimePermissionGranted = false,
                notificationsEnabled = false,
                shouldShowRationale = false,
                wasRequested = false,
            ),
        )
    }

    @Test
    fun `requested runtime permission without rationale is denied`() {
        assertEquals(
            NotificationPermissionStatus.Denied,
            resolveAndroidNotificationPermissionStatus(
                runtimePermissionRequired = true,
                runtimePermissionGranted = false,
                notificationsEnabled = false,
                shouldShowRationale = false,
                wasRequested = true,
            ),
        )
    }

    @Test
    fun `runtime permission with rationale is denied`() {
        assertEquals(
            NotificationPermissionStatus.Denied,
            resolveAndroidNotificationPermissionStatus(
                runtimePermissionRequired = true,
                runtimePermissionGranted = false,
                notificationsEnabled = false,
                shouldShowRationale = true,
                wasRequested = true,
            ),
        )
    }
}
