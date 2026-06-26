# 通知一覧 API 未実装問題の調査と対応案

## 1. 現象

モバイルアプリの通知一覧画面で `HTTP 404: 404 Not Found` が表示され、通知が一覧に反映されない。プッシュ通知自体は端末に届く。

## 2. 原因

モバイルアプリが呼び出す通知一覧系 API が、本番バックエンド（`https://rectime-api.rectime-project.workers.dev`）に存在しないため。

```txt
GET /api/v1/notifications
GET /api/v1/notifications/unread-count
PATCH /api/v1/notifications/{id}/read
PATCH /api/v1/notifications/read-all
```

本番ルート (`/`) のレスポンスにこれらのエンドポイントが含まれておらず、実際に `GET /api/v1/notifications` を呼ぶと 404 が返ることを確認した。

```shell
curl -s "https://rectime-api.rectime-project.workers.dev/"
# => endpoints に notifications 系の一覧取得・既読化 API が含まれていない

curl -s -o /dev/null -w "%{http_code}\n" \
  "https://rectime-api.rectime-project.workers.dev/api/v1/notifications?student_number=24A001&read_status=all&limit=50&offset=0"
# => 404
```

## 3. バックエンド (`rectime-api`) の状況調査

### 3.1 ブランチが複数系統に分岐している

`rectime-api` リポジトリには大きく2つの系統がある。

| 系統 | 内容 |
| --- | --- |
| `notification` ブランチ | 古い3層構造（`src/controllers` / `src/services` / `src/repositories` を直下に配置）。通知一覧取得・未読数取得・既読化 API を実装済み。 |
| `develop` / `fix/28-protect-notification-api` 系 | 新しい4層構造（`domain` / `application` / `infrastructure` / `presentation` + DI コンテナ）へリファクタリング済み。通知一覧・既読化 API は未実装。 |

本番にデプロイされているのは新4層構造の系統（ルートのレスポンスが `"Four Layer Architecture"` を返すことで確認）。`notification` ブランチをそのままマージ・デプロイすることはできない（ディレクトリ構成が根本的に異なるため）。

### 3.2 本番 D1 データベースの実態

本番 D1 (`rectime-api`) に対して `wrangler d1 execute --remote` で直接確認した。

```txt
テーブル一覧:
  t_events, users, firebase_tokens, notifications, notification_send_logs,
  m_class_rooms, m_users, m_student_description, t_entries
```

- 新旧スキーマが両方残っている（`m_students` は削除済み、生徒データは `m_student_description.f_student_id_number`）
- `users.student_number` には `m_student_description.f_student_id_number` と同じ形式の値（例: `24A001`）が入っており、Firebase トークン登録はこの `users` / `firebase_tokens` テーブルに対して行われている（登録自体は機能しており、本番に `users` 1件・`firebase_tokens` 3件が存在）
- `notifications` テーブルは **0 件**。さらに列が `id, type, title, body, created_at` のみで、`user_id` や `read_at` などモバイル向け一覧表示に必要な列が存在しない（MVP 初期マイグレーションのまま）
- `ScheduledNotificationService`（10分前リマインダー送信）は FCM 送信は行うが、`notifications` テーブルへの記録（INSERT）処理がない

### 3.3 本番マイグレーション適用状況

```txt
0001_initial_schema.sql
0002_seed_data.sql
0003_create_notification_mvp_tables.sql
0004_create_notification_send_logs.sql
0003_update_student_schema.sql
0005_recreate_entries_for_student_schema.sql
0006_align_student_numbers_with_firebase_users.sql
```

`origin/fix/28-protect-notification-api`（2026-06-24 時点の最新、`0007_add_event_date_to_events.sql` まで含む）が本番にデプロイされているコードに最も近いと判断した。このブランチには「イベント参加者のみへの通知配信」(`findActiveTokensForEvents`) は実装済みだが、モバイル向けの一覧取得・既読化 API は無い。

## 4. 調査時点でのローカルの変更について

調査・検証のため、リポジトリに対して以下の操作を行った。

