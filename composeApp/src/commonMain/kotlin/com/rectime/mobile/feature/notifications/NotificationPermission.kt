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

interface NotificationPermissionRequestStore {
    suspend fun wasRequested(): Boolean

    suspend fun markRequested()
}

/** 初回起動時だけOSの通知権限を要求する。 */
class NotificationPermissionStartup(
    private val controller: NotificationPermissionController,
    private val store: NotificationPermissionRequestStore,
) {
    suspend fun requestIfNeeded(): NotificationPermissionStatus {
        if (store.wasRequested()) {
            return controller.getStatus()
        }

        val status = controller.getStatus()
        return if (status == NotificationPermissionStatus.NotDetermined) {
            store.markRequested()
            controller.requestPermission()
        } else {
            status
        }
    }
}
