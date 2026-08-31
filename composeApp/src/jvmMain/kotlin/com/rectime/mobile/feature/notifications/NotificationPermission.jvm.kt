package com.rectime.mobile.feature.notifications

actual fun notificationPermissionController(): NotificationPermissionController =
    UnavailableNotificationPermissionController

actual fun notificationPermissionRequestStore(): NotificationPermissionRequestStore =
    JvmNotificationPermissionRequestStore

private object JvmNotificationPermissionRequestStore : NotificationPermissionRequestStore {
    private val preferences = java.util.prefs.Preferences.userRoot()
        .node("com/rectime/mobile/notification-permission")
    private const val REQUESTED_KEY = "requested"

    override suspend fun wasRequested(): Boolean = preferences.getBoolean(REQUESTED_KEY, false)

    override suspend fun markRequested() {
        preferences.putBoolean(REQUESTED_KEY, true)
    }
}

private object UnavailableNotificationPermissionController : NotificationPermissionController {
    override suspend fun getStatus(): NotificationPermissionStatus = NotificationPermissionStatus.Unavailable

    override suspend fun requestPermission(): NotificationPermissionStatus =
        NotificationPermissionStatus.Unavailable
}
