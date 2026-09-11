# Firebase iOS設定

Firebase ConsoleでBundle ID `com.rectime.mobile.Rectimemobile` のiOSアプリを登録し、
設定ファイルを次の場所へ配置する。

```text
iosApp/iosApp/GoogleService-Info.plist
```

このファイルはGit管理しない。CIでは同じパスに復元する。

Firebase ConsoleのCloud Messaging設定には、Apple Push Notification Authentication Key
（`.p8`、Key ID、Team ID）を登録する。Apple Developer側では同じBundle IDのApp IDで
Push Notificationsを有効化し、`aps-environment`を含むProvisioning Profileを使用する。

Apple Developer側の準備ができるまでは、`iosApp.entitlements`をXcodeの署名設定へ適用しない。
準備後はDebugに`development`、Releaseに`production`の`aps-environment`を設定して、
`CODE_SIGN_ENTITLEMENTS`に`iosApp/iosApp.entitlements`を指定する。
