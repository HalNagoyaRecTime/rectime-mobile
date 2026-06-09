# FCM 구현 이후 해야 할 일 정리

이 문서는 현재 Android FCM MVP 검증 이후 남아 있는 작업과, 이후 확장 시 추가하면 좋은 작업을 정리한 문서입니다.

## 현재 완료된 범위

- Firebase 프로젝트 생성
- Android 앱 등록
- `google-services.json` 추가
- Google Services Gradle plugin 설정
- Firebase Analytics / Messaging SDK 추가
- Android 앱에서 FCM Token 발급 확인
- Firebase Console에서 테스트 알림 발송
- Android Emulator에서 FCM 메시지 수신 확인
- Android 시스템 알림 등록 확인
- FCM 테스트 확인 절차 문서화

## 바로 해야 할 것

### 1. Git 상태 정리

현재 로컬에 남아 있는 변경 중 커밋에 포함할 것과 제외할 것을 분리해야 합니다.

커밋에 포함할 것:

- `composeApp/src/androidMain/kotlin/com/rectime/mobile/notification/`
- `docs/android-fcm-test.md`
- `docs/fcm-next-steps.md`

커밋에서 제외할 것:

- `.idea/misc.xml`

`.idea/misc.xml`은 로컬 IDE의 JDK 설정 변경이므로 저장소에 올리지 않는 것이 좋습니다.

예시:

```sh
git restore .idea/misc.xml
git add composeApp/src/androidMain/kotlin/com/rectime/mobile/notification/ docs/android-fcm-test.md docs/fcm-next-steps.md
git commit -m "FCM通知テスト手順を追加"
```

### 2. FCM 수신 서비스 파일 포함 여부 확인

`FirebaseMessagingService` 구현 파일이 Git에 포함되어야 합니다.

이 파일이 누락되면 다른 개발자가 pull 했을 때 FCM 메시지 수신 및 foreground 알림 표시 처리가 빠집니다.

확인 명령:

```sh
git status --short
git ls-files composeApp/src/androidMain/kotlin/com/rectime/mobile/notification
```

## Android 앱 쪽에서 하면 좋은 것

### 1. 개발용 로그 정리

현재 FCM Token은 Logcat에 출력해서 확인하고 있습니다.

MVP 단계에서는 괜찮지만, 이후에는 debug build에서만 출력되도록 제한하는 것이 좋습니다.

예시 방향:

```kotlin
if (BuildConfig.DEBUG) {
    Log.d(TAG, "FCM registration token: $token")
}
```

### 2. 알림 클릭 동작 정의

현재 알림 클릭 시 `MainActivity`를 여는 기본 동작만 있습니다.

이후에는 알림 종류에 따라 특정 화면으로 이동할 수 있도록 정해야 합니다.

예시:

- 호출 알림 클릭 -> 호출 상세 화면
- 공지 알림 클릭 -> 공지 상세 화면
- 예약 알림 클릭 -> 예약 화면

### 3. foreground / background 동작 정리

앱 상태에 따라 알림 처리 방식이 달라질 수 있습니다.

- foreground: 앱 코드의 `onMessageReceived`에서 직접 알림 생성
- background: FCM notification payload가 시스템 알림으로 표시될 수 있음

MVP에서는 foreground 수신 로그와 시스템 알림 등록 확인까지 완료했습니다.

이후 테스트에서는 다음 케이스를 분리해서 확인하는 것이 좋습니다.

- 앱 실행 중
- 앱을 홈으로 보낸 상태
- 앱 종료 상태
- 화면 잠금 상태

### 4. 알림 채널 정리

현재 채널:

```text
rectime_notifications
Rectime Notifications
```

이후 실제 서비스용 문구로 정리하는 것이 좋습니다.

예시:

```text
channel id: rectime_notifications
channel name: レクタイム通知
```

## Mock 데이터 기반 MVP 발송

DB가 아직 없으므로, 다음 단계에서는 FCM Token을 DB에 저장하지 않고 Mock 방식으로 검증합니다.

### 목표

Cloudflare Workers에서 Android Emulator로 FCM 알림을 발송할 수 있는지 확인합니다.