- `c:\Users\kuram\rec\rectime-api`（`notification` ブランチ、ユーザー作業ディレクトリ）: 旧3層構造のコードに対し、一覧取得・既読化・通知送信 API 周りの修正を試みた変更が存在する。**本番にデプロイされていない3層構造側のコードであり、現在の本番（4層構造）とは互換しない**。
- `C:\Users\kuram\AppData\Local\Temp\rectime-api-work`（git worktree、ブランチ `feature/notification-list-api`、ベース: `origin/fix/28-protect-notification-api`）: 本番系統の4層構造に合わせて一覧取得・未読数取得・既読化 API を新規実装した。**commit / push / 本番デプロイは未実施**（ユーザー指示により保留中）。

いずれも本番には反映されていないため、現時点でモバイルアプリから動作確認しても通知一覧 API は 404 のままとなる。

## 5. `feature/notification-list-api` worktree での実装内容（未 commit）

`origin/fix/28-protect-notification-api` をベースに、既存の4層アーキテクチャの規約（`domain/entities`, `domain/interfaces/repositories`, `infrastructure/repositories`, `application/services`, `presentation/controllers`, `di/container.ts`）に沿って実装した。

### 5.1 変更・追加ファイル

| ファイル | 内容 |
| --- | --- |
| `migrations/0008_extend_notifications_for_list_api.sql`（新規） | `notifications` テーブルに `user_id`, `link_url`, `resource_type`, `resource_id`, `severity`, `send_status`, `sent_at`, `read_at`, `updated_at` を追加。`user_id` にインデックスを追加。 |
| `src/domain/entities/Notification.ts`（新規） | `NotificationEntity` 等のドメイン型を定義。 |
| `src/domain/interfaces/repositories/INotificationRepository.ts`（新規） | 通知リポジトリのインターフェース定義。 |
| `src/infrastructure/repositories/NotificationRepository.ts`（新規） | D1 への実装。`student_number` から `users` 経由で対象を絞り込む。 |
| `src/application/services/INotificationService.ts`（新規） | 通知サービスのインターフェース定義。 |
| `src/application/services/NotificationService.ts`（新規） | サービス実装。 |
| `src/presentation/controllers/NotificationController.ts`（既存に追記） | `getNotifications` / `getUnreadCount` / `markNotificationAsRead` / `markAllNotificationsAsRead` を追加。既存の `sendTestNotification` は維持。 |
| `src/application/services/ScheduledNotificationService.ts`（既存に追記） | FCM 送信成功時に `notifications` テーブルへ INSERT する処理を追加。 |
| `src/di/container.ts`（既存に追記） | `notificationRepository` / `notificationService` の生成・注入を追加。 |
| `src/index.ts`（既存に追記） | 以下のルートを追加。CORS の `allowMethods` に `PATCH` を追加。 |

```txt
GET   /api/v1/notifications
GET   /api/v1/notifications/unread-count
PATCH /api/v1/notifications/read-all
PATCH /api/v1/notifications/:id/read
```

### 5.2 確認済みの事項

- `npm run type-check`（`tsc --noEmit`）: エラーなし
- `npm run lint`（`eslint .`）: エラーなし
- このブランチにはテストフレームワーク（vitest 等）がまだ導入されておらず、自動テストは追加していない

### 5.3 未実施の事項（ユーザー確認待ち）

- ローカルブランチの commit
- GitHub への push / PR 作成
- 本番 D1 へのマイグレーション適用（`wrangler d1 migrations apply rectime-api --remote`）
- 本番 Cloudflare Workers への `wrangler deploy`

## 6. 今後の進め方の論点

- `rectime-api` は 2026-06-24 時点でも他のコントリビューター（`fix/28-protect-notification-api` ブランチの作者）が活発に開発を続けている。今回の実装をそのまま push すると作業が重複・競合する可能性があるため、push 前にチームへの確認が必要。
- 本番デプロイ・本番 D1 マイグレーション適用は不可逆性のある操作のため、実行前に明示的な承認を得る。
- モバイル側 (`rectime-mobile`) の `NotificationsRepository` は `MockUser.me.studentId`（固定値 `24A001`）に依存した暫定実装のままであり、これは今回のバックエンド対応とは独立した既知の課題（`doc/notification-read-status-implementation.md` 9章参照）。
