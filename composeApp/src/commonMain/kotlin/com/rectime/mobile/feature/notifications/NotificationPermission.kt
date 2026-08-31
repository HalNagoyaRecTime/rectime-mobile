package com.rectime.mobile.feature.notifications

/** OS が管理する通知許可の状態。 */
enum class NotificationPermissionStatus {
    Granted,
    NotDetermined,
    Denied,
    Unavailable,
}

interface NotificationPermissionController {
    suspend fun getStatus(): NotificationPermissionStatus

    /** 未選択ならOSの許可ダイアログを表示する。 */
    suspend fun requestPermission(): NotificationPermissionStatus
}

expect fun notificationPermissionController(): NotificationPermissionController

interface NotificationPermissionRequestStore {
    suspend fun wasRequested(): Boolean

    suspend fun markRequested()
}

expect fun notificationPermissionRequestStore(): NotificationPermissionRequestStore

/** 初回起動時だけOSの通知権限を要求する。 */
class NotificationPermissionStartup(
    private val controller: NotificationPermissionController = notificationPermissionController(),
    private val store: NotificationPermissionRequestStore = notificationPermissionRequestStore(),
) {
    suspend fun requestIfNeeded(): NotificationPermissionStatus {
        if (store.wasRequested()) {
            return controller.getStatus()
        }

        val status = controller.getStatus()
        store.markRequested()
        return if (status == NotificationPermissionStatus.NotDetermined) {
            controller.requestPermission()
        } else {
            status
        }
    }
}