### 흐름

```text
Android Emulator
  ↓
FCM Token 발급
  ↓
Logcat에서 Token 확인
  ↓
Cloudflare Worker에 테스트 Token 저장
  ↓
Worker에서 FCM HTTP v1 API 호출
  ↓
Android Emulator 알림 수신
```

### 테스트 API 예시

```http
POST /notifications/test
```

요청 body:

```json
{
  "title": "test通知",
  "body": "君に届け"
}
```

Worker는 임시로 `TEST_FCM_TOKEN`을 사용해 FCM으로 메시지를 전송합니다.

## Cloudflare Workers / Hono에서 해야 할 것

### 1. Hono Worker 프로젝트 준비

Cloudflare Workers에서 Hono 기반 API를 준비합니다.

예상 구조:

```text
worker/
  src/
    index.ts
  wrangler.toml
  package.json
```

### 2. Firebase Service Account 준비

FCM HTTP v1 API를 호출하려면 Firebase Service Account가 필요합니다.

Firebase Console에서 확인할 위치:

```text
Project settings
  -> Service accounts
  -> Generate new private key
```

주의:

- Service Account JSON은 Git에 커밋하지 않습니다.
- private key는 Cloudflare Secret으로 저장합니다.

### 3. Cloudflare Secret 설정

Worker에 직접 비밀값을 하드코딩하지 않고 secret으로 등록합니다.

예시:

```sh
wrangler secret put FIREBASE_PROJECT_ID
wrangler secret put FIREBASE_CLIENT_EMAIL
wrangler secret put FIREBASE_PRIVATE_KEY
wrangler secret put TEST_FCM_TOKEN
```

필요 값:

- `FIREBASE_PROJECT_ID`
- `FIREBASE_CLIENT_EMAIL`
- `FIREBASE_PRIVATE_KEY`
- `TEST_FCM_TOKEN`

### 4. FCM HTTP v1 API 호출

FCM 발송 endpoint:

```text
https://fcm.googleapis.com/v1/projects/rectime-3c0ba/messages:send
```

전송 payload 예시:

```json
{
  "message": {
    "token": "FCM_TOKEN",
    "notification": {
      "title": "test通知",
      "body": "君に届け"
    },
    "data": {
      "type": "test"
    }
  }
}
```

## DB 준비 후 추가할 것

DB가 준비되면 FCM Token을 서버에 저장하는 구조로 확장합니다.

### 최소 테이블 예시

```text
device_tokens
- id
- user_id
- platform
- fcm_token
- device_id
- created_at
- updated_at
- last_seen_at
```

MVP 초기에는 `user_id` 없이 시작할 수도 있습니다.

```text
device_tokens
- id
- fcm_token
- platform
- created_at
```

### 앱에서 추가할 API

```http
POST /device-tokens
```

요청 body:

```json
{
  "token": "FCM_TOKEN",
  "platform": "android"
}
```

### 서버에서 해야 할 처리

- 새 토큰 저장
- 기존 토큰이면 갱신
- 마지막 사용 시간 업데이트
- 오래된 토큰 정리
- FCM 발송 실패 시 invalid token 삭제

## 추천 진행 순서

1. Git 상태 정리
2. FCM 수신 서비스 파일과 문서 커밋
3. Cloudflare Workers + Hono 프로젝트 생성
4. Firebase Service Account 발급
5. Cloudflare Secret 등록
6. Mock Token 기반 `/notifications/test` 구현
7. Worker에서 Emulator로 알림 발송 확인
8. DB 준비 후 FCM Token 저장 API 추가
9. 저장된 Token 기반 실제 알림 발송으로 확장

## 현재 판단

지금 단계에서는 DB 없이 진행하는 것이 적절합니다.

우선 목표는 `Android Emulator에서 Firebase 알림을 정상 수신할 수 있는지`와 `서버 역할을 하는 Cloudflare Workers에서 FCM으로 발송할 수 있는지`를 검증하는 것입니다.

DB 연동은 알림 수신과 서버 발송이 검증된 뒤 붙이는 것이 좋습니다.
