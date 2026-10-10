# Desktop の Microsoft ログイン（Windows 開発用）

モバイルと同じ `com.rectime.mobile://auth/callback` を使う。
API のソースや環境変数を Desktop 用に変更する必要はない。

## ログインの流れ

1. アプリから Microsoft のログイン画面を開く。
2. Microsoft 側の認証・確認を進める。
3. ブラウザがアプリ用 URL を開く際の確認で「開く」を選ぶ。
4. Windows が登録済みの受信用プログラムを起動する。
5. 認証結果が起動中のアプリへ渡り、既存の PKCE/state 検証と API のコード交換へ進む。
6. アプリを前面に戻すよう要求する。

Microsoft の確認画面と、ブラウザの「アプリを開く」確認は提供元の画面。
表示や文言はセッション・ブラウザ設定で変わる。アプリ独自の確認画面は追加しない。

- [Windows の URI 起動](https://learn.microsoft.com/en-us/windows/apps/develop/launch/launch-default-app)
- [Edge の外部プロトコル確認](https://learn.microsoft.com/en-us/deployedge/microsoft-edge-browser-policies/autolaunchprotocolsfromorigins)

## 初回登録

rectime-mobile のディレクトリで実行する。管理者権限は不要。

```powershell
.\setup\register-desktop-auth.ps1 -Register
```

Windows 標準の .NET Framework コンパイラで受信用プログラムをビルドし、
現在のユーザーの `HKCU\Software\Classes\com.rectime.mobile` に登録する。
別のアプリが登録済みの場合は上書きしない。
登録・解除後は `SHChangeNotify(SHCNE_ASSOCCHANGED)` で Windows に関連付けの変更を通知する。
実行ファイルは `composeApp/build/desktop-auth/` に置き、資格情報は保存しない。
`gradlew clean` 後は再登録する。作業ディレクトリを移動する場合は、移動前に登録を解除し、新しい場所で再登録する。
この登録は開発中のチェックアウト用。配布用インストーラーの登録処理は含まない。

## ローカル起動

rectime-api では通常どおり起動する。

```powershell
npm run dev
```

API の `MICROSOFT_MOBILE_REDIRECT_URI` はモバイルと同じ
`com.rectime.mobile://auth/callback` のまま使う。

rectime-mobile では API 接続先だけを指定する。

```powershell
$env:API_BASE_URL = 'http://127.0.0.1:8787'
.\gradlew.bat :composeApp:run
```

Desktop はブラウザを開く直前に `127.0.0.1:49152/auth/callback` で待ち受ける。
この HTTP 接続は Windows の受信用プログラムからアプリへの中継専用で、
Microsoft の戻り先には使わない。認証結果は保存せず、その場でアプリへ渡す。
待ち受けは応答後、再試行時、ブラウザ起動の失敗時、または10分後に閉じる。
アプリを終了してから認証を続けた場合は、アプリを起動してログインをやり直す。

## Firefoxで「続行」の後に停止する場合

WindowsでChrome経由の実ログインとアプリへの復帰を確認済み。
FirefoxではMicrosoftが正しい戻り先への302応答を返しても、
不明なプロトコルとして移動を中止する現象が複数のFirefox環境で再現した。
関連付け変更の通知を追加しても解消しておらず、Firefox側の具体的な原因は未特定。
開発時はChromeを利用できる。

Microsoftの応答が `302` で、`Location` が `com.rectime.mobile://auth/callback` なら、
Microsoftからの戻り先は正しい。FirefoxからWindowsのURLハンドラーへ渡す段階を確認する。
登録コマンドを再実行して関連付け情報を更新し、アプリから新しいログインを開始する。
改善しない場合はFirefoxを通常の操作で完全に終了して開き直し、再試行する。
ネットワークの `Location` に含まれる認証コードや、リクエストのトークンは共有しない。

## 登録解除

```powershell
.\setup\register-desktop-auth.ps1 -Unregister
```

このチェックアウトの受信用プログラムを指す登録だけを解除する。
