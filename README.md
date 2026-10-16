# rectime-mobile

Android・iOS・Desktop（JVM）を対象とした Kotlin Multiplatform プロジェクト。

## 技術スタック

| レイヤー | ライブラリ / バージョン |
|---|---|
| 言語 | Kotlin 2.3.20 |
| UI | Compose Multiplatform 1.10.3 |
| マテリアル | Material3 1.10.0-alpha05 |
| ライフサイクル | AndroidX Lifecycle 2.10.0 |
| ホットリロード | Compose Hot Reload 1.0.0 |
| Android Gradle Plugin | 8.13.2 |

## 必要環境

| ツール | 要件 |
|---|---|
| JDK | 21（Temurin 最新安定版、確認済み `jdk-21.0.10+7`） |
| Android SDK | compileSdk 36 / minSdk 26 |
| Xcode | iOS ビルド時のみ（macOS 必須） |
| IDE | IntelliJ IDEA / Android Studio（KMP プラグイン推奨） |

## セットアップ

```shell
git clone https://github.com/yukiidayo/rectime-mobile.git
cd rectime-mobile
```

依存関係は初回ビルド時に Gradle が自動取得します。

### Firebase Android設定

Android版をFirebaseへ接続して実行する場合は、Firebase Consoleから取得した設定ファイルを次の場所へ配置してください。

```text
composeApp/google-services.json
```

`composeApp/google-services.json`はGitの管理対象外です。コミットしないでください。
`composeApp/google-services.ci.json` はGit管理するCI専用のdummy設定で、`google-services.json` のJSON形式と `applicationId` の整合性をビルドで確認するために使います。本番Firebase設定のテンプレートではなく、ローカル実行や配布用ビルドには使用しません。
配布用ビルドでは対象Firebase projectから取得した実ファイルを使用してください。

### iOSローカル設定

iOSの共通設定はGit管理された `iosApp/Configuration/Config.xcconfig` を使用します。
Bundle ID、バージョン、Staging / Release API URLはRepositoryを正とし、ローカルファイルへ複製しません。

実機デバッグなどで開発者固有の値が必要な場合だけ、
Git管理対象外の `iosApp/Configuration/Local.xcconfig` を作成してください。

```xcconfig
TEAM_ID = <Apple Developer Team ID>
DEBUG_API_BASE_URL = http:/$()/<MacのIP>:8787
```

`Local.xcconfig` は任意です。未作成の場合、Debug APIは `rectime-api-development` を使用します。
Staging / Release API URLは `Config.xcconfig` の値から変更しません。

詳細なセットアップ手順は [`setup/README.md`](setup/README.md) を参照。

## ディレクトリ構成

```
rectime-mobile/
├── composeApp/
│   └── src/
│       ├── commonMain/       # 全プラットフォーム共通コード
│       ├── androidMain/      # Android 固有コード
│       ├── iosMain/          # iOS 固有コード
│       └── jvmMain/          # Desktop 固有コード
└── iosApp/                   # iOS エントリポイント（SwiftUI）
```

## ビルドの種類

| 種類 | 接続先 API | Android | iOS (Build Configuration) |
| --- | --- | --- | --- |
| デバッグ | `rectime-api-development` | `debug` | `Debug` |
| ステージング | `rectime-api-staging` | `staging` | `Staging` |
| リリース | `rectime-api` | `release` | `Release` |

- デバッグだけは接続先を差し替えられる。ローカルの `wrangler dev` に繋ぐときは、Android は
  `local.properties` の `API_BASE_URL`、iOS は `Local.xcconfig` の `DEBUG_API_BASE_URL` を指定する。
- ステージングはリリースと同じ非デバッグのbuildで、Android はアップロード鍵ではなく debug 鍵で署名する。

## 起動方法

### Android

```shell
# macOS / Linux
./gradlew :composeApp:assembleDebug

# Windows
.\gradlew.bat :composeApp:assembleDebug
```

IDE のツールバーから Run Configuration `composeApp [mobile]` を使うことも可能。

ステージングは `./gradlew :composeApp:assembleStaging`（Android Studio では Build Variants で `staging` を選ぶ）。

#### Android の release build

```shell
# Windows
.\gradlew.bat :composeApp:bundleRelease -PVERSION_CODE=2 -PVERSION_NAME=1.0.1
```

AAB は `composeApp/build/outputs/bundle/release/` に出力される。

- `API_BASE_URL` は release build では `local.properties` を参照せず、承認済みの本番 origin
  （`https://rectime-api.rectime-project.workers.dev`）が埋め込まれる。開発用の接続先が
  紛れ込むと、アプリは起動するのに API 通信だけ OS に遮断されるため、非本番の値を
  指定した場合は build が失敗する。
- `VERSION_CODE` / `VERSION_NAME` は未指定なら `1` / `1.0`。Play は同じ `versionCode` の
  再アップロードを受け付けないため、配布のたびに指定する。
- `BuildConfig.GIT_COMMIT` に build 時の commit が入る。

### Desktop（JVM）

API 接続先と Microsoft ログインの設定は [Desktop 認証の手順](setup/desktop-auth.md) を参照。

```shell
# macOS / Linux
./gradlew :composeApp:run

# Windows
.\gradlew.bat :composeApp:run
```

ホットリロードを使う場合（明示的リロード）:

```shell
# アプリ起動
.\gradlew.bat :composeApp:hotRunJvm

# ソース変更後にリロード
.\gradlew.bat :composeApp:hotReloadJvmMain

# 自動リロード
.\gradlew.bat :composeApp:hotRunJvm --auto
```

### iOS

`iosApp/` を Xcode で開いて実行、または IDE のツールバーから Run Configuration `composeApp [mobile]` を使う（macOS 必須）。

ステージングで動かすときは、Scheme の Run の Build Configuration を `Staging` にする。

## テスト

```shell
# ユニットテスト
.\gradlew.bat :composeApp:testDebugUnitTest

# Android インストルメンテッドテスト
.\gradlew.bat :composeApp:connectedDebugAndroidTest
```

---

[Kotlin Multiplatform について詳しく](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)

