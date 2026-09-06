package com.rectime.mobile.feature.notifications

/** OS が管理する通知許可の状態。 */
enum class NotificationPermissionStatus {
    Granted,
    NotDetermined,
    Denied,
    Unavailable,
}

internal fun NotificationPermissionStatus.description(): String = when (this) {
    NotificationPermissionStatus.Granted -> "通知は許可されています"
    NotificationPermissionStatus.NotDetermined -> "通知の許可を選択してください"
    NotificationPermissionStatus.Denied -> "端末の設定で通知を許可してください"
    NotificationPermissionStatus.Unavailable -> "この端末では通知設定を確認できません"
}

interface NotificationPermissionController {
    suspend fun getStatus(): NotificationPermissionStatus

    /** 未選択ならOSの許可ダイアログを表示する。 */
    suspend fun requestPermission(): NotificationPermissionStatus

    /** OSの通知設定画面を開く。 */
    fun openSystemSettings()
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
    /** 現在のOS通知許可状態を取得する。 */
    suspend fun getStatus(): NotificationPermissionStatus = controller.getStatus()

    /** アプリ内からOSの通知設定画面へ遷移する。 */
    fun openSystemSettings() = controller.openSystemSettings()

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
