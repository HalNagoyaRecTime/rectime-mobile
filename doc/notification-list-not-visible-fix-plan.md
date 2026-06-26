# 通知一覧にプッシュ通知が反映されない不具合 修正方針

## 1. 症状

- FCM プッシュ通知は端末に届く（通知バーに表示される）
- 同じ通知がアプリ内の通知一覧（`NotificationsScreen`）には表示されない
- 未読件数バッジも反映されない

## 2. 原因

`NotificationsRepository` がバックエンド API を呼び出す際、リクエストにユーザーを識別する情報を一切付与していない。

- 対象: `composeApp/src/commonMain/kotlin/com/rectime/mobile/feature/notifications/NotificationsRepository.kt`
  - `getNotifications()` (17-30行)
  - `getUnreadCount()` (32-37行)
  - `markRead()` (39-44行)
  - `markAllRead()` (46-51行)
- いずれも `client.get(...)` / `client.patch(...)` を認証情報なしで呼んでいる

一方、要件定義書 `doc/notification-read-status-requirements.md` では各 API は「ログインユーザー自身」の通知のみを返す仕様であり、401（未認証）がエラーケースとして明記されている（5章・6章）。

FCM トークン登録 (`composeApp/src/androidMain/kotlin/com/rectime/mobile/notification/FcmTokenRegistrar.kt:23,43`) は `MockUser.me.studentId` を body に含めてユーザーを識別しているのに対し、`NotificationsRepository` には同様の仕組みがない。

この結果、バックエンド送信・DB保存（FCM経路）は正常に行われる一方、一覧取得 API 側はユーザーを特定できず、空または認証エラー相当の結果になり、一覧に反映されない。

なお、この欠落は実装時点で `doc/notification-read-status-implementation.md` の「8. 残課題」に「実 API の認証方式に合わせて `NotificationsRepository` に認証ヘッダーを追加する」として既に把握されていたが、未対応のまま残っている。

## 3. 修正方針

### 3.1 短期対応（暫定）

現状の `FcmTokenRegistrar` と同様に `MockUser.me.studentId` を全リクエストに付与し、症状を解消する。

- `NotificationsRepository` の各メソッドに `studentNumber` パラメータ（クエリ or ヘッダー）を追加
- バックエンド側の `studentNumber` 解決ロジック（ヘッダー `X-Student-Number` / クエリ `studentNumber` / `student_number`）のうち、どれを正とするか確認し統一する
- `markRead` / `markAllRead` の PATCH リクエストにも同様に付与する

この対応は `MockUser` に依存した暫定実装であることを明記し、本対応で置き換える前提とする。

### 3.2 本対応（認証方式に合わせる）

`MockUser` は仮のユーザー情報であり、本来は認証済みユーザーのトークン等から解決すべきもの。本対応では以下を行う。

1. アプリ全体の認証方式（ログイン機構、トークン管理）を確認し、`NotificationsRepository` を含む API クライアントに認証情報を一元的に付与する仕組みを導入する
   - 例: `HttpClient` に `defaultRequest` / `Auth` プラグインで `Authorization` ヘッダーを自動付与
2. `FcmTokenRegistrar` も含め、`MockUser.me.studentId` への直接依存をなくし、共通の認証コンテキストから取得するように統一する
3. バックエンド側の `GET /api/v1/notifications` 系 API が、認証ヘッダーからユーザーを解決する前提になっているか確認する（クエリパラメータでの studentNumber 渡しは暫定運用として廃止する想定）

## 4. 影響範囲

- `composeApp/src/commonMain/kotlin/com/rectime/mobile/feature/notifications/NotificationsRepository.kt`
- `composeApp/src/commonMain/kotlin/com/rectime/mobile/feature/notifications/NotificationsViewModel.kt`（Repository 呼び出し元、必要に応じて studentNumber 受け渡し）
- `composeApp/src/androidMain/kotlin/com/rectime/mobile/notification/FcmTokenRegistrar.kt`（本対応時に認証コンテキスト共通化）
- バックエンド側 API（別リポジトリ。studentNumber 解決方法の確認が必要）

## 5. 確認手順

1. Android Logcat で `GET /api/v1/notifications` 等のレスポンスステータスを確認し、401 / 400 / 空配列のいずれになっているか裏取りする
2. 短期対応実装後、実機でプッシュ通知受信 → 通知一覧画面を開き、該当通知が表示され未読バッジが更新されることを確認する
3. 既読化（個別・一括）も同様に動作確認する
4. 他ユーザー宛の通知が見えないこと（studentNumber 違いのデータで確認）

## 6. 残課題・懸念

- バックエンド側で studentNumber 偽装によるなりすましが可能な状態になっていないか確認が必要（クエリパラメータでの ID 渡しは本来的に脆弱）
- 本対応（認証ヘッダー化）の優先度・スケジュールは別途決定する
