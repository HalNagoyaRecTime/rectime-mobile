# 通知既読・未読機能 実装変更メモ

## 1. 概要

通知の既読・未読機能として、モバイルアプリ側に以下を実装した。

- 通知一覧取得
- 未読件数取得
- 個別既読化
- 一括既読化
- 通知一覧画面での既読・未読表示
- ホーム画面、カレンダー画面の通知ベル未読バッジ表示

実装対象は Kotlin Multiplatform / Compose Multiplatform の共通 UI を中心とし、API ベース URL のみプラットフォーム別に解決する構成にした。

## 2. 追加・変更ファイル

### 2.1 通知機能

| ファイル | 内容 |
| --- | --- |
| `composeApp/src/commonMain/kotlin/com/rectime/mobile/feature/notifications/NotificationModels.kt` | 通知モデル、重要度、既読フィルタ、UI 状態を定義 |
| `composeApp/src/commonMain/kotlin/com/rectime/mobile/feature/notifications/NotificationsRepository.kt` | 通知 API 呼び出しを担当 |
| `composeApp/src/commonMain/kotlin/com/rectime/mobile/feature/notifications/NotificationJson.kt` | API レスポンス JSON をアプリ内モデルへ変換 |
| `composeApp/src/commonMain/kotlin/com/rectime/mobile/feature/notifications/NotificationsViewModel.kt` | 通知一覧、未読件数、既読操作の状態管理 |
| `composeApp/src/commonMain/kotlin/com/rectime/mobile/feature/notifications/NotificationUnreadStore.kt` | 通知ベル用の未読件数共有状態 |
| `composeApp/src/commonMain/kotlin/com/rectime/mobile/feature/notifications/NotificationBellBadge.kt` | 未読件数バッジ付き通知ベル UI |
| `composeApp/src/commonMain/kotlin/com/rectime/mobile/feature/notifications/NotificationsScreen.kt` | 通知一覧画面を実装 |

### 2.2 API 設定

| ファイル | 内容 |
| --- | --- |
| `composeApp/src/commonMain/kotlin/com/rectime/mobile/core/network/ApiConfig.kt` | API ベース URL の expect 定義 |
| `composeApp/src/androidMain/kotlin/com/rectime/mobile/core/network/ApiConfig.android.kt` | Android は `BuildConfig.API_BASE_URL` を利用 |
| `composeApp/src/iosMain/kotlin/com/rectime/mobile/core/network/ApiConfig.ios.kt` | iOS 用 API ベース URL |
| `composeApp/src/jvmMain/kotlin/com/rectime/mobile/core/network/ApiConfig.jvm.kt` | JVM 用 API ベース URL |

### 2.3 既存画面

| ファイル | 内容 |
| --- | --- |
| `composeApp/src/commonMain/kotlin/com/rectime/mobile/feature/home/HomeScreen.kt` | 通知ベルを `NotificationBellBadge` に差し替え |
| `composeApp/src/commonMain/kotlin/com/rectime/mobile/feature/calendar/CalendarScreen.kt` | 通知ベルを `NotificationBellBadge` に差し替え |
| `composeApp/build.gradle.kts` | `commonMain` に `ktor-client-core` を追加 |

## 3. 実装した API 連携

### 3.1 通知一覧取得

```txt
GET /api/v1/notifications
```

利用クエリ:

| パラメータ | 内容 |
| --- | --- |
| `read_status` | `all` / `read` / `unread` |
| `limit` | 既定値 50 |
| `offset` | 既定値 0 |

レスポンスは `NotificationJson` で `AppNotification` に変換する。

### 3.2 未読件数取得

```txt
GET /api/v1/notifications/unread-count
```

`unread_count` を読み取り、`NotificationUnreadStore` に反映する。

### 3.3 個別既読化

```txt
PATCH /api/v1/notifications/{id}/read
```

成功時は対象通知の `isRead` と `readAt` を更新し、未読件数を減らす。その後、最新状態と整合させるため再取得する。

### 3.4 一括既読化

```txt
PATCH /api/v1/notifications/read-all
```

成功時は未読件数を 0 にし、表示中の通知を既読状態へ更新する。その後、最新状態と整合させるため再取得する。

## 4. 画面仕様

### 4.1 通知一覧画面

通知一覧画面では以下を表示する。

- 未読件数
- 再読み込み状態
- 一括既読ボタン
- 既読状態フィルタ
  - すべて
  - 未読
  - 既読
- 通知一覧
- 読み込み状態
- 空状態
- エラー時の再試行導線

未読通知は既読通知と背景色、ドット、文字の太さで区別する。

### 4.2 個別既読操作

未読通知は以下の操作で既読化できる。

- 通知行をタップ
- 通知行の `既読` ボタンを押下

既読済み通知には既読日時が表示される。

### 4.3 通知ベル

ホーム画面とカレンダー画面の通知ベルに未読件数バッジを表示する。

- 未読件数が 1 件以上の場合のみ表示する
- 99 件を超える場合は `99+` と表示する
- 表示時に未読件数 API を取得し、共有状態へ反映する

## 5. 状態管理

`NotificationsViewModel` が通知画面の状態を管理する。

主な状態:

- `notifications`
- `unreadCount`
- `selectedFilter`
- `isLoading`
- `isRefreshing`
- `errorMessage`
- `actionInProgressIds`
- `isMarkingAllRead`

通知ベル用の未読件数は `NotificationUnreadStore` で共有する。

## 6. JSON 変換方針

`NotificationJson` で API レスポンスをアプリ内モデルへ変換する。

対応している主な項目:

| API 項目 | アプリ内項目 |
| --- | --- |
| `notification_id` / `id` | `id` |
| `type` | `type` |
| `title` | `title` |
| `message` | `message` |
| `link_url` | `linkUrl` |
| `severity` | `severity` |
| `is_read` | `isRead` |
| `read_at` | `readAt` |
| `created_at` | `createdAt` |
| `sent_at` | `sentAt` |

通知一覧レスポンスは、配列形式と `{ "notifications": [...] }` 形式の両方を想定している。

## 7. 検証結果

以下のコマンドで共通実装の JVM コンパイルを確認した。

```shell
.\gradlew.bat :composeApp:compileKotlinJvm
```

結果:

- 成功

Android コンパイルは以下のコマンドで確認しようとした。

```shell
.\gradlew.bat :composeApp:compileDebugKotlinAndroid
```

結果:

- 失敗
- 理由: Android SDK の場所が未設定
- `ANDROID_HOME` または `local.properties` の `sdk.dir` 設定が必要

## 8. 残課題

- Android SDK 設定後に Android ターゲットでのコンパイル確認を行う
- 実 API の認証方式に合わせて `NotificationsRepository` に認証ヘッダーを追加する
- API レスポンス形式が確定したら、JSON 変換を正式スキーマに合わせて調整する
- 通知タップ時の詳細画面遷移、または `linkUrl` ルーティングを実装する
- アプリ復帰時の未読件数再同期を追加する
