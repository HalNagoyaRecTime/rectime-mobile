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

    /** 未選択ならOSの許可ダイアログを表示し、選択済みならOS設定を開く。 */
    suspend fun requestPermissionOrOpenSettings(): NotificationPermissionStatus

    fun openSystemSettings()
}

expect fun notificationPermissionController(): NotificationPermissionController

internal fun NotificationPermissionStatus.isGranted(): Boolean = this == NotificationPermissionStatus.Granted

internal fun NotificationPermissionStatus.description(): String = when (this) {
    NotificationPermissionStatus.Granted -> "通知は許可されています"
    NotificationPermissionStatus.NotDetermined -> "通知の許可を選択してください"
    NotificationPermissionStatus.Denied -> "端末の設定で通知を許可してください"
    NotificationPermissionStatus.Unavailable -> "この端末では通知設定を確認できません"
}
