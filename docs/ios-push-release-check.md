# iOS Push通知のリリース確認

iOSのPush通知は、アプリ内の登録処理だけでは動作しません。App ID、Provisioning Profile、署名済みアプリのすべてがPush Notificationsに対応している必要があります。

## リポジトリで保証する設定

- Xcode targetでPush Notifications capabilityを有効にする
- `iosApp.entitlements`をコード署名に使用する
- ReleaseではApple Distribution署名を要求する
- Debug／Stagingではsandbox APNs、Releaseではproduction APNsを要求する
- CIでReleaseの`CODE_SIGN_ENTITLEMENTS`と`APS_ENVIRONMENT`を確認する

CIにはAppleの配布証明書とProvisioning Profileを置いていないため、実際の署名結果までは確認できません。
Staging構成をTestFlightなどで配布する場合は、開発署名のまま使わず、配布専用の構成とproduction APNs設定を別途用意してください。

## Apple・Firebase側で必要な設定

1. Apple DeveloperのApp ID `io.github.halnagoyarectime` でPush Notificationsを有効にする
2. Push Notifications有効化後のDistribution Provisioning ProfileをXcodeへ反映する
3. Firebase ConsoleのiOSアプリにAPNs認証キーまたは証明書を登録する

Automatic Signingを使う場合も、Archive時に正しいTeamとApp IDが選ばれていることを確認してください。

## 配布前の確認

1. Release構成で実機用Archiveを作成する
2. 次のコマンドでArchiveを検証する

   ```sh
   ./scripts/verify-ios-release-entitlements.sh "/path/to/RECREATION.xcarchive"
   ```

3. `production APNsに対応しています` と表示された同じArchiveをApp Store Connectへアップロードする
4. TestFlightを実機へインストールし、通知許可後にPush通知の受信を確認する

検証スクリプトは、署名済みアプリと埋め込まれたProvisioning Profileの両方で`aps-environment=production`になっている場合だけ成功します。
