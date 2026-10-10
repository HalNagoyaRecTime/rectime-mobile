# DesktopのMicrosoftログイン

DesktopはシステムブラウザとAuthorization Code + PKCEを使い、
認証後はアプリが受信するHTTPループバックURLへ戻る。
Android/iOSは従来どおりカスタムスキームを使う。
WindowsのURL登録・受信用EXEは不要。

## Entraの設定

アプリ登録 → 認証 →「モバイルとデスクトップ アプリケーション」に
`http://localhost/auth/callback` を登録する。
Web用の `/api/v1/auth/microsoft/callback` とはパスを分ける。
localhostのポートはMicrosoftの一致判定で無視されるため、登録時に固定しない。
Webとネイティブの登録で、同じlocalhostパスを重複させない。
APIの `MICROSOFT_MOBILE_REDIRECT_URI` はAndroid/iOS用の値を保つ。

- [Microsoftのlocalhostの規則](https://learn.microsoft.com/en-us/entra/identity-platform/reply-url#localhost-exceptions)
- [RFC 8252: ネイティブアプリのループバック認証](https://www.rfc-editor.org/rfc/rfc8252.html#section-7.3)

## 起動

API側で通常どおり起動する。

```powershell
npm run dev
```

モバイルリポジトリでDesktopを起動する。

```powershell
$env:API_BASE_URL = 'http://127.0.0.1:8787'
.\gradlew.bat :composeApp:run
```

## 認証の流れと画面

1. 共通のログインボタンからMicrosoftの画面をシステムブラウザで開く。
2. DesktopがIPv4ループバックの空きポートで待ち受ける。
3. APIへ `X-Desktop-Redirect-Uri: http://localhost:<空きポート>/auth/callback` を送る。
4. APIはlocalhost・パス・ポートを検証し、stateと一緒に戻り先を保存する。
5. Microsoftの認証後、ブラウザがそのURLに戻る。
6. DesktopはstateとHostを検証し、共通の認証処理へ結果を渡す。
7. APIは開始時に保存した同じ戻り先とPKCEでコードを交換する。

ログイン前後のアプリ画面はモバイルと共通。
Microsoftの画面はセッションやアカウントの設定によって変わる。
「アプリを開きますか？」は出ず、ブラウザには結果をアプリに渡した旨の文言を表示する。
アプリを前面に戻すよう要求するが、OSによっては手動で戻る必要がある。

受信は10分後、認証応答後、再試行時、API取得失敗時、ブラウザ起動失敗時に終了する。
アプリを閉じた場合や認証待ちが期限切れの場合は、新しくログインを開始する。
ブラウザのURLに認証コードが含まれるため、共有・保存しない。

## 以前のWindows登録

以前の `register-desktop-auth.ps1` で登録したカスタムスキームは今回の方式では使わない。
登録を解除する場合は以前のブランチの同スクリプトを `-Unregister` で実行する。
別のアプリが所有する登録を手動で削除しない。
