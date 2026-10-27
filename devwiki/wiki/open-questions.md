# 미결 질문

wiki를 만들며 나온 질문이다. 답이 정해지면 해당 문서를 고치고 여기서 지운다. CLAUDE.md에 따라 client 작업은 정책을 임의로 정하지 않으므로, 아래 "백엔드 확인 대상"은 백엔드 담당과 합의한 뒤 `docs/business-rules`에 반영되어야 클라이언트 구현 기준이 된다.

## 백엔드 확인 대상

| # | 질문 | 왜 필요한가 | 관련 문서 |
| --- | --- | --- | --- |
| B1 | 사용자 **신고·차단** 기능을 둘 것인가 | App Store 1.2(UGC)는 필터링 외에 신고·차단을 요구한다. 지금은 관리자 사전 검수만 있다 | [security §5](common/security-privacy-release.md#5-스토어-심사-필수-항목) |
| B2 | 업로드 사진의 **EXIF·위치 정보**를 서버도 제거·검증하는가 | 클라이언트 재인코딩만 믿으면 플랫폼별로 결과가 갈린다 | [media-upload §3.1](common/media-upload.md#31-인코딩) |
| B3 | 429·503에 **Retry-After**를 보내는가, 웹에 노출(`Access-Control-Expose-Headers`)하는가 | 재시도 상수와 우선순위가 달라진다 | [networking §2.2](common/networking-errors-retry.md#22-어떻게-재시도하나) |
| B4 | 게시물 삭제 재시도에서 이미 지운 게시물이 404면 클라이언트가 성공으로 봐도 되는가 | DELETE 자동 재시도 정책 | [networking §2.1](common/networking-errors-retry.md#21-무엇을-재시도하나) |
| B5 | `POST /posts` 응답 유실 후 재전송 시 오는 오류 코드가 무엇인가(POST-003·004) | "이미 올라갔을 수 있음" 처리 기준 | [media-upload §3.2](common/media-upload.md#32-업로드-흐름) |
| B6 | 웹앱용 **CORS**를 `/api/v1/**`에 열 것인가, 같은 도메인 프록시로 갈 것인가 | 웹앱이 서버를 부를 수 없다 | [web.md](platforms/web.md#먼저-풀어야-할-서버-쪽-전제) |
| B7 | 웹의 **토큰 보관**: httpOnly 쿠키를 지원할 것인가 | localStorage 보관은 XSS 위험 | [security §2](common/security-privacy-release.md#2-토큰-저장) |
| B8 | Flutter·RN·웹의 **소셜 로그인 클라이언트 ID**(Google 웹 클라이언트, Apple Services ID, Kakao 웹 키)를 서버 audience에 추가할 수 있는가 | 새 플랫폼에서 로그인이 거절된다 | [security §9](common/security-privacy-release.md#9-소셜-로그인) |
| B9 | **웹 푸시**: FCM 웹 토큰을 `PUT /push-devices/current`로 받아도 되는가 | 서버는 FCM으로만 보낸다 | [push §2](common/push-deeplink-navigation.md#2-앱-상태별-수신-위치) |
| B10 | 서버의 **최소 지원 앱 버전** 규칙을 둘 것인가 | 지금은 iOS가 App Store 조회로 판단한다. 다섯 플랫폼 같은 기준이 필요하면 서버 값이 낫다 | [security §6](common/security-privacy-release.md#6-릴리스와-업데이트) |
| B11 | AI 리뷰의 OpenAI 전송 동의를 가입 약관으로 갈음하는 것이 App Store 5.1.2(i) 심사에서 충분한가 | 심사 반려 위험 | [security §5](common/security-privacy-release.md#5-스토어-심사-필수-항목) |

## 팀 결정 대상

| # | 질문 | 선택지와 권고 | 관련 문서 |
| --- | --- | --- | --- |
| T1 | 기존 iOS·Android 앱도 동등성 점검 대상인가 | 권고: 예. 기존 앱 동작을 기준선으로 삼고 차이를 매트릭스에 기록 | [feature-map §1](chalkak/feature-map.md#1-현재-레포-상태) |
| T2 | 앱이 백그라운드로 가도 업로드를 끝내는 기능을 넣을 것인가 | 권고: 첫 단계는 넣지 않음(기존 Android와 같게, 웹은 불가). 넣으면 웹은 허용된 차이 | [media-upload §4](common/media-upload.md#4-백그라운드-업로드) |
| T3 | Flutter 상태 관리 | 권고: 공식 가이드의 `ChangeNotifier` + Command. Riverpod·Bloc은 팀 숙련도에 따라 | [flutter.md](platforms/flutter.md) |
| T4 | RN Expo SDK 버전과 Reanimated 사용 | SDK 56(Reanimated 메모리 회귀) vs SDK 57 대기 | [react-native.md](platforms/react-native.md#버전-기준-2026-10) |
| T5 | 웹 프레임워크 | 권고: React + Vite SPA. SEO가 필요한 공개 페이지가 생기면 Next.js 재검토 | [web.md](platforms/web.md#권장-스택-제안) |
| T6 | 레포 구조: 새 클라이언트를 `client/flutter`, `client/react-native`, `client/webapp`에 둘지, RN·웹 공용 TS 패키지를 둘지 | 공용 패키지는 API 타입·쿼리 키·문구만 | |
| T7 | Android·웹에서 Apple 로그인을 제공할 것인가 | iOS는 필수. 나머지는 계정 이동성(iOS에서 Apple로 가입한 사람이 웹에서 로그인)을 위해 제공 권고 | [security §9](common/security-privacy-release.md#9-소셜-로그인) |
| T8 | 크래시 리포팅 도구 | Sentry(다섯 플랫폼 한 도구) vs Crashlytics(모바일) + Sentry(웹) | [security §7](common/security-privacy-release.md#7-관측성-크래시-로그-분석) |
| T9 | 원격 설정·킬 스위치 도입 | AI 리뷰·업로드 장애 대응용 | [security §6](common/security-privacy-release.md#6-릴리스와-업데이트) |
| T10 | 공통 상수 확정(타임아웃, 재시도, 로딩 표시) | 표의 [제안] 값을 확정 | [networking 상수](common/networking-errors-retry.md#찰캌-공통-상수) |
| T11 | 상대 시간 표기 경계값, 버전 게이트 판정 방식 | 기존 iOS·Android 구현을 비교해 하나로 | [ux §6](common/ux-accessibility.md#6-날짜시간대) |
| T12 | 공유 링크(Universal Links·App Links) 도입 여부 | 지금은 푸시 내부 라우트만 | [push §4](common/push-deeplink-navigation.md#4-딥링크) |
| T13 | 심사용 데모 계정 준비 방법 | 소셜 로그인만 있어 별도 준비 필요(App Review 2.1) | [ios.md](platforms/ios.md#심사) |

## 다시 확인할 리서치 항목

리서치 중 본문을 직접 열지 못한 항목이다. 구현에 쓰기 전에 공식 문서로 확인한다.

- Apple 문서 다수(Keychain 접근 클래스, PhotosPicker, HIG 44pt, ATS, Privacy manifest)의 본문. HIG `refresh-content-controls` 경로는 404였다.
- iOS ImageIO의 WebP **쓰기** 지원 버전, Safari `canvas.toBlob('image/webp')` 지원 여부, `expo-image-manipulator`의 WebP 저장 지원 범위
- `firebase_messaging` 토큰 API 변경(`register()`/`onRegistered`) 보고
- RN에서 FCM 기기 토큰을 얻는 방법(Expo `getDevicePushTokenAsync`는 iOS에서 APNs 토큰)
- FlashList v2 세부 API, Zustand/RTK 문서
- Retrofit·Gson R8 규칙 공식 페이지
- GDPR·개인정보보호법 원문(출시 전 법무 검토 필요)
