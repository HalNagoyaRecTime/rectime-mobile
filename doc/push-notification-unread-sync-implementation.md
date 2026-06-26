# プッシュ通知受信時の未読件数同期 実装メモ

## 1. 概要

Android 端末がフォアグラウンド状態で FCM プッシュ通知を受信した際、通知ベルの未読件数バッジが更新されない問題があった。`NotificationUnreadStore` の未読件数は次のタイミングでのみ更新されており、プッシュ受信そのものをトリガーにした更新がなかったため。

- `NotificationBellBadge` 表示時
- `NotificationsViewModel` の `refresh` / `markRead` / `markAllRead`

これに対し、FCM メッセージ受信時に未読件数 API を呼び直し、`NotificationUnreadStore` に反映する処理を追加した。

## 2. 変更ファイル

| ファイル | 内容 |
| --- | --- |
| `composeApp/src/androidMain/kotlin/com/rectime/mobile/notification/RectimeFirebaseMessagingService.kt` | `onMessageReceived` で通知表示後に未読件数を再取得し、`NotificationUnreadStore` を更新する処理を追加 |

## 3. 実装内容

`RectimeFirebaseMessagingService` に `NotificationsRepository` のインスタンスと、IO ディスパッチャ上で動く `CoroutineScope` を持たせた。

```kotlin
private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
private val notificationsRepository = NotificationsRepository()
```

`onMessageReceived` でプッシュ通知をシステム通知として表示した直後に `refreshUnreadCount()` を呼ぶ。

```kotlin
override fun onMessageReceived(message: RemoteMessage) {
    super.onMessageReceived(message)
    ...
    showNotification(title, body)
    refreshUnreadCount()
}

private fun refreshUnreadCount() {
    scope.launch {
        runCatching {
            notificationsRepository.getUnreadCount()
        }.onSuccess { count ->
            NotificationUnreadStore.setCount(count)
        }.onFailure { error ->
            Log.w(TAG, "Failed to refresh unread count after push", error)
        }
    }
}
```

`GET /api/v1/notifications/unread-count` をサーバーに問い合わせて取得した値で更新するため、FCM メッセージの payload 内容（data フィールドの未読数など）をクライアント側で信用する必要がない。サーバー側の状態と必ず一致する。

## 4. 動作の流れ

1. バックエンドが FCM 経由でプッシュ通知を送信する
2. `RectimeFirebaseMessagingService.onMessageReceived` が呼ばれる
3. システム通知を表示する（既存処理）
4. `notificationsRepository.getUnreadCount()` で最新の未読件数を取得する
5. `NotificationUnreadStore.setCount()` で共有状態を更新する
6. `NotificationBellBadge` が `collectAsStateWithLifecycle` で状態を購読しているため、フォアグラウンドの画面のバッジが自動的に再描画される

## 5. 対象外・既知の制約

- iOS 側は FCM / APNs 連携自体が未実装のため、本対応の対象外
- アプリがバックグラウンド/終了状態で受信した場合、`onMessageReceived` はシステムが通知を直接表示するケースがあり（notification ペイロードのみの場合）、その際は本処理が呼ばれずバッジ更新は次回のアプリ起動時 (`NotificationBellBadge` 表示時) に行われる
- `NotificationsRepository` は `MockUser.me.studentId` に依存した暫定実装のままであり、認証方式が確定した際に合わせて置き換える前提（`doc/notification-read-status-implementation.md` 9章参照）
