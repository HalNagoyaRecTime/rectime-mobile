# Android FCM 알림 테스트 확인 문서

이 문서는 Android Emulator에서 Firebase Cloud Messaging(FCM) 테스트 알림을 발송하고, 앱 수신 여부와 Android 시스템 알림 등록 여부를 확인한 절차를 정리한 문서입니다.

## 테스트 환경

- Firebase project: `rectime-3c0ba`
- Firebase Console account: `rectime.project@gmail.com`
- Android package: `com.rectime.mobile`
- Emulator: `Pixel_7`
- Notification channel: `rectime_notifications`
- Logcat tag: `RectimeFCM`

## 1. Emulator 실행 및 앱 설치

Emulator가 켜져 있는지 확인합니다.

```sh
adb devices
```

앱을 설치합니다.

```sh
./gradlew :composeApp:installDebug
```

앱을 실행합니다.

```sh
adb shell am start -n com.rectime.mobile/.MainActivity
```

Android 13 이상에서는 알림 권한이 필요하므로 테스트 전에 권한을 부여합니다.

```sh
adb shell pm grant com.rectime.mobile android.permission.POST_NOTIFICATIONS
```

## 2. FCM Token 확인

앱 실행 후 Logcat에서 `RectimeFCM` 태그를 확인합니다.

```sh
adb logcat -d -s RectimeFCM
```

확인된 로그 예시:

```text
D RectimeFCM: FCM registration token: <FCM_TOKEN>
```

Firebase Console에서 테스트 메시지를 보낼 때 이 토큰을 사용합니다.

## 3. Firebase Console에서 테스트 알림 보내기

Firebase Console URL:

```text
https://console.firebase.google.com/u/1/project/rectime-3c0ba/notification/compose
```

진행 순서:

1. `Cloud Messaging > 알림 작성` 화면으로 이동
2. 알림 제목 입력
3. 알림 텍스트 입력
4. `테스트 메시지 전송` 클릭
5. `FCM 등록 토큰 추가`에 Logcat에서 확인한 FCM Token 입력
6. `+` 버튼으로 테스트 기기 추가
7. 추가된 토큰이 체크된 상태에서 `테스트` 클릭

이번 테스트에서 사용한 메시지:

```text
title: test通知
body: 君に届け
```

## 4. FCM 수신 로그 확인

테스트 메시지를 보낸 뒤 다시 Logcat을 확인합니다.

```sh
adb logcat -d -s RectimeFCM
```

이번 테스트에서 확인된 수신 로그:

```text
D RectimeFCM: FCM message received from: 946149362229
```

이 로그가 있으면 Firebase에서 앱으로 FCM 메시지가 도착한 것입니다.

## 5. Android 시스템 알림 등록 확인

Emulator 화면에서 알림이 눈에 띄지 않을 경우, Android 시스템 알림 목록에 등록됐는지 확인합니다.

```sh
adb shell dumpsys notification --noredact | rg -n "Rectime|test通知|君に届け|rectime_notifications|com.rectime.mobile"
```

이번 테스트에서 확인된 주요 값:

```text
pkg=com.rectime.mobile
Notification(channel=rectime_notifications ...)
android.title=String (test通知)
android.text=String (君に届け)
```

이 값들이 있으면 앱이 FCM 메시지를 받은 뒤 Android notification을 생성한 것입니다.

## 6. Emulator 화면에서 직접 확인하는 팁

앱이 foreground 상태이면 알림 배너를 놓칠 수 있습니다. 화면에서 직접 확인하려면 앱을 홈으로 보낸 뒤 테스트 메시지를 보내는 것이 좋습니다.

```sh
adb shell input keyevent KEYCODE_HOME
```

그 다음 Firebase Console에서 테스트 메시지를 보내고, Emulator의 상단 알림바를 내려서 알림을 확인합니다.

## 확인 결과

- FCM Token 발급 확인 완료
- Firebase Console 테스트 메시지 발송 완료
- Logcat에서 FCM 수신 확인 완료
- Android 시스템 알림 목록에서 알림 제목/본문 등록 확인 완료
- 확인된 알림 제목: `test通知`
- 확인된 알림 본문: `君に届け`
