# ランキング画面の所属チーム取得に関する調査レポート

調査日: 2026-09-15

## 結論

現在確認できるモバイル実装と OpenAPI 定義だけでは、自分の所属チームを確実に特定する取得経路は完成していない。クラス情報には `team_id` があるが、認証ユーザーのモデルには `classRoomId` と `teamId` がない。

ランキングの所属チーム判定には、`GET /api/v1/auth/me` で `teamId` を返し、ランキングの `team_id` と比較する方式を提案する。これは API の変更案であり、現在のレスポンス仕様として確認したものではない。

## 確認できた現状

| 対象 | 確認内容 |
| --- | --- |
| `RankingScreen.kt` | `isMyTeam` が真の行を強調表示し、その行への自動スクロールを行う処理がある。 |
| `RankingsResponse.kt` | 各ランキングに `teamId` があるが、モデル変換時の `isMyTeam` は `false` 固定。 |
| `RankingViewModel.kt` | ランキングを取得し、通信成功時・キャッシュ利用時に表示モデルへ変換している。所属チームを取得・照合する処理はない。 |
| `AuthApi.kt` / `AuthModels.kt` | ユーザー情報には `classRoomName` と `studentIdNumber` があるが、クラス ID・チーム ID の受け取り定義はない。 |
| `openapi.json` | 自分の所属チームを直接返すエンドポイント、および `/api/v1/auth/me` の定義は見当たらない。 |

そのため、現状の変換処理では所属チームの強調表示・自動スクロールの対象が作られない。

## クラスを経由して取得する場合

OpenAPI 上では、次の取得経路を構成できる。

1. 自分の `class_room_id` を取得する。
2. `GET /api/v1/classrooms/{classId}` のレスポンスから `team_id` を取得する。
3. 必要に応じて `GET /api/v1/teams/{teamId}` でチーム詳細を取得する。

ただし、手順 1 の取得方法は現状の認証モデルでは提供されていない。先の説明にあった「自分の `class_room_id` を取得する」は、この不足が解決済みであることを前提にした手順であり、そのまま実装できると確認したものではない。

また、教室取得 API には 403 応答が定義されており、学生権限で利用できるかは仕様だけでは断定できない。`classRoomName` と教室一覧の名前を照合する方式も、名前の一意性や対応関係を確認できていないため、確実な取得方法とは扱わない。

## 提案する対応

### バックエンド

- `/api/v1/auth/me` の実際のレスポンスに `teamId` または `classRoomId` が含まれるか確認する。
- `teamId` がなければ、認証ユーザーの所属から解決した `teamId` を返す仕様を追加する。
- 未所属ユーザーの値を `null` とするなど、未所属時の仕様を定める。複数クラスを担当する教員などについては、単一チームとして扱えるかを決める。
- 認証 API のレスポンス仕様を OpenAPI に記載する。

### モバイル

- `AuthUserResponse` と `AuthUser` に、合意した仕様に合わせて `teamId` を追加する。
- セッションの保存・復元処理にも反映し、既存の保存データとの互換性を保つ。
- 所属チーム ID をランキングのモデル変換へ渡し、次の条件で判定する。

```kotlin
isMyTeam = myTeamId != null && teamId == myTeamId
```

- 通信成功時とキャッシュ利用時の両方で、現在のログインユーザーに基づいて判定する。
- 所属情報がランキングより遅れて届く場合に、自動スクロールが実行されるようにする。現在は対象が見つからなくても `hasAutoScrolled` を真にするため、後から所属情報を反映してもスクロールされない可能性がある。

ランキングの強調表示だけなら、チーム詳細 API の追加呼び出しは不要。

## 実装後の確認項目

- 所属チームと一致する行だけが強調表示される。
- 所属情報の取得順序にかかわらず、対象行へ自動スクロールする。
- 未所属・所属情報取得失敗・取得済みランキング内に所属チームがない場合に、誤った行を強調しない。
- キャッシュ表示やアカウント切り替え時に、別ユーザーの所属判定が残らない。
- 既存の保存済みセッションを引き続き読み込める。

## 調査範囲と参照ファイル

ローカルの仕様・コードを確認した。実 API のレスポンス、バックエンドの実装、学生権限での API 呼び出しは未検証。本レポートではアプリや API の実装変更は行っていない。

- [openapi.json](../openapi.json)
- [RankingScreen.kt](../composeApp/src/commonMain/kotlin/com/rectime/mobile/feature/ranking/RankingScreen.kt)
- [RankingViewModel.kt](../composeApp/src/commonMain/kotlin/com/rectime/mobile/feature/ranking/RankingViewModel.kt)
- [RankingsResponse.kt](../composeApp/src/commonMain/kotlin/com/rectime/mobile/core/network/RankingsResponse.kt)
- [AuthApi.kt](../composeApp/src/commonMain/kotlin/com/rectime/mobile/feature/auth/AuthApi.kt)
- [AuthModels.kt](../composeApp/src/commonMain/kotlin/com/rectime/mobile/feature/auth/AuthModels.kt)
- [AuthSessionCodec.kt](../composeApp/src/commonMain/kotlin/com/rectime/mobile/feature/auth/AuthSessionCodec.kt)
