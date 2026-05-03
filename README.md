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

## 起動方法

### Android

```shell
# macOS / Linux
./gradlew :composeApp:assembleDebug

# Windows
.\gradlew.bat :composeApp:assembleDebug
```

IDE のツールバーから Run Configuration `composeApp [mobile]` を使うことも可能。

### Desktop（JVM）

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

## テスト

```shell
# ユニットテスト
.\gradlew.bat :composeApp:testDebugUnitTest

# Android インストルメンテッドテスト
.\gradlew.bat :composeApp:connectedDebugAndroidTest
```

---

[Kotlin Multiplatform について詳しく](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)
