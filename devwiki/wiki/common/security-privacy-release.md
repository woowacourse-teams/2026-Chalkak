# 보안, 개인정보, 난독화, 릴리스

근거: [raw/security-obfuscation-release.md](../../raw/security-obfuscation-release.md), 각 플랫폼 raw의 보안·배포 절, [회원 규칙](../../../docs/business-rules/rules/user.md), [AI 리뷰 규칙](../../../docs/business-rules/rules/ai-review.md).

## 1. 기본 전제

- **클라이언트는 공개된 코드다.** 앱 바이너리와 웹 번들은 누구나 열어 볼 수 있다. 난독화는 크기를 줄이고 분석을 늦출 뿐 보안 경계가 아니다. [공식](https://docs.flutter.dev/deployment/obfuscate)
- 클라이언트에 넣어도 되는 것: Firebase 설정, 소셜 로그인 공개 키(Kakao 네이티브 앱 키 등), Sentry DSN 같은 공개 식별자.
- 넣으면 안 되는 것: 서버 비밀 키, OpenAI 등 AI API 키, 스토리지 자격 증명, 소셜 로그인 client secret. 찰캌은 AI 리뷰와 업로드를 모두 서버 경유·presigned URL로 처리하므로 이 원칙에 맞다. [찰캌규칙]
- 웹의 `VITE_*`·`NEXT_PUBLIC_*`, Flutter `--dart-define`, RN EAS 환경 변수에 넣은 값은 번들에 그대로 들어간다.
- 서버의 권한 검사가 진짜 경계다. 클라이언트에서 버튼을 숨기는 것은 UX일 뿐이다(예: 정지 회원 제한 USER-002는 서버가 막는다).

## 2. 토큰 저장

| 플랫폼 | 저장소 | 주의 |
| --- | --- | --- |
| iOS | Keychain. 접근 클래스는 `AfterFirstUnlockThisDeviceOnly`(백그라운드 갱신 필요 시) 또는 `WhenUnlockedThisDeviceOnly` [공식·미열람] | 앱을 지워도 Keychain이 남는다. 첫 실행 표시(UserDefaults)가 없으면 Keychain을 정리 |
| Android | Keystore 키로 암호화해 DataStore에 저장(Tink AEAD 등) | **EncryptedSharedPreferences(security-crypto)는 전부 deprecated**. 신규 도입 금지 [공식](https://developer.android.com/privacy-and-security/cryptography). Auto Backup에서 토큰 파일 제외(`dataExtractionRules`) |
| Flutter | `flutter_secure_storage` | Android 백업 복원 후 `Failed to unwrap key` 가능 → 재로그인으로 폴백. 웹 구현은 실험적이며 localStorage를 쓴다 [공식](https://pub.dev/packages/flutter_secure_storage) |
| RN | `expo-secure-store` | 일부 iOS에서 약 2KB 넘는 값 거부. Android 백업 제외 설정 [공식](https://docs.expo.dev/versions/latest/sdk/securestore/) |
| Web | httpOnly·Secure·SameSite 쿠키가 원칙. 서버가 지금은 바디로 토큰을 준다 | localStorage는 XSS 한 번에 탈취된다. 서버 협의 전까지는 access는 메모리, refresh 보관 방식은 [open-questions](../open-questions.md#백엔드-확인-대상) [일반지식] |

- 저장소 접근은 한 곳(`TokenStore`)으로 감싼다. access·refresh를 한 번에 원자적으로 저장한다(rotation 때문. [networking §4](networking-errors-retry.md#4-토큰-갱신)).
- 복호화 실패는 크래시가 아니라 "로그아웃 상태"로 처리한다.
- 로그아웃·탈퇴 시 토큰, 사용자 캐시, 로컬 DB, 임시 파일을 전부 지운다.

## 3. 네트워크 보안

- 모든 통신은 HTTPS. iOS ATS와 Android 9+ 기본 설정이 평문을 막는다. 로컬 개발 서버용 예외는 debug 빌드에만 둔다(Android `debug-overrides`, iOS debug xcconfig). [공식](https://developer.android.com/privacy-and-security/security-config)
- **인증서 고정(pinning)은 도입하지 않는다.** 인증서 교체 시 앱 업데이트 전까지 전면 장애가 나고, 강제 업데이트 체계가 먼저 있어야 한다. [제안]
- 웹: HSTS, CSP(스크립트 출처 제한), `X-Content-Type-Options`, 서드파티 스크립트 최소화. 사용자 입력(제목, 피드백)은 프레임워크 기본 이스케이프에 맡기고 `dangerouslySetInnerHTML`을 쓰지 않는다. [일반지식]
- 배너의 이슈 웹 화면(WebView)은 허용 도메인만 열고, JS 브리지를 열지 않는다. [제안]

## 4. 난독화와 심볼

| 플랫폼 | 축소·난독화 | 크래시 복원 파일 | 함정 |
| --- | --- | --- | --- |
| iOS | 없음(심볼 strip) | dSYM | 빌드마다 업로드 자동화 |
| Android | R8(AGP 8부터 full mode 기본) [공식](https://developer.android.com/build/shrink-code) | `mapping.txt` | 직렬화 모델·리플렉션 대상이 지워짐. kotlinx.serialization은 라이브러리 규칙 포함, Gson은 `@Keep` 필요. **release 빌드로 스모크 테스트** |
| Flutter | `--obfuscate --split-debug-info=<dir>` | split-debug-info 심볼 | `--obfuscate`만 켜면 빌드 실패·복원 불가. `runtimeType.toString()` 의존 코드가 깨짐 [공식](https://docs.flutter.dev/deployment/obfuscate) |
| RN | Hermes 바이트코드 + Metro minify | source map(Hermes 합성) | Sentry RN SDK가 빌드 중 자동 업로드 [공식](https://docs.sentry.io/platforms/react-native/sourcemaps/) |
| Web | 번들러 minify | source map | `.map`을 공개 호스팅에 올리지 않고 비공개 업로드 |

- 모든 심볼 파일은 CI 아티팩트로 버전과 함께 보관한다. 과거 버전 크래시를 복원하려면 그 빌드의 파일이 있어야 한다.
- 난독화된 release에서 일부러 크래시를 내 복원되는지 한 번 확인한다.

## 5. 스토어 심사 필수 항목

| 항목 | 근거 | 찰캌 상태 |
| --- | --- | --- |
| Sign in with Apple | App Review 4.8: 서드파티 로그인을 주 계정으로 쓰면 동등한 옵션 필요 [공식](https://developer.apple.com/app-store/review/guidelines/) | 서버 Apple 로그인 지원. Flutter·RN도 iOS에서 Apple 로그인 필수. Android·Web에서 Apple 로그인 제공 여부는 팀 결정 |
| 앱 내 계정 삭제 | Apple 5.1.1(v), Google Play 계정 삭제 정책(앱 내 + 웹 링크) [공식](https://support.google.com/googleplay/android-developer/answer/13327111) | `DELETE /users/me`(USER-003~006). 웹 삭제 경로 필요 |
| 사용자 생성 콘텐츠(UGC) | Apple 1.2: 필터링, **신고**, **차단**, 연락처 [공식](https://developer.apple.com/app-store/review/guidelines/) | 관리자 사전 검수(승인·반려)가 있다. **사용자 신고·차단 기능은 규칙에 없다** → [open-questions](../open-questions.md#백엔드-확인-대상) |
| 사진의 제3자 AI 전송 | Apple 5.1.2: 개인정보를 제3자 AI에 보낼 때 명확한 고지와 동의 [공식](https://developer.apple.com/app-store/review/guidelines/) | AIREVIEW 규칙상 OpenAI 위탁, 동의는 가입 필수 약관으로 갈음하고 별도 안내 없음. 심사 리스크로 기록 |
| 권한 문자열 | iOS `NS*UsageDescription` | 카메라, (필요 시) 사진 저장 |
| 개인정보 표시 | iOS Privacy manifest(`PrivacyInfo.xcprivacy`) + App Privacy, Play Data safety | 다섯 플랫폼 공통 수집 항목 표를 하나로 유지 |
| 추적 | ATT: 다른 회사 데이터와 결합한 추적 시에만 | 현재 해당 없음으로 보임. 광고 SDK 도입 시 재검토 |

## 6. 릴리스와 업데이트

### 버전과 출시

- 사용자 표시 버전은 `MAJOR.MINOR.PATCH`, 빌드 번호는 단조 증가(iOS `CFBundleVersion`, Android `versionCode`, Flutter `x.y.z+build`, Expo `app.json`). CI가 빌드 번호를 올린다.
- 단계적 출시: App Store phased release(7일, 1%→100%, 최대 30일 일시중지) [공식](https://developer.apple.com/help/app-store-connect/update-your-app/release-a-version-update-in-phases/), Play staged rollout(비율 수동, halt 가능) [공식](https://support.google.com/googleplay/android-developer/answer/6346149), 웹은 카나리 + 즉시 롤백.
- 중단 기준(crash-free 비율 등)을 릴리스 전에 적어 둔다.

### 앱 버전 안내(VersionGate)

기존 iOS 앱은 App Store 조회 API(`itunes.apple.com/lookup`)로 최신 버전을 확인하고, Android 앱도 VersionGate 화면이 있다. 서버에는 최소 지원 버전 규칙이 없다. [찰캌규칙]

- 새 플랫폼도 같은 진입 게이트를 둔다. 판정 기준(권장 업데이트 vs 강제)과 비교 방식은 기존 두 앱의 구현을 맞춰 보고 정한다. [open-questions](../open-questions.md#팀-결정-대상)
- Flutter·RN은 스토어 버전 조회를 각 OS 방식으로 한다. 웹은 새 배포 감지 시 "새로고침" 안내(Service Worker 업데이트)로 대체한다.

### OTA 업데이트

- RN EAS Update: JS·문구·에셋 수정만. 네이티브 코드·권한·SDK 변경은 새 바이너리. `runtimeVersion`으로 호환 빌드에만 전달. [공식](https://docs.expo.dev/eas-update/introduction/)
- Flutter Shorebird: Dart 코드 패치. 도입은 선택. [공식](https://docs.shorebird.dev/)
- Apple 2.5.2는 앱 기능을 바꾸는 코드 다운로드를 금지한다. **OTA는 버그 수정과 문구로 한정**한다. [공식](https://developer.apple.com/app-store/review/guidelines/)

### 원격 설정·킬 스위치

- AI 리뷰, 업로드 같은 기능은 원격으로 끌 수 있으면 장애 대응이 빨라진다. 도입 여부와 도구(Firebase Remote Config 등)는 팀 결정이다. [제안]

## 7. 관측성: 크래시, 로그, 분석

- 크래시 도구는 다섯 플랫폼이 같은 것을 쓰면 대시보드가 하나가 된다. Sentry는 다섯 플랫폼 모두, Crashlytics는 iOS·Android·Flutter 공식 지원이고 웹은 없다. 기존 Android 앱은 Firebase Analytics·Messaging만 쓰고 크래시 도구는 없다. [공식](https://firebase.google.com/docs/crashlytics) → [팀 결정](../open-questions.md#팀-결정-대상)
- 크래시 리포트에 `release`(버전+빌드)와 환경을 태깅한다.
- 로그·breadcrumb에 **토큰, Authorization 헤더, 이메일, 서명된 사진 URL, 정확한 위치**를 남기지 않는다. 로깅 래퍼 한 곳에서 마스킹한다.
- 분석 이벤트 이름과 속성은 다섯 플랫폼이 한 사전을 공유한다(snake_case, 같은 타입). 사전 위치는 [testing-parity.md §4](testing-parity.md#4-분석-이벤트-동등성).

## 8. 개인정보와 권한

- 권한은 쓰는 순간에 요청한다. 거부해도 다른 기능은 계속 쓴다. 상세 UX는 [ux-accessibility.md §5](ux-accessibility.md#5-권한-요청).
- 사진 위치 정보: 클라이언트 재인코딩으로 제거하는 것을 기본으로 하고 서버도 검증하는지 확인한다. [media-upload.md §3](media-upload.md#31-인코딩)
- 개인정보 처리방침, Privacy manifest, Data safety의 수집 항목이 서로 일치해야 한다. 새 SDK를 넣으면 세 곳을 함께 고친다.

## 9. 소셜 로그인

서버는 `POST /auth/social-login`에 `{ provider, idToken, rawNonce, authorizationCode? }`를 받는다. 제공자는 `GOOGLE`, `KAKAO`, `APPLE`. [찰캌규칙] ([SocialLoginRequest](../../../backend/src/main/java/com/chalkak/backend/auth/api/v1/dto/request/SocialLoginRequest.java))

- 클라이언트가 원본 nonce를 만들고, SDK에는 **SHA-256 소문자 hex**를 넘기며, 서버에는 원본(`rawNonce`)을 보낸다. 다섯 플랫폼이 같은 방식으로 만든다(안전한 난수).
- Apple만 일회용 `authorizationCode`를 함께 보낸다. 다른 제공자가 보내면 서버가 거절한다.
- Apple은 이메일을 최초 1회만 주고 숨김 이메일일 수 있다. 식별은 서버가 `sub`로 한다.
- 각 플랫폼·제공자별 클라이언트 ID(iOS 번들, Android 패키지·서명 지문, 웹 출처)가 서버의 허용 audience에 들어가야 한다. 새 플랫폼을 추가할 때마다 확인한다. [open-questions](../open-questions.md#백엔드-확인-대상)
- 플랫폼별 SDK: iOS `AuthenticationServices`·Google Sign-In·Kakao SDK, Android Credential Manager(Google)·Kakao SDK(디버그·릴리스 키 해시 모두 등록), Apple은 Android·웹에서 웹 OAuth, Flutter `sign_in_with_apple`·`google_sign_in`·`kakao_flutter_sdk`, RN `expo-apple-authentication`·Google·Kakao 라이브러리, 웹은 각 JS SDK. [일반지식]
- 로그인 취소는 오류가 아니다. 취소·네트워크 오류·서버 거절을 구분해 다섯 플랫폼이 같은 문구를 쓴다.

## 체크리스트

- [ ] 번들·바이너리에 비밀 키가 없다(`strings`, 번들 grep, CI secret scanning)
- [ ] 토큰은 안전 저장소에만, 백업 제외, 복호화 실패 시 로그아웃 상태
- [ ] release 빌드에 평문 HTTP 예외가 없다
- [ ] 난독화된 release 빌드로 스모크 테스트를 했고 심볼 파일이 보관·업로드된다
- [ ] iOS에 Apple 로그인, 모든 플랫폼에 앱 내 계정 삭제가 있다
- [ ] 로그에 토큰·개인정보가 남지 않는다
- [ ] 권한 문자열·Privacy manifest·Data safety가 최신이다
