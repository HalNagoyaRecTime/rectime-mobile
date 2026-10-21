# iOS Push通知のリリース確認

iOSのPush通知は、アプリ内の登録処理だけでは動作しません。App ID、Provisioning Profile、署名済みアプリのすべてがPush Notificationsに対応している必要があります。

## リポジトリで保証する設定

- Xcode targetでPush Notifications capabilityを有効にする
- `iosApp.entitlements`をコード署名に使用する
- Automatic SigningでArchive時の開発署名をXcodeに任せる
- CIで`CODE_SIGN_ENTITLEMENTS`とPush Notifications capabilityを確認する

CIにはAppleの配布証明書とProvisioning Profileを置いていないため、実際の署名結果までは確認できません。
Staging構成をTestFlightなどで配布する場合は、開発署名のまま使わず、配布専用の構成とproduction APNs設定を別途用意してください。

Automatic Signingでは、Archive内のアプリが開発署名になることがあります。XcodeのOrganizerで「Distribute App」からTestFlight／App Store向けに配布するとき、配布用Provisioning Profileで再署名され、`aps-environment`が`production`になります。そのため、確認対象はArchive内のアプリではなく、配布処理後のIPAです。

## Apple・Firebase側で必要な設定

1. Apple DeveloperのApp ID `io.github.halnagoyarectime` でPush Notificationsを有効にする
2. Push Notifications有効化後のDistribution Provisioning ProfileをXcodeへ反映する
3. Firebase ConsoleのiOSアプリにAPNs認証キーまたは証明書を登録する

Automatic Signingを使う場合も、Archive時に正しいTeamとApp IDが選ばれていることを確認してください。

## 配布前の確認

1. Release構成でArchiveを作成する
2. Organizerの「Distribute App」でTestFlight／App Store向け・Automatic Signingを選び、配布用IPAをExportする
3. 次のコマンドで配布用IPAを検証する

   ```sh
   ./scripts/verify-ios-release-entitlements.sh "/path/to/RECREATION.ipa"
   ```

4. `production APNsに対応しています` と表示された同じ配布物をApp Store Connectへアップロードする
5. TestFlightを実機へインストールし、通知許可後にPush通知の受信を確認する

検証スクリプトは、署名済みアプリと埋め込まれたProvisioning Profileの両方で`aps-environment=production`になっている場合だけ成功します。
