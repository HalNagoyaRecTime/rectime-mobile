# Firebase iOS設定

Firebase ConsoleでBundle ID `io.github.halnagoyarectime` のiOSアプリを登録し、
設定ファイルを次の場所へ配置する。

```text
iosApp/iosApp/GoogleService-Info.plist
```

このファイルはGit管理しない。ビルド検証のCI（build-ios）では配置しない（実行時にFCMが無効になるだけでビルドには影響しない）。
配布用ビルドを行うCIを追加する場合は、同じパスに復元する。

Firebase ConsoleのCloud Messaging設定には、Apple Push Notification Authentication Key
（`.p8`、Key ID、Team ID）を登録する。Apple Developer側では同じBundle IDのApp IDで
Push Notificationsを有効化し、`aps-environment`を含むProvisioning Profileを使用する。

リポジトリでは`iosApp.entitlements`をXcodeの署名設定へ適用し、
Debug／Stagingに`development`、Releaseに`production`の`aps-environment`を設定している。
Apple Developer側の準備が未完了の場合は、署名付き実機ビルドを成功扱いにせず外部設定を完了する。

配布前の署名確認は[`docs/ios-push-release-check.md`](../docs/ios-push-release-check.md)に従う。

## Bundle ID変更時の外部設定

iOSのBundle IDを変更した場合は、Repositoryの変更だけでは配布できません。

- Apple Developerで同じBundle IDのApp IDを用意する
- 対応するProvisioning Profileを更新する
- Firebase Consoleで同じBundle IDのiOS Appを登録する
- 新しい `GoogleService-Info.plist` を取得してローカル/Release環境へ配置する

OAuthのURL Schemeは別設定です。この手順では変更しません。
