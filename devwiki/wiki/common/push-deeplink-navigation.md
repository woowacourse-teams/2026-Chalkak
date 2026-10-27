# 푸시, 딥링크, 내비게이션

원본: [푸시 메시지 연동 계약](../../../docs/business-rules/guides/notification-push-contract.md), [푸시 기기 등록 안내](../../../docs/business-rules/guides/push-device-registration.md), [알림함 안내](../../../docs/business-rules/guides/notification-inbox.md). 이 문서는 다섯 플랫폼이 같은 결과를 내기 위한 구현 기준만 정리한다. 근거: 각 플랫폼 raw의 푸시·딥링크 절, [raw/ios-android-compat-ux.md](../../raw/ios-android-compat-ux.md).

## 1. 푸시 탭 → 도착 화면 (다섯 플랫폼 동일)

`data.type`으로만 분기한다. 제목·본문 문자열로 판단하지 않는다. [찰캌규칙]

| `type` | 도착 화면 | 조회 | 읽음 처리 |
| --- | --- | --- | --- |
| `POST_APPROVED` | `sourceId` 게시물 화면 | 게시물 조회 | `PATCH /notifications/{notificationId}/read` |
| `POST_REJECTED` | 알림 상세(반려 사유, 원본) | `GET /notifications/{notificationId}` | 같음 |
| `AI_REVIEW_COMPLETED`, `AI_REVIEW_FAILED` | `sourceId` 게시물의 AI 리뷰 화면 | `GET /posts/{sourceId}/ai-review`부터 | 같음 |
| `NOTICE` | 알림 상세(공지 본문) | `GET /notifications/{notificationId}` | 같음 |
| `TOPIC` | **탭한 날의** 오늘 주제 화면 | 오늘 주제 조회 | 하지 않음(`notificationId` 없음) |
| `REMINDER` | 앱 메인 | 없음 | 하지 않음 |
| 알 수 없는 값 | 앱 메인 [제안] | — | — |

규칙

- 푸시를 받았다고 권한이 생기지 않는다. 항상 현재 로그인 계정으로 API를 호출하고, 404면 [알림함 안내](../../../docs/business-rules/guides/notification-inbox.md)의 오류 처리를 따른다. 푸시에 남은 정보로 화면을 대신 그리지 않는다.
- 로그인하지 않은 상태에서 탭하면 로그인 → 원래 목적지로 이어간다. 진입 게이트(버전, 약관, 사인 등록)를 건너뛰지 않는다. [제안]
- 목적지 화면 밑에 메인 탭을 깔아 뒤로 가기가 앱 밖으로 튕기지 않게 한다(콜드 스타트 포함). [제안]
- 포그라운드 수신: 지금 보고 있는 게시물의 AI 리뷰 푸시는 표시를 생략하기를 권장한다. 그 외 포그라운드 표시 방식(시스템 배너 vs 인앱 배너)은 플랫폼 관례를 따르되 탭 결과는 위 표와 같다.

## 2. 앱 상태별 수신 위치

| 상태 | iOS | Android | Flutter (`firebase_messaging`) | RN (`expo-notifications`) | Web |
| --- | --- | --- | --- | --- | --- |
| 포그라운드 | `userNotificationCenter(_:willPresent:)` | `FirebaseMessagingService.onMessageReceived` | `FirebaseMessaging.onMessage` | `addNotificationReceivedListener` | Service Worker `push` / 페이지 `onMessage` |
| 백그라운드에서 탭 | `didReceive response` | 시작 Activity의 Intent extras(`onNewIntent` 포함) | `onMessageOpenedApp` | `addNotificationResponseReceivedListener` | SW `notificationclick` → `clients.openWindow`/`focus` |
| 종료 상태에서 탭 | 같은 delegate, 실행 직후 | 시작 Intent | `getInitialMessage()` | `getLastNotificationResponseAsync()` | 같음 |

