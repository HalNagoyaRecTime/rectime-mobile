package com.rectime.mobile.feature.notifications

actual fun notificationPermissionController(): NotificationPermissionController =
    UnavailableNotificationPermissionController

private object UnavailableNotificationPermissionController : NotificationPermissionController {
    override suspend fun getStatus(): NotificationPermissionStatus = NotificationPermissionStatus.Unavailable

    override suspend fun requestPermissionOrOpenSettings(): NotificationPermissionStatus =
        NotificationPermissionStatus.Unavailable

    override fun openSystemSettings() = Unit
}
