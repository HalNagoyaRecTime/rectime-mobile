@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.rectime.mobile.feature.notifications

import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusEphemeral
import platform.UserNotifications.UNAuthorizationStatusNotDetermined
import platform.UserNotifications.UNAuthorizationStatusProvisional
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.coroutines.resume

actual fun notificationPermissionController(): NotificationPermissionController =
    IosNotificationPermissionController

private object IosNotificationPermissionController : NotificationPermissionController {
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

    override suspend fun requestPermissionOrOpenSettings(): NotificationPermissionStatus {
        if (getStatus() != NotificationPermissionStatus.NotDetermined) {
            openSystemSettings()
            return getStatus()
        }

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
                        continuation.resume(status)
                    }
            }
        }
    }

    override fun openSystemSettings() {
        val url = NSURL.URLWithString(UIApplicationOpenSettingsURLString) ?: return
        UIApplication.sharedApplication.openURL(
            url = url,
            options = emptyMap<Any?, Any>(),
            completionHandler = null,
        )
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
