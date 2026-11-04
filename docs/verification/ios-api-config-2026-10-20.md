# iOS API接続設定の確認記録

関連: #98。設定の確認記録であり、実機からの認証済み疎通は未完了。

## 対象と確認環境

- 対象: develop `30e7851f`。
- 実施日: 2026-10-20 JST。
- Xcode 27.0 / JDK 21.0.9。
- 既存の開発者設定を使わず、別checkoutで確認した。
- PR #103は#127と重複してClose済み。現行の設定を確認し、旧実装を再導入しない。

## Xcodeで評価した設定

`xcodebuild -showBuildSettings -json`でInfo.plistへ渡す値を確認した。

| Configuration | 接続環境 | APP_ENVIRONMENT | Info.plist |
| --- | --- | --- | --- |
| Debug | development | Development | Info-Debug.plist |
| Staging | staging | Staging | Info-Release.plist |
| Release | production | Production | Info-Release.plist |

- `Local.xcconfig`なしで3環境が意図した接続先へ解決された。
- 検証用の`Local.xcconfig`に`DEBUG_API_BASE_URL = http:/$()/127.0.0.1:8787`だけを設定し、再度3環境を評価した。
- Debugだけが検証用URLへ変更された。Staging / Releaseの接続先は変更されなかった。
- Stagingの`KOTLIN_FRAMEWORK_BUILD_TYPE`は`release`だった。
- 検証用ファイルは確認後に削除した。署名情報や実Firebase設定は使用していない。

再実行時は各Configurationで以下を実行し、`API_BASE_URL`、`APP_ENVIRONMENT`、`INFOPLIST_FILE`を確認する。

```sh
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp \
  -configuration Debug -destination 'generic/platform=iOS Simulator' \
  -disableAutomaticPackageResolution -showBuildSettings -json
```

ローカル設定がある場合、上書き確認には別checkoutを使用する。既存の`Local.xcconfig`を置き換えない。

## コードと自動テスト

- iOSはInfo.plistの`API_BASE_URL`を参照する。環境変数へのfallbackはDebugだけ。
- `resolveApiBaseUrl`は未設定・未展開・不正scheme・host欠落を拒否する。
- 非DebugではHTTPおよび定義済みloopback / emulator hostを拒否する。
- これは任意の非本番HTTPS接続先を全て拒否する検証ではない。接続環境の分離は上記Xcode設定で確認した。
- 共通コード・Android実装は変更していない。
- `./gradlew :composeApp:testDebugUnitTest`: 824 tests中30件失敗。未変更のdevelop上で、EventCache / LocalCache / NotificationReadStore等のキャッシュテストが主に`Dispatchers.Main`未設定で失敗した。設定確認の成功とAndroid全体の成功を混同しない。
- Androidの設定・認証・API関連10 suitesのみ再実行: 93 tests成功。全体失敗の解消を示すものではない。
- `./gradlew :composeApp:jvmTest`: 88 suites / 835 tests、失敗0。
- `./gradlew :composeApp:iosSimulatorArm64Test`: 76 suites / 786 tests、失敗0。
- Debug / ReleaseのInfo.plistは`plutil -lint`成功。

## Simulatorと実機の接続先

- Simulatorの`127.0.0.1`は開発Macを指す。
- 実機の`127.0.0.1`は実機自身を指す。MacのローカルAPIへ接続する場合は、DebugにMacのLANアドレスを指定する。
- Staging / Releaseには共通設定のHTTPS接続先を使用する。

## 未確認事項

- [ ] 各環境でMicrosoftログインし、実際のiOS Bearer TokenによるAPI成功を確認する。
- [ ] 実機でDebug / Staging / Releaseの意図した接続先を確認する。
- [ ] Android実機で接続先の回帰を確認する。
- [ ] 配布buildの署名、Firebase設定、Push受信を確認する。

設定評価とUnit Testの成功を、実機・TestFlight・App Store審査の成功として扱わない。
Backend側の認証・疎通結果はrectime-api #241 / #246と合わせて確認する。