- **Android**: `notification` + `data` 메시지는 백그라운드에서 SDK가 직접 표시하므로 `onMessageReceived`가 불리지 않는다. 탭 처리를 `onMessageReceived`에만 두면 이동이 빠진다. 서버는 `click_action`을 지정하지 않는다. [공식](https://firebase.google.com/docs/cloud-messaging/android/receive-messages)
- **iOS**: 표시 정보는 `aps`, 식별자는 `userInfo` 최상위에 온다. FCM JSON의 중첩 구조로 파싱하지 않는다. delegate는 `didFinishLaunching`이 끝나기 전에 지정해야 콜드 스타트 탭을 놓치지 않는다. [공식·미열람]
- **Flutter**: `getInitialMessage`를 처리하지 않으면 종료 상태 탭이 메인으로만 간다. [공식](https://firebase.google.com/docs/cloud-messaging/flutter/receive)
- **RN**: Expo 푸시 토큰이 아니라 **FCM 기기 토큰**(`getDevicePushTokenAsync`)을 서버에 등록해야 한다. 서버는 FCM으로 보낸다. [찰캌규칙] [공식](https://docs.expo.dev/push-notifications/sending-notifications-custom/)
- **Web**: iOS·iPadOS Safari는 **홈 화면에 설치한 PWA에서만** Web Push를 받는다(16.4+). 웹 푸시는 서버가 FCM 웹 토큰을 받을 수 있는지부터 확인해야 한다. [open-questions](../open-questions.md#백엔드-확인-대상) [공식](https://webkit.org/blog/13878/web-push-for-web-apps-on-ios-and-ipados/)

## 3. 권한과 토큰 등록

- 알림 권한은 앱 첫 실행 즉시가 아니라 **가치를 설명한 뒤**(예: 첫 게시물 업로드 직후 "승인되면 알려 드릴게요") 요청한다. Android 13+ `POST_NOTIFICATIONS`, iOS `requestAuthorization`, 웹 `Notification.requestPermission()`(사용자 제스처 안에서). [제안]
- Android 13 미만은 권한 없이 표시된다. Android 8+는 알림 채널이 필요하다. 채널은 수신 설정 종류(승인·반려, 주제, AI 리뷰)에 맞춰 만든다. [공식](https://developer.android.com/develop/ui/views/notifications/notification-permission)
- 토큰 등록: 로그인 후 토큰이 준비되면 `PUT /push-devices/current { fcmToken }`. 토큰이 바뀌거나 인증된 상태로 앱을 다시 시작할 때 같은 API로 갱신한다. 등록 실패만으로 로그아웃하지 않는다.
- JWT에 `session_id`가 없으면 먼저 토큰 갱신(single-flight) 후 등록한다. [networking §4](networking-errors-retry.md#4-토큰-갱신)
- 로그아웃·탈퇴 때 기기 해제 API를 따로 부르지 않는다. 서버가 함께 처리한다.
- 수신 설정은 OS 권한과 별개인 서버 값이다(`GET/PATCH /notification-settings`). OS 권한이 꺼져 있으면 설정 화면에 "기기 알림이 꺼져 있음 + 설정 열기"를 함께 보여 준다. [제안]

## 4. 딥링크

현재 iOS·Android 앱에는 Universal Links·App Links가 없고 URL 스킴은 소셜 로그인 콜백용뿐이다. 따라서 지금의 "딥링크"는 **푸시 탭의 앱 내부 라우트**다. [찰캌규칙]

- 다섯 플랫폼이 같은 내부 라우트 표를 쓴다. 예: `/post/{postId}`, `/post/{postId}/ai-review`, `/notifications/{notificationId}`, `/today`, `/`. [제안]
- 웹 앱은 이 경로가 실제 URL이 된다. 새로고침·직접 진입에서도 같은 화면이 떠야 한다(SPA fallback 설정).
- 공유 링크 같은 외부 딥링크를 도입하면 iOS `apple-app-site-association`, Android `assetlinks.json`(`autoVerify`)을 웹 도메인에 두고, 앱 미설치 시 웹으로 열리게 한다. 도입 여부는 팀 결정이다. [공식](https://developer.android.com/training/app-links/verify-android-applinks)
- 외부에서 들어온 ID는 신뢰하지 않는다. 형식 검사 후 API 조회로 확인한다.

## 5. 뒤로 가기와 내비게이션

| 항목 | iOS | Android | Flutter | RN | Web |
| --- | --- | --- | --- | --- | --- |
| 뒤로 | 엣지 스와이프, 내비게이션 바 버튼 | 시스템 Back. targetSdk 36부터 predictive back 기본 [공식](https://developer.android.com/guide/navigation/custom-back/predictive-back-gesture) | `PopScope`(`WillPopScope`는 폐기) | React Navigation이 처리 | 브라우저 뒤로 = history |
| 스택 도구 | `NavigationStack` + 경로 값 | Navigation Compose(기존 앱 Route) | `go_router` | Expo Router | React Router 등 |

- 작성 중 이탈(업로드 편집 중 뒤로)은 다섯 플랫폼 모두 같은 확인 다이얼로그를 띄운다. Android는 `BackHandler`/`OnBackPressedCallback`, Flutter는 `PopScope(canPop: false)`, 웹은 `beforeunload`(새로고침·탭 닫기)와 라우터 차단을 함께 쓴다.
- 결과는 같아야 한다. 같은 상태에서 뒤로를 누르면 같은 화면에 도착한다.
- 탭 재선택 시 맨 위로 스크롤·루트 복귀 같은 관례 차이는 허용한다.

## 체크리스트

- [ ] `type` 7종 + 알 수 없는 값에 대한 라우팅 테스트(포그라운드, 백그라운드, 종료 상태 각각)
- [ ] 읽음 처리는 `notificationId`가 있는 4종만
- [ ] 로그아웃 상태 탭 → 로그인 → 목적지
- [ ] 콜드 스타트 탭 후 뒤로가 메인으로 간다
- [ ] 알림 권한은 맥락 있는 시점에 요청하고, 거부 시 설정 안내가 있다
- [ ] FCM 기기 토큰을 등록하고, 바뀌면 다시 등록한다
