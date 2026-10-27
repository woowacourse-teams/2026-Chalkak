# iOS ↔ Android 동작 차이와 모바일 UX 가이드 (찰캌 5개 플랫폼 공통)

> 작성일: 2026-10-27 / 대상: iOS native, Android native, Flutter, React Native, Web app
> 원칙: UI는 플랫폼 관례를 따르되, **기능 동작(알림 수신 규칙, 딥링크 라우팅, 업로드 결과, 시간 계산)은 동일**해야 한다.
> 검증 표기: **[확인]** = 이번 조사에서 공식 페이지를 직접 열어 확인. **[공식]** = 공식 문서 URL을 근거로 하나 이번에 페이지 본문까지는 재확인하지 않음(구현 전 재확인 권장). 수치(OS 점유율 등)는 시점이 바뀌므로 항상 대시보드 원문을 다시 볼 것.

---

## Part A. 호환성 차이

### A1. Push Notifications

| 항목 | iOS | Android | 동일 동작 보장 방법 | Flutter / RN / Web 노출 |
|---|---|---|---|---|
| 전달 채널 | APNs (FCM을 쓰더라도 최종은 APNs) [공식](https://firebase.google.com/docs/cloud-messaging/ios/client) | FCM 직접 [공식](https://firebase.google.com/docs/cloud-messaging/android/client) | 서버는 FCM HTTP v1 한 곳으로 발송, iOS는 FCM이 APNs로 중계 | Flutter: `firebase_messaging` [공식](https://firebase.flutter.dev/docs/messaging/overview) / RN: `@react-native-firebase/messaging` / Web: FCM Web SDK + Service Worker |
| notification vs data | notification 메시지는 시스템이 표시. data-only는 **앱이 foreground이며 FCM 연결이 있을 때만** 전달 [확인](https://firebase.google.com/docs/cloud-messaging/concept-options) | data 메시지는 앱 상태와 무관하게 `onMessageReceived()` 호출 [확인](https://firebase.google.com/docs/cloud-messaging/concept-options) | **사용자에게 보여야 하는 알림은 notification(+data) 조합**으로 보낸다. data-only는 "조용한 동기화"에만 쓰고 iOS에서 보장되지 않음을 전제 | Flutter/RN 모두 `onMessage`(foreground), `onBackgroundMessage`, `onMessageOpenedApp`, `getInitialNotification` 계열 |
| foreground 표시 | 기본적으로 배너 안 뜸. `UNUserNotificationCenterDelegate.willPresent`에서 옵션 지정 [공식](https://developer.apple.com/documentation/usernotifications/unusernotificationcenterdelegate) | foreground면 notification 메시지도 앱 콜백으로 전달, 시스템이 자동 표시하지 않음 [확인](https://firebase.google.com/docs/cloud-messaging/concept-options) | 두 OS 모두 foreground에서는 **인앱 배너(스낵바/토스트형)를 직접 표시**하는 정책으로 통일 | Flutter: `setForegroundNotificationPresentationOptions`. RN/Web: `onMessage`에서 직접 UI |
| 앱 종료(killed) | 사용자가 앱 스와이프 종료 시 background fetch/silent push 전달이 제한됨. 표시용 notification은 정상 전달 [공식](https://developer.apple.com/documentation/usernotifications/pushing-background-updates-to-your-app) | 제조사별 "강제 종료/배터리 최적화"로 FCM 수신 지연·차단 가능. `Force stop` 상태는 수신 불가 [공식](https://developer.android.com/training/monitoring-device-state/doze-standby) | 중요 알림(예약 리마인더)은 **표시용 notification**으로, 앱 로직에 의존하지 않게 설계 | 플러그인은 OS 동작을 바꾸지 못함 |
| 우선순위 | APNs priority 5/10 (FCM normal/high 매핑) [확인](https://firebase.google.com/docs/cloud-messaging/concept-options) | data 기본 normal(Doze에서 지연), notification 기본 high [확인](https://firebase.google.com/docs/cloud-messaging/concept-options) | 리마인더는 `high`(Android) + `apns-priority: 10` 명시 | 서버 payload 설정 |
| 권한 | 최초 `requestAuthorization` 프롬프트 1회. 거부 후엔 설정 앱만 가능 [공식](https://developer.apple.com/documentation/usernotifications/asking-permission-to-use-notifications) | Android 13(API 33)+ `POST_NOTIFICATIONS` 런타임 권한. 신규 설치는 기본 off. targetSdk 33+면 프롬프트 시점을 앱이 제어 [확인](https://developer.android.com/develop/ui/views/notifications/notification-permission) | **프리-퍼미션 화면(가치 설명) → 시스템 프롬프트** 순서로 통일. 첫 실행 즉시 요청 금지 | Flutter: `permission_handler`/`firebase_messaging.requestPermission`. RN: `PermissionsAndroid.request('android.permission.POST_NOTIFICATIONS')` [공식](https://reactnative.dev/docs/permissionsandroid) + iOS는 messaging `requestPermission`. Web: `Notification.requestPermission()` (사용자 제스처 필요) |
| Provisional auth | `.provisional`: 프롬프트 없이 Notification Center에 조용히 전달 [공식](https://developer.apple.com/documentation/usernotifications/asking-permission-to-use-notifications) | 해당 개념 없음 | 찰캌은 "리마인더 가치가 명확"하므로 provisional 대신 명시적 프롬프트 권장. 쓴다면 iOS만 다른 동작임을 문서화 | Flutter `provisional: true`, RN 동일 옵션 |
| 채널 | 없음(앱 설정에서 카테고리 정도) | Android 8+ **Notification Channel 필수**, 중요도는 채널 생성 후 사용자만 변경 가능 [공식](https://developer.android.com/develop/ui/views/notifications/channels) | 채널 ID를 서버 payload 종류와 1:1(`reminder`, `like`, `sign`, `system`)로 고정. 앱 설정 화면의 토글도 같은 4종 | Flutter `flutter_local_notifications` 채널 생성, RN `notifee`/FCM `android.channelId`. Web 없음 |
| 배지 | 앱 아이콘 숫자 `badge` (서버가 값 지정 or 앱이 갱신) | 런처 의존. 점(dot)이 기본, 숫자는 제조사별 | **배지 숫자의 진실은 서버의 unread count**. 앱 진입/읽음 시 서버값으로 재설정. Android는 숫자에 의존한 기능 금지 | Flutter `app_badge_plus` 등, RN `setBadgeCount`(notifee), Web Badging API(PWA 한정) |
| 탭 처리 | `didReceive response` / cold start는 launchOptions | `PendingIntent` → Activity intent extras | payload에 `route`(예: `post/123`)와 `notification_id` 포함, **모든 상태(cold/warm/foreground tap)에서 같은 라우터 함수**로 진입 | Flutter `getInitialMessage`+`onMessageOpenedApp`, RN `getInitialNotification`+`onNotificationOpenedApp`, Web `notificationclick` |
| 토큰 갱신 | APNs 토큰 변경 가능, FCM 토큰은 `onTokenRefresh` | 동일. 앱 데이터 삭제/복원/재설치 시 변경 [공식](https://firebase.google.com/docs/cloud-messaging/manage-tokens) | 로그인 시·앱 시작 시·`onTokenRefresh` 시 서버에 upsert, 로그아웃 시 삭제. 토큰에 timestamp를 저장하고 오래된 토큰 정리 | Flutter `onTokenRefresh`, RN `onTokenRefresh`, Web `getToken` |

**자주 하는 실수**
- iOS에서 data-only 메시지로 사용자 알림을 대체 → 앱 종료 시 도착하지 않음.
- Android 13+에서 manifest 선언만 하고 런타임 요청 누락 → 알림이 영원히 안 옴.
- 채널 중요도를 나중에 코드로 올리려 함 → 이미 만든 채널은 앱이 변경 불가(재설치 외 방법 없음). 채널 ID 변경 필요.
- 로그아웃 시 토큰 미삭제 → 다른 계정에 알림 전달.
- foreground에서 수신한 알림을 인박스에 저장하지 않아 push 인박스와 불일치.

**체크리스트**
- [ ] 알림 종류별 payload 스키마(`type`, `route`, `notification_id`, `badge`)를 서버·5개 클라이언트가 공유
- [ ] cold/warm/foreground 3상태 탭 라우팅 테스트
- [ ] 권한 거부 상태에서 설정 화면 유도 UI
- [ ] 토큰 upsert/삭제 API 및 만료 정리
- [ ] 알림 인박스(서버 저장)와 push는 별개 — push 유실돼도 인박스에는 존재

---

### A2. Deep Links

| 항목 | iOS | Android | 동일 동작 보장 방법 | Flutter / RN / Web |
|---|---|---|---|---|
| 방식 | Universal Links: `apple-app-site-association`(AASA) JSON을 `https://도메인/.well-known/`에서 제공 + Associated Domains entitlement [공식](https://developer.apple.com/documentation/xcode/supporting-associated-domains) | App Links: `/.well-known/assetlinks.json` + intent-filter `android:autoVerify="true"` [공식](https://developer.android.com/training/app-links/verify-android-applinks) | 한 도메인(`chalkak.app` 등)에 AASA와 assetlinks.json을 같이 호스팅. 경로 체계(`/post/{id}`, `/topic/{date}`)를 단일 표로 관리 | Flutter: `go_router`/`app_links`. RN: `Linking` [공식](https://reactnative.dev/docs/linking) + React Navigation linking config. Web: 일반 URL이 곧 진실 |
| 검증 실패 시 | Safari로 열림(앱 미실행) | 디스앰비규에이션 다이얼로그 또는 브라우저 | 릴리즈 빌드(서명 인증서 SHA-256)로 검증. 디버그/릴리즈 지문 모두 assetlinks에 등록 | — |
| 앱 미설치 | 웹 페이지(스토어 유도 배너) | 웹 페이지 | **웹 랜딩에서 동일 콘텐츠 표시 + 스토어 링크** | Web이 fallback 역할 |
| Deferred deep link | 기본 미제공(클립보드·SDK 필요, 클립보드 접근은 붙여넣기 알림 노출) | Play Install Referrer API로 설치 시 referrer 전달 [공식](https://developer.android.com/google/play/installreferrer) | 필요 시 Branch/AppsFlyer 등 서드파티로 통일하거나, MVP에서는 "설치 후 첫 실행 시 로그인 → 홈" 단순화 | 플러그인/서드파티 SDK |
| 커스텀 스킴 | 가능하나 도메인 소유 증명 없음(가로채기 가능) | 동일 | 커스텀 스킴(`chalkak://`)은 OAuth 콜백 등 보조용으로만 | — |

**자주 하는 실수**: AASA에 `Content-Type`/리다이렉트 문제(리다이렉트 금지), 경로 패턴 누락; assetlinks.json의 fingerprint가 Play App Signing 키와 불일치; 딥링크 진입 시 **로그인 필요 화면**에서 목적지 정보를 잃음(→ 로그인 후 복귀용 pending route 저장).

**체크리스트**: [ ] 5개 플랫폼 공통 URL 스펙 표 [ ] 미로그인·삭제된 게시물·권한 없음 3가지 실패 화면 [ ] Universal/App Links 검증 CI 점검.

---

### A3. Permissions (카메라, 사진 라이브러리)

| 항목 | iOS | Android | 동일 동작 보장 방법 | Flutter / RN / Web |
|---|---|---|---|---|
| 카메라 | `NSCameraUsageDescription` 필수, 프롬프트 1회 | `CAMERA` 런타임 권한 | 권한 요청 전 목적 설명(인앱) | Flutter `image_picker`/`camera`, RN `react-native-vision-camera`/`image-picker`, Web `getUserMedia`(HTTPS 필수) |
| 사진 선택 | PHPicker는 **권한 없이** 선택 가능. 라이브러리 전체 접근 시 Limited(선택한 사진만) 존재 [공식](https://developer.apple.com/documentation/photokit/delivering-an-enhanced-privacy-experience-in-your-photos-app) | **Photo Picker**: 권한 없이 선택 가능 [공식](https://developer.android.com/training/data-storage/shared/photopicker) | 두 OS 모두 **시스템 Picker 사용 → 라이브러리 권한 요청 자체를 하지 않는다**. 찰캌 업로드에 전체 라이브러리 접근은 불필요 | Flutter `image_picker` (내부적으로 PHPicker/Photo Picker), RN `react-native-image-picker`, Web `<input type="file" accept="image/*">` |
| 거부 후 | 재요청 불가 → 설정 앱 | 2회 거부 이후(Android 11+ 규칙) 시스템이 다이얼로그를 생략할 수 있음 [공식](https://developer.android.com/training/permissions/requesting) | 상태 3종(미결정/거부/영구거부)을 추상화하고, 영구거부면 "설정으로 이동" 버튼 | Flutter `permission_handler`의 `isPermanentlyDenied`/`openAppSettings`, RN `Linking.openSettings()` |
| Rationale | 별도 API 없음(Info.plist 문구가 프롬프트에 표시) | `shouldShowRequestPermissionRationale()` [공식](https://developer.android.com/training/permissions/requesting) | 두 OS 모두 프롬프트 **전**에 자체 설명 화면 | — |

**자주 하는 실수**: 사진 라이브러리 전체 권한을 요청해 심사/사용자 신뢰 저하; Limited 상태를 "권한 없음"으로 오인; Info.plist 문구 누락으로 크래시.
**체크리스트**: [ ] Picker 기반 업로드 [ ] 카메라 거부/영구거부 UI [ ] 문구 한국어 현지화.

---

### A4. 백그라운드 실행 제한 / 프로세스 종료 / 상태 복원

| 항목 | iOS | Android | 동일 동작 보장 방법 | Flutter / RN / Web |
|---|---|---|---|---|
| 백그라운드 | 수 초 내 중단. 장시간은 `BGTaskScheduler` 등 시스템 재량 [공식](https://developer.apple.com/documentation/backgroundtasks) | Doze/App Standby, WorkManager 권장 [공식](https://developer.android.com/develop/background-work/background-tasks) | **정시 동작(일일 리마인더)은 서버 푸시로 구현**, 클라이언트 타이머/백그라운드 작업에 의존 금지 | Flutter `workmanager`, RN `react-native-background-fetch` — 둘 다 OS 재량 |
| 정확한 알람 | 로컬 알림 `UNCalendarNotificationTrigger` | `SCHEDULE_EXACT_ALARM` 제한 [공식](https://developer.android.com/develop/background-work/services/alarms) | 로컬 예약은 보조 수단. 정책의 진실은 서버 스케줄 | — |
| 업로드 지속 | `URLSession` background configuration | WorkManager + 네트워크 제약 | 업로드는 **재개 가능 + 서버 멱등(idempotency key)** 설계 | 플러그인 의존, Web은 탭 닫히면 종료 |
| 프로세스 종료 복원 | `NSUserActivity`/Scene state restoration [공식](https://developer.apple.com/documentation/uikit/view_controllers/preserving_your_app_s_ui_across_launches) | 프로세스 사망 시 `SavedStateHandle`/`onSaveInstanceState`로 복원 필수 [공식](https://developer.android.com/topic/libraries/architecture/saving-states) | 작성 중인 게시물 draft(사진 URI, 텍스트)는 로컬 저장 후 복원. 임시 URI 접근권한 만료 주의 | Flutter `RestorationMixin`, RN은 persisted store(AsyncStorage 등), Web `sessionStorage`/IndexedDB |

**자주 하는 실수**: Android 카메라 앱으로 이동한 사이 프로세스가 죽어 촬영 결과 유실; 메모리 상태에만 폼 보관; 백그라운드 타이머로 리마인더 구현.
**체크리스트**: [ ] "개발자옵션: 활동 유지 안 함"으로 복원 테스트 [ ] 업로드 재시도·중복 방지.

---

### A5. Navigation (뒤로가기, 시트)

| 항목 | iOS | Android | 동일 동작 보장 방법 | Flutter / RN / Web |
|---|---|---|---|---|
| 뒤로 | 좌측 가장자리 스와이프 + 좌상단 Back 버튼 | 시스템 Back(제스처/버튼), Android 13+ 예측적 뒤로 [공식](https://developer.android.com/guide/navigation/custom-back/predictive-back-gesture) | **화면 스택 구조는 동일**, 뒤로 결과(닫기/취소)만 동일하게. 편집 중 이탈은 "초안 저장/버리기" 확인 | Flutter `PopScope`, RN `BackHandler` [공식](https://reactnative.dev/docs/backhandler), Web History API |
| 모달/시트 | 카드형 sheet, 아래로 스와이프 닫기 [공식](https://developer.apple.com/design/human-interface-guidelines/sheets) | Modal bottom sheet, Back으로 닫힘 [공식](https://m3.material.io/components/bottom-sheets/overview) | 시트 열림 상태에서 Back = 시트 닫기 보장 | — |

**자주 하는 실수**: Android Back을 가로채 앱이 종료되지 않음; 예측적 뒤로 미지원 콜백 사용(`onBackPressed` 대신 `OnBackPressedCallback`); 시트 닫힘 후 상태 미초기화.
**체크리스트**: [ ] 모든 화면에서 Back 동작 표 [ ] 딥링크 진입 시 back stack 구성(홈 → 상세).

---

### A6. Keyboard, Safe Area, Edge-to-edge, Status bar

| 항목 | iOS | Android | 동일 동작 보장 방법 | Flutter / RN / Web |
|---|---|---|---|---|
| Safe area | notch/Dynamic Island/홈 인디케이터를 safe area로 제공 [공식](https://developer.apple.com/design/human-interface-guidelines/layout) | targetSdk 35+는 Android 15에서 **edge-to-edge 강제**, insets 직접 처리 [확인](https://developer.android.com/develop/ui/views/layout/edge-to-edge) | 콘텐츠는 inset 안쪽, 배경은 가장자리까지. 하단 버튼/탭바는 항상 inset 적용 | Flutter `SafeArea`, RN `react-native-safe-area-context`, Web `env(safe-area-inset-*)` + `viewport-fit=cover` |
| 키보드 | 키보드가 뷰를 가림(`keyboardLayoutGuide`) | `adjustResize` + IME insets [공식](https://developer.android.com/develop/ui/views/layout/sw-keyboard) | 입력 필드·제출 버튼이 키보드 위에 보이도록 | RN `KeyboardAvoidingView`(플랫폼별 behavior 상이), Flutter `Scaffold.resizeToAvoidBottomInset`, Web `visualViewport` |
| Status bar | 스타일 light/dark | 아이콘 명암 `isAppearanceLightStatusBars` [확인](https://developer.android.com/develop/ui/views/layout/edge-to-edge) | 사진 위 상단바는 scrim으로 가독성 확보 | Flutter `SystemUiOverlayStyle`, RN `StatusBar` |

**자주 하는 실수**: Android 15에서 상단/하단이 시스템바와 겹침; 키보드에 제출 버튼이 가려짐; RN `KeyboardAvoidingView`를 iOS 전용 값으로 두고 Android 확인 누락.
**체크리스트**: [ ] Android 15 기기/에뮬레이터 [ ] 3버튼/제스처 내비 양쪽 [ ] 가로 모드 cutout.

---

### A7. 이미지 포맷, EXIF, 파일 크기, 공유

| 항목 | iOS | Android | 동일 동작 보장 방법 | Flutter / RN / Web |
|---|---|---|---|---|
| 포맷 | 카메라 기본 HEIC/HEIF(설정에 따라 JPEG) | 대부분 JPEG, 일부 HEIC/AVIF/WebP | **서버가 받는 포맷을 JPEG(필요 시 WebP)로 정의**하고 클라이언트가 업로드 전 변환·리사이즈. Web은 브라우저가 HEIC 디코딩 못 할 수 있음 | `image_picker`의 압축 옵션, RN picker의 `quality`/`maxWidth`; Web은 canvas 재인코딩 |
| EXIF 방향 | orientation 태그로 회전 표현 | 동일하나 일부 기기/라이브러리가 무시 [공식](https://developer.android.com/reference/androidx/exifinterface/media/ExifInterface) | 변환 시 **픽셀을 회전시키고 orientation 태그 제거**. 위치정보(GPS) EXIF는 개인정보 이슈로 제거 | 서버에서도 한번 더 정규화(이중 안전망) |
| 크기 | 12MP+ 원본 수 MB~10MB+ | 기기별 편차 큼 | 장변 상한(예: 2048px)과 용량 상한을 정하고 공통 상수로 관리 | — |
| 공유 | Share Extension / `UIActivityViewController` | `ACTION_SEND` intent 수신 [공식](https://developer.android.com/training/sharing/receive) | "다른 앱에서 사진 공유 → 찰캌 업로드" 지원 여부를 기능 범위로 먼저 결정(미지원이면 양쪽 다 미지원) | Flutter `receive_sharing_intent`, Web Share Target(PWA) |

**자주 하는 실수**: HEIC 원본 업로드 → Web/Android에서 미표시; 회전 이중 적용; GPS EXIF 유출; 대용량 업로드 타임아웃.
**체크리스트**: [ ] iPhone HEIC·세로 사진 샘플 [ ] EXIF 제거 검증 [ ] 업로드 진행률/취소/재시도.

---

### A8. 날짜·시간·시간대·로케일

| 항목 | iOS | Android | 동일 동작 보장 방법 | Flutter / RN / Web |
|---|---|---|---|---|
| 시간대 | 기기 설정 따라감, `TimeZone` [공식](https://developer.apple.com/documentation/foundation/timezone) | `java.time.ZoneId` [공식](https://developer.android.com/reference/java/time/ZoneId) | **서버 저장은 UTC(ISO 8601), "오늘의 주제" 기준일은 KST(Asia/Seoul) 고정**. 해외 사용자도 같은 주제일을 보도록 정책 문서화 | Flutter `timezone`, RN `date-fns-tz`/Luxon, Web `Intl.DateTimeFormat({timeZone:'Asia/Seoul'})` [공식](https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Intl/DateTimeFormat) |
| 12/24시간 | 기기 설정에 따라 | 동일 | 표시는 로케일 포맷터 사용, 하드코딩 `HH:mm` 금지. 리마인더 시각 입력 UI는 두 형식 모두 지원 | 각 플랫폼 locale API |
| 리마인더 | 로컬 예약 가능 | 정확 알람 제한 | 서버가 KST 기준으로 발송. 사용자가 시각 변경 시 서버에 저장 | — |

**자주 하는 실수**: 클라이언트 로컬 날짜로 "오늘 주제" 계산(자정 경계 불일치); 서머타임 없는 KST를 가정해 다른 지역 사용자 처리 누락; 문자열 날짜 파싱 차이.
**체크리스트**: [ ] 자정 직전/직후 테스트 [ ] 기기 시간대를 바꿔도 주제일 동일 [ ] 서버가 주제 날짜를 응답에 포함.

---

### A9. 텍스트, 폰트 스케일, 접근성

| 항목 | iOS | Android | 동일 동작 보장 방법 | Flutter / RN / Web |
|---|---|---|---|---|
| 글자 크기 | Dynamic Type [공식](https://developer.apple.com/design/human-interface-guidelines/typography) | `sp`, Android 14부터 최대 200% 비선형 스케일링 [공식](https://developer.android.com/about/versions/14/features) | 큰 글자에서 레이아웃이 깨지지 않게 줄바꿈·스크롤 허용, 고정 높이 지양 | Flutter `MediaQuery.textScaler`, RN `allowFontScaling`/`maxFontSizeMultiplier` [공식](https://reactnative.dev/docs/text), Web `rem` |
| 스크린리더 | VoiceOver | TalkBack | 이미지에 대체 텍스트, 버튼 라벨, 읽기 순서, 상태("좋아요 눌림") 전달 | Flutter `Semantics` [공식](https://docs.flutter.dev/ui/accessibility-and-internationalization/accessibility), RN `accessibilityLabel` [공식](https://reactnative.dev/docs/accessibility), Web ARIA |
| 터치 타깃 | 44×44pt [공식](https://developer.apple.com/design/human-interface-guidelines/accessibility) | 48×48dp [공식](https://m3.material.io/foundations/accessible-design/overview) | 공통 최소 48 단위로 설계(양쪽 충족) | 아이콘 버튼에 hit slop |

**자주 하는 실수**: 폰트 스케일 200%에서 탭바·버튼 텍스트 잘림; 아이콘 버튼 라벨 없음; 색만으로 상태 구분.
**체크리스트**: [ ] 최대 폰트 스케일 스크린샷 [ ] VoiceOver/TalkBack 핵심 플로우(로그인→업로드→좋아요).

---

### A10. 최소 OS 정책, 점유율, 앱 업데이트, 리뷰 요청

- 점유율 출처: Android는 [Android Studio 대시보드](https://developer.android.com/about/dashboards), iOS는 [App Store 지원 페이지의 OS 점유율](https://developer.apple.com/support/app-store/) (Apple 공식). 한국 시장 데이터는 별도 통계(StatCounter 등)로 보완 — **수치는 구현 시점에 재조회**.
- Apple은 매년 Xcode/SDK 최소 요건을 공지 [공식](https://developer.apple.com/news/upcoming-requirements/), Google Play는 targetSdk 요건을 매년 상향(Play Console 정책).
- 제안: 점유율 약 95~97% 커버 수준으로 minOS 결정(예: iOS 최신-2 메이저, Android API 26~29대). 확정은 팀 합의 사항이며 위 대시보드로 근거 첨부.
- 파편화: Android는 제조사 배터리 정책(삼성/샤오미 등)과 화면 크기 다양성 → 실기기 테스트 매트릭스 필요.

| 항목 | iOS | Android | 동일 동작 보장 | Flutter/RN/Web |
|---|---|---|---|---|
| 업데이트 | 강제 업데이트 API 없음, 서버 버전 체크 후 스토어 이동 | Play In-App Updates(flexible/immediate) [공식](https://developer.android.com/guide/playcore/in-app-updates) | 서버가 `min_supported_version` 제공 → 두 OS 모두 같은 규칙으로 강제/권장 팝업 | Flutter `in_app_update`(Android), RN `sp-react-native-in-app-updates`; Web은 배포 즉시 반영 |
| 리뷰 | `requestReview` (빈도 시스템 제한) [공식](https://developer.apple.com/documentation/storekit/requesting-app-store-reviews) | Play In-App Review (quota 제한) [공식](https://developer.android.com/guide/playcore/in-app-review) | 긍정 순간(예: 게시 성공 N회 후)에만, 버튼 눌러도 안 뜰 수 있음을 전제 | `in_app_review` 등 |

**자주 하는 실수**: 리뷰 API를 "리뷰 쓰기" 버튼에 직결; API 최소 버전 올리며 구형 서버 응답 호환 상실.

---

### A11. 로그인 SDK (Apple, Google, Kakao)

| 항목 | iOS | Android | 동일 동작 보장 방법 | Flutter / RN / Web |
|---|---|---|---|---|
| Sign in with Apple | 네이티브 `AuthenticationServices`. 타사 소셜 로그인 제공 시 동등 옵션 요구(App Store Review Guidelines 4.8) [공식](https://developer.apple.com/app-store/review/guidelines/) | 네이티브 SDK 없음, 웹 OAuth(브라우저) 방식 [공식](https://developer.apple.com/sign-in-with-apple/) | 서버가 Apple identity token(JWT)을 검증. 이메일 숨김(relay) 가능 → **이메일을 유일 식별자로 쓰지 말고 `sub` 사용** | Flutter `sign_in_with_apple`, RN `@invertase/react-native-apple-authentication`, Web Apple JS |
| Google | Google Sign-In SDK | Credential Manager [공식](https://developer.android.com/identity/sign-in/credential-manager) | ID token을 서버 검증, 클라이언트 ID(플랫폼별) 관리 | Flutter `google_sign_in`, RN `@react-native-google-signin/google-signin` |
| Kakao | Kakao SDK, 카카오톡 앱 실행 후 복귀(URL scheme, `LSApplicationQueriesSchemes`) [공식](https://developers.kakao.com/docs/latest/ko/kakaologin/common) | Kakao SDK, 키 해시 등록 필요 | 서버가 Kakao 액세스 토큰으로 사용자 조회·검증, 계정 연동 규칙 통일 | Flutter `kakao_flutter_sdk`, RN `@react-native-seoul/kakao-login`, Web Kakao JS SDK |

**자주 하는 실수**: Android 키 해시를 디버그/릴리즈 둘 다 등록하지 않음; Apple 로그인 재심사에서 계정 삭제 기능 누락(앱 내 계정 삭제 요구 있음); Apple 이메일 재수신 불가(최초 1회만 제공)를 모름.
**체크리스트**: [ ] 3개 provider → 하나의 서버 계정 모델 [ ] 계정 삭제 [ ] 로그인 취소/오류 UX 공통.

---

## Part B. 사용자 친화 UI/UX

### B1. Apple HIG vs Material 3

| 항목 | iOS (HIG) | Android (M3) | 찰캌 권장 | 구현 메모 |
|---|---|---|---|---|
| 메인 내비 | Tab bar 하단, 최대 5개 [공식](https://developer.apple.com/design/human-interface-guidelines/tab-bars) | Navigation bar 3~5개 [공식](https://m3.material.io/components/navigation-bar/overview) | 하단 3~4탭(홈/촬영·업로드/알림/내 정보). 두 OS 구조 동일 | Flutter `NavigationBar`/`CupertinoTabBar`, RN React Navigation bottom-tabs |
| 상단 | Large title, 좌측 Back | Top app bar | 플랫폼 관례 허용 | — |
| 버튼 | 텍스트/채움 버튼, 파괴적 동작은 빨강 | Filled/tonal/outlined, FAB | 주요 CTA 1개만 강조 | — |
| 알림창 | Alert는 짧게, 파괴적 버튼 명시 [공식](https://developer.apple.com/design/human-interface-guidelines/alerts) | Dialog [공식](https://m3.material.io/components/dialogs/overview) | 확인 대화상자 최소화(B3 참고) | — |
| 일시 피드백 | **Toast/Snackbar 없음**(관례상 인라인 배너·햅틱·상태 변화) | Snackbar (실행취소 액션 가능) [공식](https://m3.material.io/components/snackbar/overview) | 공통 컴포넌트 `Feedback`: Android=Snackbar, iOS=상단/하단 커스텀 배너(비차단), 의미·지속시간·undo 액션은 동일 | 접근성: 자동 사라짐 시간 충분히(스크린리더 안내 `liveRegion`) |
| 아이콘/폰트 | SF Symbols, SF Pro | Material Symbols, Roboto | 플랫폼 기본 사용 가능(브랜드 폰트 쓰면 통일) | — |

**자주 하는 실수**: iOS에서 Material 스타일을 그대로 이식해 이질감; 반대로 iOS 식 탭을 Android 상단 탭으로 복제; 모든 플랫폼에 Snackbar를 쓰려 해 iOS에서 어색함.
**체크리스트**: [ ] "플랫폼별로 달라도 되는 것 / 같아야 하는 것" 표 [ ] 컴포넌트 매핑표.

### B2. 로딩·빈 상태·오류·오프라인

| 상태 | 가이드 | 근거 |
|---|---|---|
| 로딩 | 1초 미만은 표시 없음, 이후 **skeleton**(콘텐츠 형태 예고), 긴 작업은 진행률 | [NN/g Skeleton screens](https://www.nngroup.com/articles/skeleton-screens/) |
| 빈 상태 | 이유 설명 + 다음 행동(예: "오늘의 첫 사진을 올려보세요") | [NN/g Empty states](https://www.nngroup.com/articles/empty-state-interface-design/) |
| 오류 | 무엇이/왜/어떻게 해결 — 사람 말투, 비난 금지, 원인 코드 숨김, 입력값 보존 | [NN/g Error message guidelines](https://www.nngroup.com/articles/error-message-guidelines/) |
| 오프라인 | 캐시된 콘텐츠 표시 + 상단 오프라인 표식 + 쓰기 동작은 큐잉 or 명확한 실패 | [공식 Android 오프라인 가이드](https://developer.android.com/topic/architecture/data-layer/offline-first) |
| Pull-to-refresh | 두 OS 관례(iOS 상단 스피너/Android M3 인디케이터) 모두 존재. 피드 상단에서만 | [HIG Refresh](https://developer.apple.com/design/human-interface-guidelines/refresh-content-controls) |
| 무한 스크롤 | cursor 기반 페이징, 끝 도달 표시, 하단 로딩 행, 실패 시 "다시 시도" 행, 스크롤 위치 보존 | [NN/g Infinite scroll](https://www.nngroup.com/articles/infinite-scrolling-tips/) |
| 시스템 상태 가시화 | 사용자가 항상 현재 상태를 알게 함 | [NN/g 10 Heuristics](https://www.nngroup.com/articles/ten-usability-heuristics/) |

오류 문구 예(한국어): "사진을 올리지 못했어요. 네트워크 연결을 확인하고 다시 시도해 주세요. [다시 시도]" — 서버 코드(`500`)는 노출 금지, 로그에만 기록.
**자주 하는 실수**: 스피너만 오래 노출; 실패 시 입력/선택 사진 소실; 빈 피드와 로딩 실패를 구분 못 함; 오프라인에서 좋아요 눌러 조용히 실패.
**체크리스트**: [ ] 화면당 loading/empty/error/offline 4상태 스펙 [ ] 페이지네이션 중복 제거(id 기준) [ ] 재시도 지수 백오프.

### B3. 피드백, 햅틱, Undo, 모션

| 항목 | 가이드 | 근거 |
|---|---|---|
| 햅틱 | 의미 있는 순간에만(좋아요, 업로드 성공). iOS: UIFeedbackGenerator, Android: HapticFeedbackConstants. 끈 사용자 설정 존중 | [HIG Playing haptics](https://developer.apple.com/design/human-interface-guidelines/playing-haptics), [Android haptics](https://developer.android.com/develop/ui/views/haptics/haptic-feedback) |
| Undo > confirm | 잦은 동작(좋아요 취소, 게시물 숨김)은 확인창 대신 **Undo 액션**; 되돌릴 수 없는 파괴적 동작(계정 삭제)만 확인 | [NN/g Confirmation dialogs](https://www.nngroup.com/articles/confirmation-dialog/) |
| 낙관적 UI | 좋아요 즉시 반영 → 실패 시 롤백 + 오류 알림 | [NN/g Heuristics #1](https://www.nngroup.com/articles/ten-usability-heuristics/) |
| 모션 | 의미 전달용, 짧게. "동작 줄이기" 설정 존중 | [HIG Motion](https://developer.apple.com/design/human-interface-guidelines/motion), [M3 Motion](https://m3.material.io/styles/motion/overview) |
| 대비 | 본문 4.5:1 이상 | [WCAG 대비](https://www.w3.org/WAI/WCAG22/Understanding/contrast-minimum.html) |
| 터치 타깃 | 최소 24px(WCAG 2.2 AA), 권장 44pt/48dp | [WCAG Target Size](https://www.w3.org/WAI/WCAG22/Understanding/target-size-minimum.html) |

**자주 하는 실수**: 모든 탭에 햅틱; 확인창 남발(경고 피로); 애니메이션이 reduce motion 무시; 색 대비 미달 회색 캡션.
**체크리스트**: [ ] 낙관적 업데이트 + 롤백 테스트 [ ] Undo 5초 유지 [ ] reduce motion 적용.

---

## 찰캌 적용 포인트

1. **푸시**: 사용자 노출 알림은 항상 notification(+data) 메시지. 예약 리마인더는 서버가 KST 기준 발송(클라이언트 스케줄 의존 금지). payload 스키마(`type/route/notification_id/badge`)를 5개 플랫폼 공통 계약으로 두고, Android 채널 4종과 설정 토글을 1:1로 맞춘다.
2. **알림 인박스**는 서버에 저장 — push 유실·권한 거부 사용자도 동일한 정보를 본다. 배지는 서버 unread count가 진실.
3. **딥링크**: 단일 URL 표(`/post/{id}`, `/topic/{date}`), AASA + assetlinks.json 동시 호스팅, 모든 진입 상태(cold/warm/foreground)가 같은 라우터 함수로 수렴, 로그인 후 복귀용 pending route.
4. **사진**: 라이브러리 권한 없이 시스템 Picker 사용. 업로드 전 클라이언트에서 JPEG 변환·리사이즈·EXIF 회전 정규화·GPS 제거, 서버에서도 정규화. 업로드는 멱등 키 + 재시도, 작성 중 draft는 프로세스 종료 대비 저장.
5. **시간**: 서버 UTC 저장, "오늘의 주제"는 KST 기준일을 서버가 내려줌. 포맷은 로케일 API, 24h 설정 존중.
6. **UI 정책**: 정보 구조·동작은 동일, 컴포넌트 외형만 플랫폼 관례(탭바/Snackbar vs 인라인 배너). 확인창 대신 Undo, 4상태(loading/empty/error/offline) 스펙을 화면별로 문서화.
7. **접근성 기준**: 터치 타깃 48 단위, 폰트 스케일 200% 레이아웃 검증, VoiceOver/TalkBack 핵심 플로우 테스트를 5개 플랫폼 공통 DoD에 포함.
8. **버전 정책**: 서버 `min_supported_version`으로 양 OS 동일 강제 업데이트 규칙, minOS는 공식 대시보드 근거로 합의 후 문서화.
9. **로그인**: Apple/Google/Kakao 모두 서버 토큰 검증, 식별자는 provider `sub`/id, 계정 삭제 기능 필수.

---

## 출처 목록

직접 확인함 [확인]:
- https://developer.android.com/develop/ui/views/notifications/notification-permission
- https://developer.android.com/develop/ui/views/layout/edge-to-edge
- https://firebase.google.com/docs/cloud-messaging/concept-options

공식 문서 URL 기반 [공식] (본문 재확인 권장):
- Apple: https://developer.apple.com/documentation/usernotifications/asking-permission-to-use-notifications , https://developer.apple.com/documentation/usernotifications/unusernotificationcenterdelegate , https://developer.apple.com/documentation/usernotifications/pushing-background-updates-to-your-app , https://developer.apple.com/documentation/xcode/supporting-associated-domains , https://developer.apple.com/documentation/photokit/delivering-an-enhanced-privacy-experience-in-your-photos-app , https://developer.apple.com/documentation/backgroundtasks , https://developer.apple.com/documentation/uikit/view_controllers/preserving_your_app_s_ui_across_launches , https://developer.apple.com/documentation/foundation/timezone , https://developer.apple.com/documentation/storekit/requesting-app-store-reviews , https://developer.apple.com/sign-in-with-apple/ , https://developer.apple.com/app-store/review/guidelines/ , https://developer.apple.com/support/app-store/ , https://developer.apple.com/news/upcoming-requirements/
- Apple HIG: https://developer.apple.com/design/human-interface-guidelines/tab-bars , /alerts , /sheets , /layout , /typography , /accessibility , /playing-haptics , /motion , /refresh-content-controls (모두 https://developer.apple.com/design/human-interface-guidelines/ 하위)
- Android: https://developer.android.com/develop/ui/views/notifications/channels , https://developer.android.com/training/app-links/verify-android-applinks , https://developer.android.com/google/play/installreferrer , https://developer.android.com/training/permissions/requesting , https://developer.android.com/training/data-storage/shared/photopicker , https://developer.android.com/training/monitoring-device-state/doze-standby , https://developer.android.com/develop/background-work/background-tasks , https://developer.android.com/develop/background-work/services/alarms , https://developer.android.com/topic/libraries/architecture/saving-states , https://developer.android.com/guide/navigation/custom-back/predictive-back-gesture , https://developer.android.com/develop/ui/views/layout/sw-keyboard , https://developer.android.com/reference/androidx/exifinterface/media/ExifInterface , https://developer.android.com/training/sharing/receive , https://developer.android.com/reference/java/time/ZoneId , https://developer.android.com/about/versions/14/features , https://developer.android.com/about/dashboards , https://developer.android.com/guide/playcore/in-app-updates , https://developer.android.com/guide/playcore/in-app-review , https://developer.android.com/identity/sign-in/credential-manager , https://developer.android.com/develop/ui/views/haptics/haptic-feedback , https://developer.android.com/topic/architecture/data-layer/offline-first
- Firebase: https://firebase.google.com/docs/cloud-messaging/ios/client , https://firebase.google.com/docs/cloud-messaging/android/client , https://firebase.google.com/docs/cloud-messaging/manage-tokens , https://firebase.flutter.dev/docs/messaging/overview
- Material 3: https://m3.material.io/components/navigation-bar/overview , https://m3.material.io/components/snackbar/overview , https://m3.material.io/components/dialogs/overview , https://m3.material.io/components/bottom-sheets/overview , https://m3.material.io/foundations/accessible-design/overview , https://m3.material.io/styles/motion/overview
- 프레임워크: https://docs.flutter.dev/ui/accessibility-and-internationalization/accessibility , https://reactnative.dev/docs/permissionsandroid , https://reactnative.dev/docs/linking , https://reactnative.dev/docs/backhandler , https://reactnative.dev/docs/text , https://reactnative.dev/docs/accessibility , https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Intl/DateTimeFormat
- NN/g: https://www.nngroup.com/articles/skeleton-screens/ , https://www.nngroup.com/articles/empty-state-interface-design/ , https://www.nngroup.com/articles/error-message-guidelines/ , https://www.nngroup.com/articles/infinite-scrolling-tips/ , https://www.nngroup.com/articles/ten-usability-heuristics/ , https://www.nngroup.com/articles/confirmation-dialog/
- W3C: https://www.w3.org/WAI/WCAG22/Understanding/contrast-minimum.html , https://www.w3.org/WAI/WCAG22/Understanding/target-size-minimum.html
- Kakao: https://developers.kakao.com/docs/latest/ko/kakaologin/common

한계: 환경상 WebFetch/검색은 일부만 수행했으며 [공식] 항목은 URL 존재/내용을 이번 세션에서 개별 검증하지 못했다. 서드파티 패키지명(Flutter/RN 플러그인)은 일반 지식이므로 도입 전 pub.dev/npm에서 유지보수 상태를 확인할 것. 점유율·최소 OS 수치는 기재하지 않았다.
