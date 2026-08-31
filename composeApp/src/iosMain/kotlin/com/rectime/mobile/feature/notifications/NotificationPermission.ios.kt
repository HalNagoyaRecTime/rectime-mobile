@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.rectime.mobile.feature.notifications

import kotlinx.coroutines.suspendCancellableCoroutine
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusEphemeral
import platform.UserNotifications.UNAuthorizationStatusNotDetermined
import platform.UserNotifications.UNAuthorizationStatusProvisional
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.coroutines.resume

internal fun createIosNotificationPermissionStartup(
    onPermissionGranted: () -> Unit,
): NotificationPermissionStartup =
    NotificationPermissionStartup(
        controller = IosNotificationPermissionController(onPermissionGranted),
        store = IosNotificationPermissionRequestStore,
    )

private object IosNotificationPermissionRequestStore : NotificationPermissionRequestStore {
    private val defaults = platform.Foundation.NSUserDefaults.standardUserDefaults
    private const val REQUESTED_KEY = "rectime_notification_permission_requested"

    override suspend fun wasRequested(): Boolean =
        defaults.stringForKey(REQUESTED_KEY) == "true"

    override suspend fun markRequested() {
        defaults.setObject("true", REQUESTED_KEY)
    }
}

private class IosNotificationPermissionController(
    private val onPermissionGranted: () -> Unit,
) : NotificationPermissionController {
    override suspend fun getStatus(): NotificationPermissionStatus =
        suspendCancellableCoroutine { continuation ->
            UNUserNotificationCenter.currentNotificationCenter()
                .getNotificationSettingsWithCompletionHandler { settings ->
                    if (continuation.isActive) {
                        continuation.resume(
                            settings?.authorizationStatus?.toPermissionStatus()
                                ?: NotificationPermissionStatus.Unavailable,
                        )
                    }
                }
        }

    override suspend fun requestPermission(): NotificationPermissionStatus {
        val status = getStatus()
        if (status != NotificationPermissionStatus.NotDetermined) return status

        return suspendCancellableCoroutine { continuation ->
            UNUserNotificationCenter.currentNotificationCenter().requestAuthorizationWithOptions(
                options = UNAuthorizationOptionAlert or
                    UNAuthorizationOptionBadge or
                    UNAuthorizationOptionSound,
            ) { _, _ ->
                UNUserNotificationCenter.currentNotificationCenter()
                    .getNotificationSettingsWithCompletionHandler { settings ->
                        if (!continuation.isActive) return@getNotificationSettingsWithCompletionHandler
                        val status = settings?.authorizationStatus?.toPermissionStatus()
                            ?: NotificationPermissionStatus.Unavailable
                        if (status == NotificationPermissionStatus.Granted) {
                            onPermissionGranted()
                        }
                        continuation.resume(status)
                    }
            }
        }
    }
}

private fun Long.toPermissionStatus(): NotificationPermissionStatus = when (this) {
    UNAuthorizationStatusAuthorized,
    UNAuthorizationStatusProvisional,
    UNAuthorizationStatusEphemeral,
    -> NotificationPermissionStatus.Granted
    UNAuthorizationStatusNotDetermined -> NotificationPermissionStatus.NotDetermined
    else -> NotificationPermissionStatus.Denied
}
