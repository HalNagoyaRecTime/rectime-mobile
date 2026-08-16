# API接続先の切り替え

API Base URLは画面や通信処理へ直接記述せず、`AppConfig`から参照する。
実際のBackend URL、認証情報、SecretはRepositoryへ記載しない。

development／staging／productionの値は管理者から安全な経路で共有し、Git管理外のローカル設定またはCIの環境変数へ登録する。

## iOS

- Debug／Releaseともに`API_BASE_URL`から接続先を受け取る。
- ローカルではGit管理外の`iosApp/Configuration/Local.xcconfig`へ設定する。
- `Local.xcconfig`は`.gitignore`対象であり、GitHubへ追加しない。
- iPhone実機からローカルWorkerへ接続する場合、`localhost`ではなくMacのLAN IPを指定する。

`Local.xcconfig`の形式:

```text
API_BASE_URL = <shared-api-url>
```

コマンドから一時的に上書きする場合:

```shell
xcodebuild -project iosApp/iosApp.xcodeproj \
  -scheme iosApp \
  -configuration Debug \
  API_BASE_URL=<shared-api-url>
```

`Info-Debug.plist`と`Info-Release.plist`はBuild Settingを読み込む。未設定や未展開の場合は起動時に拒否する。ReleaseではHTTPS以外とローカル接続先も拒否する。

## Android

- Debugは`API_BASE_URL`、Releaseは`RELEASE_API_BASE_URL`から接続先を受け取る。
- Git管理外の`local.properties`、Gradle Property、CIの環境変数のいずれかで値を設定する。
- Debug用の`API_BASE_URL`はReleaseへ引き継がれない。

`local.properties`の形式:

```properties
API_BASE_URL=<shared-debug-api-url>
RELEASE_API_BASE_URL=<shared-release-api-url>
```

Releaseでは未設定、HTTPS以外、ローカル接続先を拒否する。
