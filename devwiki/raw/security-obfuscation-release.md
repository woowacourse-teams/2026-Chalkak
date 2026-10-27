# 보안 · 난독화 · 릴리스 엔지니어링 · 관측성 (5 플랫폼)

> 대상: 찰캌(Chalkak) — 사진 게시/좋아요/푸시/AI 사진 리뷰/이미지 업로드/JWT access·refresh 로그인/소셜 로그인
> 플랫폼: iOS native · Android native · Flutter · React Native(RN) · Web
> 작성일: 2026-10-27
> 표기 규칙: `[출처](URL)`은 이번 조사에서 직접 열람한 공식 문서. **(미확인)** 표시는 이번 조사에서 공식 페이지 본문을 직접 확인하지 못했거나 일반 지식에 기반한 내용으로, 적용 전 원문 재확인 필요.

---

## 0. 핵심 메시지 (TL;DR)

1. 난독화(obfuscation)는 보안 경계가 아니다. 심볼 이름만 바꿀 뿐이므로 **클라이언트에 비밀(secret)을 넣지 않는다.** ([Flutter 문서](https://docs.flutter.dev/deployment/obfuscate): "Don't store secrets in the app.")
2. 토큰은 플랫폼 보안 저장소(Keychain / Keystore 기반)에, Web은 httpOnly 쿠키를 우선한다.
3. 릴리스마다 **심볼 파일(dSYM / mapping.txt / --split-debug-info / source map)** 을 보관·업로드하지 않으면 크래시 리포트는 쓸모없어진다.
4. 스토어 정책 3종 세트: **계정 삭제(앱 내)**, **소셜 로그인 사용 시 Sign in with Apple 동등 옵션(4.8)**, **Privacy manifest / Data safety**.
5. OTA(EAS Update / Shorebird)는 "기능 변경 없는 버그 수정" 수준으로 운영 정책을 제한한다.

---

## 1. OWASP MASVS/MASTG · OWASP Top 10

### 핵심 개념
- MASVS는 모바일 앱 보안 요구사항 표준, MASTG는 테스트 가이드. MASVS 컨트롤 그룹은 8개: STORAGE, CRYPTO, AUTH, NETWORK, PLATFORM, CODE, RESILIENCE, PRIVACY. ([MASVS](https://mas.owasp.org/MASVS/))
- Web은 OWASP Top 10. 현재 최신판은 2025판으로 리다이렉트됨. ([OWASP Top 10](https://top10.owasp.org/)) 카테고리 상세는 [2025 목록](https://top10.owasp.org/2025/en/)에서 확인 (본 조사에서 본문 미열람, **미확인**). 통상 Broken Access Control, Security Misconfiguration, Cryptographic Failures, Injection, Insecure Design, Authentication Failures, Logging/Alerting Failures 등이 포함.

### 사진 앱에 대한 MASVS 매핑
| MASVS 그룹 | 찰캌에서의 의미 |
|---|---|
| STORAGE | access/refresh 토큰, 임시 업로드 파일, 캐시된 사진/EXIF 보호. 로그·크래시 리포트에 PII 금지 |
| CRYPTO | 직접 암호 구현 금지. 플랫폼 Keystore/Keychain 사용 |
| AUTH | 서버 측 토큰 검증, refresh rotation, 소셜 로그인 토큰 서버 검증, 생체 인증은 선택 |
| NETWORK | TLS 강제, cleartext 금지, (선택) pinning |
| PLATFORM | 딥링크/푸시 payload 검증, WebView 설정, 클립보드·스크린샷 |
| CODE | 의존성 취약점 점검, 입력 검증, 디버그 빌드 분리 |
| RESILIENCE | root/jailbreak 탐지, 난독화 - 소비자 사진 앱에서는 우선순위 낮음 (옵션) |
| PRIVACY | 최소 수집, 위치 EXIF 제거, 동의/삭제 |

### 플랫폼별 매핑
| 항목 | iOS | Android | Flutter | RN | Web |
|---|---|---|---|---|---|
| 적용 표준 | MASVS/MASTG | MASVS/MASTG | MASVS(+플러그인 감사) | MASVS(+JS 번들/네이티브 모듈) | OWASP Top 10, ASVS |
| 가장 큰 위험 | 토큰 저장, ATS 예외 | 백업/Keystore, 취소 불가 cleartext 예외 | 서드파티 플러그인 | JS 번들 내 비밀, OTA | XSS로 토큰 탈취, 접근 제어 |

### 자주 하는 실수
- 서버의 접근 제어(게시물 소유자 확인)를 클라이언트 UI 숨김으로 대체.
- 보안 점검을 출시 직전에만 수행.

### 체크리스트
- [ ] MASVS L1 기준 자체 점검표를 릴리스 게이트에 포함
- [ ] 서버 API는 IDOR(타인 게시물 수정/삭제) 테스트
- [ ] 의존성 취약점 스캔(CI)

---

## 2. 안전한 토큰 저장

### 핵심 개념
- access token은 짧게(분 단위), refresh token은 rotation + 재사용 탐지를 서버에서 구현한다 (서버 정책, 클라이언트는 저장만 담당).
- 저장 위치: 모바일은 Keychain/Keystore 기반 저장소, Web은 httpOnly+Secure+SameSite 쿠키 (**일반 보안 권고, 이번 조사 미확인**).

### 공식 가이드라인
- **iOS Keychain accessibility class**: `kSecAttrAccessible`로 지정. 원칙은 가장 제한적인 클래스 사용, 기기 종속 토큰은 `ThisDeviceOnly`, 백그라운드 작업이 필요하면 `AfterFirstUnlock(ThisDeviceOnly)`, `Always`는 deprecated. ([Apple 문서](https://developer.apple.com/documentation/security/restricting-keychain-item-accessibility), 본문 미열람으로 **미확인**; 유사 내용이 [Expo SecureStore 문서](https://docs.expo.dev/versions/latest/sdk/securestore/)의 keychainAccessible 옵션 설명에서 확인됨)
- **Android**: `androidx.security:security-crypto`(EncryptedSharedPreferences 포함)의 모든 API는 1.1.0 stable에서 deprecated, 이후 릴리스 없음. Android Keystore 사용 시 provider를 명시적으로 지정하지 말 것. ([Android Cryptography](https://developer.android.com/privacy-and-security/cryptography)) 대안으로 Keystore 키 + DataStore에 Tink(AEAD) 암호화 값을 저장하는 패턴이 일반적 **(미확인, Tink 공식 문서 참조 필요)**.
- **Flutter flutter_secure_storage**: iOS는 Keychain. Android는 자체 암호화(기본 RSA OAEP 키 래핑 + AES-GCM), EncryptedSharedPreferences는 더 이상 권장하지 않으며 v10에서 자동 마이그레이션. Web 구현은 experimental이며 키가 LocalStorage에 저장됨. Android Auto Backup 시 `Failed to unwrap key` 발생 가능. minSdk 23. ([pub.dev](https://pub.dev/packages/flutter_secure_storage))
- **RN expo-secure-store**: Android는 Keystore로 암호화한 SharedPreferences, iOS는 Keychain generic password. 기본 `WHEN_UNLOCKED`. iOS 재설치 시 값이 남을 수 있으나 보장되지 않음. Android는 Auto Backup에서 SecureStore 항목 제외 필요. 일부 iOS에서 약 2048 bytes 초과 값 거부 사례. ([Expo SecureStore](https://docs.expo.dev/versions/latest/sdk/securestore/)) react-native-keychain은 대안(이번 조사에서 공식 문서 미열람, **미확인**).
- **Web**: localStorage는 JS에서 읽을 수 있어 XSS 시 탈취됨. flutter_secure_storage 문서도 Web에서 JS hijacking 위험과 엄격한 HTTPS 헤더 필요를 경고. ([pub.dev](https://pub.dev/packages/flutter_secure_storage))

### 플랫폼별 매핑
| 항목 | iOS | Android | Flutter | RN | Web |
|---|---|---|---|---|---|
| 저장소 | Keychain | Keystore 키 + DataStore/Tink | flutter_secure_storage | expo-secure-store / react-native-keychain | httpOnly 쿠키 (BFF/동일 사이트) |
| 권장 옵션 | `AfterFirstUnlockThisDeviceOnly` (푸시 후 백그라운드 동기화 시) 또는 `WhenUnlockedThisDeviceOnly` | EncryptedSharedPreferences 신규 도입 지양 | Android 백업 제외 | `keychainAccessible` 지정, 백업 제외 | `Secure; HttpOnly; SameSite` |
| 주의 | 앱 삭제 후 Keychain 잔존 가능 -> 첫 실행 시 정리 로직 | 백업 복원 시 복호화 실패 | 위와 동일 | 위와 동일 | CSRF 대응 필요 |

### 자주 하는 실수
- 토큰을 SharedPreferences/UserDefaults/AsyncStorage/localStorage에 평문 저장.
- Android Auto Backup 설정 누락으로 복원 후 크래시.
- 로그아웃 시 Keychain 항목·푸시 토큰 서버 등록 해제 누락.
- deprecated된 EncryptedSharedPreferences를 신규 코드에 도입.

### 체크리스트
- [ ] 토큰 저장소 래퍼 1곳으로 추상화 (플랫폼별 구현)
- [ ] 백업 제외 설정 (`allowBackup`/`dataExtractionRules`)
- [ ] 로그아웃/계정 삭제 시 저장소 전체 삭제
- [ ] 복호화 실패 시 재로그인으로 폴백

---

## 3. 네트워크 보안

### 핵심 개념
- 모든 통신은 TLS. cleartext 예외를 만들지 않는다.
- Certificate pinning은 보안 이득이 있으나 운영 리스크(인증서 교체 시 접속 불가)가 크다.

### 공식 가이드라인
- **Android**: API 28(Android 9) 이상에서 cleartext 기본 비활성. `base-config`에서 `cleartextTrafficPermitted="true"`는 가능한 한 피할 것. Pinning은 백업 핀 필수, `expiration` 설정은 양날의 검(만료 후 pinning 해제 -> 우회 가능), `debug-overrides`는 debuggable일 때만 적용. ([Network security config](https://developer.android.com/privacy-and-security/security-config))
- **iOS ATS**: 기본으로 HTTPS 강제. 예외는 `NSAppTransportSecurity`로 지정. (공식 본문 이번 조사 미열람, **미확인**; Apple 문서 "Preventing Insecure Network Connections" 참조)
- Flutter/RN은 각 네이티브 레이어의 ATS/NSC 설정을 그대로 따름 (dev 서버용 cleartext 예외가 release에 남지 않게 주의). **(미확인)**
- Web: HTTPS + HSTS + CSP (**일반 권고, 미확인**).

### 플랫폼별 매핑
| 항목 | iOS | Android | Flutter | RN | Web |
|---|---|---|---|---|---|
| 강제 설정 | ATS (기본 on) | NSC, API28+ cleartext off | iOS/Android 설정 상속 | 동일 (Info.plist/NSC) | HSTS, CSP |
| 개발용 예외 | ATS 예외 debug 전용 xcconfig | `debug-overrides` | flavor별 설정 | 빌드 variant별 | dev server만 |
| Pinning | 가능(선택) | NSC pin-set(선택) | 플러그인/Dio 설정(선택) | 라이브러리(선택) | 불가(브라우저) |

### Pinning 장단점
- 장점: 악성 CA·MITM 방어 강화.
- 단점: 인증서/키 교체 시 앱 업데이트 전까지 장애, 백업 핀 관리 부담, 강제 업데이트 체계 없으면 위험. ([Android 문서](https://developer.android.com/privacy-and-security/security-config))
- 찰캌 권고: **초기에는 pinning 미도입**, TLS + 서버측 보호 중심. 도입한다면 SPKI 핀 + 백업 핀 + 강제 업데이트 체계 선행.

### 자주 하는 실수
- `usesCleartextTraffic="true"` 또는 `NSAllowsArbitraryLoads`를 release에 방치.
- 핀 만료/교체 계획 없이 pinning 도입.
- 프록시 디버깅용 user CA 신뢰를 release에 허용.

### 체크리스트
- [ ] release 빌드에서 HTTP 요청 시 실패 확인
- [ ] 스테이징/로컬 호스트 예외는 debug variant 한정
- [ ] pinning 도입 시 교체 runbook 작성

---

## 4. 난독화 · 축소 · 심볼

### 핵심 개념
- 난독화/축소는 크기 감소와 역공학 난이도 상승 목적이지 보안 경계가 아님. 비밀은 서버에.
- 난독화를 켜면 스택트레이스가 읽을 수 없으므로 **심볼 파일을 빌드마다 보관**해야 한다.

### 공식 가이드라인
- **Android R8**: AGP 8.0부터 full mode 기본. 리플렉션 등에 대해 더 엄격한 가정을 하므로 추가 keep rule이 필요할 수 있음. opt-out은 `android.enableR8.fullMode=false`. 최근 AGP(9.3+)는 `.keep` 확장자 규칙 파일 사용. Logcat은 AGP 8.12부터 자동 retrace. ([Shrink code](https://developer.android.com/build/shrink-code))
  - mapping.txt 생성 위치 및 Play Console 업로드 절차는 해당 페이지에 없음. App Bundle 업로드 시 mapping 포함/별도 업로드는 Play Console 도움말 참조 **(미확인)**.
  - 흔한 크래시: kotlinx.serialization/Gson/Moshi(리플렉션) 모델 클래스 필드 제거·이름 변경, Retrofit 인터페이스, JNI, 리플렉션으로 접근하는 enum/클래스. -> 모델에 `@Keep`/라이브러리 제공 consumer rule 사용 (일반 지식, **미확인**).
- **iOS**: Swift는 네이티브 컴파일이라 내장 난독화 없음(일반 지식). 심볼은 dSYM으로 관리하여 Crashlytics/Sentry에 업로드. Bitcode는 Xcode 14에서 제거됨 (**미확인**, Apple 릴리스 노트 참조).
- **Flutter**: `flutter build <target> --obfuscate --split-debug-info=<dir>`. SYMBOLS 파일 백업 필수. `flutter symbolize -i crash.txt -d <symbols>`로 복원. 난독화는 release만 가능, Web은 난독화 대신 minify. `runtimeType.toString()`처럼 이름에 의존하는 코드는 깨짐. ([Flutter 문서](https://docs.flutter.dev/deployment/obfuscate))
- **React Native**: release에서 JS가 Hermes bytecode(.hbc)로 컴파일되어 기본 배포. ([Hermes](https://reactnative.dev/docs/hermes)) 크래시 스택 복원은 source map 필요; Sentry RN SDK 5.11.0+ 는 Xcode/Gradle 빌드 중 source map 자동 업로드. ([Sentry RN](https://docs.sentry.io/platforms/react-native/sourcemaps/))
- **Web**: Sentry는 Debug ID를 번들에 주입하고 빌드 시 업로드 권장. ([Sentry JS sourcemaps](https://docs.sentry.io/platforms/javascript/sourcemaps/)) 공개 서버에 .map을 배포하지 않는 것이 권장(일반 권고, **미확인**).

### 플랫폼별 매핑
| 항목 | iOS | Android | Flutter | RN | Web |
|---|---|---|---|---|---|
| 난독화/축소 | 없음(심볼 strip) | R8 (minify+shrinkResources) | `--obfuscate` | Hermes bytecode + Metro minify | bundler minify |
| 복원 파일 | dSYM | mapping.txt | `--split-debug-info` symbols | source map (+Hermes) | source map |
| 업로드 대상 | Crashlytics/Sentry | Play Console, Crashlytics/Sentry | Crashlytics/Sentry | Sentry/Crashlytics(RN은 Firebase 공식 지원 문서 미확인) | Sentry (비공개 업로드) |
| CI 보관 | 아티팩트 | 아티팩트 | 아티팩트(필수) | 아티팩트 | 아티팩트 |

### 자주 하는 실수
- 빌드 후 symbols/mapping을 버려 과거 버전 크래시 복원 불가.
- R8 활성화 후 release 테스트 없이 배포 -> 직렬화 모델 크래시.
- Flutter `--obfuscate`만 켜고 `--split-debug-info` 누락.
- source map을 프로덕션 정적 호스팅에 공개.
- "난독화했으니 API 키를 넣어도 된다"는 오해.

### 체크리스트
- [ ] release 변형을 CI에서 실제 설치·스모크 테스트 (minify 켠 상태)
- [ ] 빌드 버전 <-> 심볼 파일 매핑을 아티팩트로 보관
- [ ] 업로드 자동화 (Gradle/Xcode run script/sentry-cli/플러그인)
- [ ] 난독화 상태 크래시 1건을 사전 발생시켜 복원 리허설

---

## 5. API 키 · 비밀 · 앱 무결성 검증

### 핵심 개념
- 클라이언트에 넣어도 되는 것: 공개 식별자(Firebase config, 공개 SDK key, Sentry DSN, Maps 제한 키 등). 단 Firebase는 보안 규칙/App Check로 보호.
- 절대 넣으면 안 되는 것: 서버 비밀키, DB 자격 증명, AI 모델(LLM/비전) API 키, 스토리지 서비스 계정, JWT 서명 키, 소셜 로그인 client secret.
- AI 사진 리뷰·이미지 업로드는 **서버 경유** 또는 서명된 단기 URL(presigned URL)로 처리.

### 공식 가이드라인
- Firebase App Check: 백엔드 리소스를 허가되지 않은 클라이언트로부터 보호. 제공자: Apple = DeviceCheck/App Attest/reCAPTCHA Enterprise, Android = Play Integrity/reCAPTCHA Enterprise, Web = reCAPTCHA Enterprise. 모든 남용을 막지는 못하며, 서비스별 enforcement를 켜야 효과가 있음. 커스텀 백엔드에도 사용 가능. Play Integrity Standard 일 10,000 호출 한도. ([App Check](https://firebase.google.com/docs/app-check))
- Flutter 문서: 난독화는 비밀 보호가 아님. ([Flutter](https://docs.flutter.dev/deployment/obfuscate))

### 플랫폼별 매핑
| 항목 | iOS | Android | Flutter | RN | Web |
|---|---|---|---|---|---|
| 빌드타임 설정 | xcconfig / Info.plist | `buildConfigField`, flavor | `--dart-define(-from-file)` (**미확인**) | EAS env / react-native-config (**미확인**) | Vite `VITE_*` 등(전부 공개됨) |
| 무결성 | App Attest/DeviceCheck | Play Integrity | 각 네이티브 + firebase_app_check | 동일 | reCAPTCHA Enterprise |
| 비밀 노출 위험 | 바이너리 strings | APK decompile | 바이너리 strings | JS 번들 평문에 가까움 | 번들 전체 공개 |

### 자주 하는 실수
- `.env`의 서버 키를 `--dart-define`/`VITE_` 로 주입(번들에 그대로 포함).
- App Check를 켜기만 하고 enforcement/모니터링 없이 방치, 혹은 enforcement를 바로 켜서 구버전 앱 차단.
- 클라이언트에서 AI API 직접 호출.

### 체크리스트
- [ ] 클라 번들에서 `strings`/`apktool`/번들 grep으로 비밀 스캔
- [ ] CI secret scanning (예: gitleaks)
- [ ] App Check: 모니터링 모드 -> 점유율 확인 -> enforcement
- [ ] 서버는 무결성 토큰을 "추가 신호"로만 사용 (유일한 인증 수단 아님)

---

## 6. 크래시 리포팅 · 로깅 · 분석

### 핵심 개념
- 도구 선택: Firebase Crashlytics(무료, Firebase 생태계) vs Sentry(5 플랫폼 일관, 웹 포함). 팀 통일을 위해 **Sentry 단일 도구** 또는 Crashlytics(모바일)+Sentry(웹) 선택.

### 공식 가이드라인
- Crashlytics: Apple, Android, Flutter, Unity 지원으로 문서화. Apple은 dSYM 필요. RN/Web 지원은 해당 개요 페이지에 언급 없음. ([Crashlytics](https://firebase.google.com/docs/crashlytics))
- Sentry: RN SDK source map 자동 업로드 ([Sentry RN](https://docs.sentry.io/platforms/react-native/sourcemaps/)), Flutter는 `sentry_flutter` + `sentry_dart_plugin`, Wizard 지원 ([Sentry Flutter](https://docs.sentry.io/platforms/flutter/)), Web은 Debug ID + bundler 플러그인 ([Sentry JS](https://docs.sentry.io/platforms/javascript/sourcemaps/)). iOS/Android native SDK도 존재 **(본 조사에서 개별 페이지 미열람)**.
- PII 스크러빙: Sentry의 `beforeSend`, 서버측 데이터 스크러빙 규칙 **(미확인, Sentry data management 문서 참조)**.

### 플랫폼별 매핑
| 항목 | iOS | Android | Flutter | RN | Web |
|---|---|---|---|---|---|
| Crashlytics | O | O | O | 공식 개요에 언급 없음(커뮤니티 래퍼 `@react-native-firebase/crashlytics`는 **미확인**) | X (개요에 언급 없음) |
| Sentry | O (미열람) | O (미열람) | O | O | O |
| 심볼 | dSYM | mapping.txt | split-debug-info | source map/Hermes | source map |
| 로깅 | os.Logger | Timber/Logcat | logger/logging 패키지 | 자체 wrapper | console wrapper |

### 로깅/분석 원칙 (일반 권고, 미확인)
- 구조화 로그: `level, event, screen, request_id, app_version` 키 고정. 토큰/이메일/전화번호/사진 URL(서명 포함)/정확한 위치는 기록 금지.
- 사용자 식별자는 내부 불투명 ID만. 이메일 해시도 지양.
- 분석 이벤트 이름은 5 플랫폼 공통 사전 사용: 예) `post_create_started`, `post_create_succeeded`, `post_like_toggled`, `login_succeeded{provider}`, `photo_review_requested`. snake_case, 과거분사/동사 규칙 통일, 파라미터 타입 동일.
- Firebase Analytics 이벤트 이름 규칙/제한은 공식 문서 확인 필요.

### 자주 하는 실수
- 네트워크 로그(Authorization 헤더, 요청 본문)를 Sentry breadcrumb에 그대로 전송.
- 플랫폼마다 이벤트 이름이 달라 대시보드 병합 불가.
- 릴리스/빌드 번호를 크래시 리포트에 태깅하지 않음.

### 체크리스트
- [ ] `release = <semver>+<build>`, `dist` 태깅
- [ ] 로깅 래퍼에서 민감 키 마스킹
- [ ] 이벤트 사전 문서 1개를 5 플랫폼이 공유
- [ ] 사용자 동의/옵트아웃 연동

---

## 7. 릴리스 엔지니어링

### 7.1 버전 규칙
- 사용자 표시 버전은 semver(`MAJOR.MINOR.PATCH`)를 따르고, 빌드 번호는 단조 증가: iOS `CFBundleShortVersionString`/`CFBundleVersion`, Android `versionName`/`versionCode`. Flutter `pubspec.yaml`의 `version: x.y.z+build`, RN/Expo는 각각 네이티브 값 또는 app.json. (일반 규칙, **미확인**)
- CI가 빌드 번호를 자동 증가시키고 5 플랫폼 버전 정책(마케팅 버전 동기화 여부)을 문서화.

### 7.2 단계적 출시
- **Play 단계적 출시**: 비율을 수동으로 올림(Manage rollout > Update rollout), 중단(halt) 시 이미 받은 사용자는 유지, 수정은 새 릴리스로 배포. 업데이트에만 사용 가능(최초 게시 불가). ([Play 도움말](https://support.google.com/googleplay/android-developer/answer/6346149))
- **App Store 단계적 출시(Phased Release)**: 7일간 1% -> 2% -> 5% -> 10% -> 20% -> 50% -> 100%, 총 30일까지 일시중지 가능, 수동 다운로드는 항상 가능. ([Apple](https://developer.apple.com/help/app-store-connect/update-your-app/release-a-version-update-in-phases/))
- Web은 점진 배포/카나리 + 즉시 롤백 가능.

### 7.3 강제 업데이트 / 최소 지원 버전
- Remote Config로 "minimum app version" 같은 파라미터 관리 가능(문서에 use case로 나열되나 상세 없음). 사용자 승인이 필요한 업데이트에 Remote Config를 쓰지 말라는 경고 있음. 클라이언트가 값을 캐시하고 activate 시점을 앱이 결정. ([Remote Config](https://firebase.google.com/docs/remote-config))
- 권고: 서버 API가 `X-App-Version`을 받아 `426 Upgrade Required` + 스토어 링크를 반환하는 방식으로 서버 주도 차단. soft update(권장)와 hard update(강제) 2단계. API 하위 호환 정책(예: 최근 N개 마이너 지원) 문서화. (설계 권고)

### 7.4 Feature flag / Remote Config
- Remote Config: iOS+, Android, Web, Flutter, Unity, C++ 클라이언트 지원, 서버 SDK도 있음. 롤아웃은 flag + 동일 크기 control 그룹 비교. RN은 `@react-native-firebase` 래퍼 경유 **(미확인)**. ([Remote Config](https://firebase.google.com/docs/remote-config))
- flag에 수명(만료일)과 소유자를 지정. 킬 스위치(AI 리뷰, 업로드 등) 우선.

### 7.5 OTA 업데이트
- **EAS Update(RN/Expo)**: JS 버그 수정, 문구, 스타일, 에셋은 OTA 가능. 네이티브 코드/의존성, 권한, SDK 버전 변경은 새 바이너리 필요. runtime version으로 호환 빌드에만 전달. 스토어 정책 준수 책임은 개발자. ([EAS Update](https://docs.expo.dev/eas-update/introduction/))
- **Shorebird(Flutter)**: 스토어 심사 없이 Dart 코드 패치를 배포하는 code push. Flutter 3.27.0+ 필요. 정책 관련 서술은 해당 페이지에 없음. ([Shorebird](https://docs.shorebird.dev/))
- **App Store Guideline 2.5.2**: 앱은 번들 내 자기완결적이어야 하며 "앱의 기능을 도입하거나 변경하는 코드"를 다운로드·설치·실행해선 안 됨(교육용 코딩 앱 예외). ([App Store Review Guidelines](https://developer.apple.com/app-store/review/guidelines/)) 해당 페이지에 3.3.1이라는 항목은 없었음(조사 요청에서 언급된 3.3.1(B)는 현재 가이드라인에서 확인되지 않음). Apple이 JS 인터프리터 기반 업데이트를 허용하는 별도 조항 존재 여부는 **미확인**.
- 운영 정책 제안: OTA는 버그 수정·문구 한정, 신규 기능·권한 변경은 스토어 릴리스. 단계적 롤아웃 + 롤백 가능 상태 유지.

### 7.6 스토어 심사 정책
| 항목 | iOS | Android |
|---|---|---|
| 계정 삭제 | 계정 생성 지원 앱은 **앱 내 삭제 제공 필수** (5.1.1(v)) ([Apple](https://developer.apple.com/app-store/review/guidelines/)) | 앱 내 경로 + 웹 삭제 링크 필수, 연관 데이터도 삭제, 법적 보존 데이터는 공개 ([Google](https://support.google.com/googleplay/android-developer/answer/13327111)) |
| 소셜 로그인 | 4.8: 주 계정에 서드파티/소셜 로그인 사용 시 동등한 옵션(이름·이메일만 수집, 이메일 비공개 가능, 광고용 상호작용 수집 동의 없이 금지) 제공. 자체 계정 전용 앱 등은 예외 ([Apple](https://developer.apple.com/app-store/review/guidelines/)) | 해당 없음 |
| 개인정보 표시 | App Privacy label + Privacy manifest | Data safety form (+ data deletion 질문) |
| 사진/연락처 데이터 | 5.1.2: Photos/Camera API 데이터를 마케팅·광고·사용기반 마이닝에 사용 금지, 제3자(AI 포함) 공유 시 명시 동의 | Data safety에 공개 |

### 플랫폼별 매핑 (릴리스)
| 항목 | iOS | Android | Flutter | RN | Web |
|---|---|---|---|---|---|
| 빌드 번호 | CFBundleVersion | versionCode | pubspec `+build` | 네이티브 값 | 빌드 해시/semver |
| 단계 배포 | Phased Release | Staged rollout | 네이티브 따름 | 네이티브 따름 | 카나리 |
| OTA | 불가(바이너리) | 불가 | Shorebird | EAS Update | 즉시 배포 |
| 강제 업데이트 | 서버 426 + 스토어 링크 | In-App Updates API(**미확인**) | 서버 426 | 서버 426 | 새로고침 안내 |

### 자주 하는 실수
- 소셜 로그인만 제공하고 Sign in with Apple 누락(4.8).
- 계정 삭제를 "고객센터 문의"로만 처리.
- 단계적 출시 없이 100% 배포 후 서버 호환 문제 발견.
- OTA로 새 기능/권한 변경 배포.
- 서버가 구버전 API를 즉시 제거.

### 체크리스트
- [ ] 계정 삭제: 인앱 + 웹 URL + 데이터 삭제 범위 정의(게시물, 좋아요, 푸시 토큰, 업로드 이미지, 로그)
- [ ] 소셜 로그인 제공 시 Apple 로그인 포함
- [ ] 릴리스 티켓에 단계적 출시 비율/중단 기준(crash-free rate 등) 명시
- [ ] 최소 지원 버전 정책과 Remote Config/서버 헤더 구현
- [ ] OTA 정책 문서화

---

## 8. 개인정보 · 권한

### 핵심 개념
- 사진 앱은 카메라/사진 라이브러리/알림 권한이 핵심. 위치 권한은 필요 시에만.
- 사진 EXIF에는 GPS, 기기 모델, 촬영 시각 등이 포함될 수 있다. **업로드 전 GPS EXIF 제거**를 기본값으로 하고, 위치 기능을 명시적으로 제공할 때만 사용자 동의 하에 별도 필드로 저장하는 것을 권장(설계 권고; 공식 근거는 법령/가이드 확인 필요, **미확인**).

### 공식 가이드라인
- **iOS Privacy manifest**: `PrivacyInfo.xcprivacy`에 수집 데이터, tracking, tracking domains, required reason API 사용 이유를 선언하고 서드파티 SDK도 자체 manifest를 포함. (페이지 본문 미열람, 요약은 일반 지식, **미확인**: [Apple 문서](https://developer.apple.com/documentation/bundleresources/privacy-manifest-files))
- **Apple 5.1.2**: 개인정보 사용/공유 전 허가, 추적은 ATT 필요, 푸시/위치/추적을 기능 이용 조건으로 강제 금지. ([Guidelines](https://developer.apple.com/app-store/review/guidelines/))
- **Android 권한 UX**: 기능 사용 시점에 요청(in context), `shouldShowRequestPermissionRationale()`이 true면 교육용 UI + "no thanks" 버튼, 거부 시 기능 저하로 계속 사용 가능, 반복 설득(nagging) 금지, 2회 거부 시 영구 거부, Android 11+ one-time 권한. ([Android permissions](https://developer.android.com/training/permissions/requesting))
- **Google Play Data safety**: 수집/공유 데이터 신고와 삭제 질문 포함. ([계정 삭제 정책](https://support.google.com/googleplay/android-developer/answer/13327111))
- **GDPR / 개인정보 보호법(PIPA)**: 이번 조사에서 공식 법령 원문 미확인 **(미확인)**. 일반 원칙: 수집 최소화, 목적 고지, 동의/철회, 삭제권, 국외 이전 고지(AI 서비스 사업자 포함 시 처리 위탁/제3자 제공 고지), 만 14세 미만 법정대리인 동의(PIPA). 서비스 출시 전 법무 검토 필수.

### 플랫폼별 매핑
| 항목 | iOS | Android | Flutter | RN | Web |
|---|---|---|---|---|---|
| 선언 | Info.plist `NS*UsageDescription` + PrivacyInfo.xcprivacy | Manifest 권한 + Data safety | 양쪽 + 플러그인 manifest 점검 | 양쪽 | 쿠키 동의, 개인정보처리방침 |
| 사진 접근 | PHPicker(권한 최소화, **미확인**) | Photo Picker(**미확인**) | image_picker 등 | expo-image-picker 등 | `<input type=file>` |
| EXIF GPS 제거 | 재인코딩/메타 제거 | 재인코딩 | 플러그인/서버 | 플러그인/서버 | Canvas 재인코딩/서버 |
| 푸시 권한 | 맥락 후 요청 | Android 13+ POST_NOTIFICATIONS (**미확인**) | 동일 | 동일 | Web Push 권한 |

### 자주 하는 실수
- 앱 시작 시 모든 권한을 한 번에 요청.
- 권한 거부 후 기능 전체 차단.
- 서드파티 SDK의 privacy manifest 누락 -> 심사 경고.
- EXIF 위치를 지우지 않아 공개 게시물에서 집 위치 노출.
- 서버 측에서만 EXIF를 제거해 업로드 구간에 원본이 전송됨 (서버에서도 반드시 재검증/제거).

### 체크리스트
- [ ] 권한별 사용 시점·문구 표 작성 (카메라/사진/알림/위치)
- [ ] 클라 + 서버 이중 EXIF 제거(서버가 최종 책임)
- [ ] privacy manifest / Data safety / 개인정보처리방침 상호 일치 검증
- [ ] 분석·크래시 SDK의 수집 항목 반영
- [ ] 법무 검토(GDPR/PIPA) 완료 전 출시 금지

---

## 9. 찰캌 적용 포인트

1. **인증**: access 짧게 + refresh rotation. 모바일은 Keychain(`AfterFirstUnlockThisDeviceOnly`) / Keystore 기반(신규는 EncryptedSharedPreferences 회피), Web은 httpOnly 쿠키. 소셜 로그인 도입 시 Sign in with Apple 동시 제공(4.8). 서버는 소셜 토큰을 직접 검증.
2. **계정 삭제**: iOS 인앱 필수, Play 인앱+웹 URL 필수. 삭제 대상에 사진, 좋아요, 푸시 토큰, AI 리뷰 결과, 로그 포함하고 보존 항목은 방침에 명시.
3. **AI 리뷰·업로드**: AI API 키는 서버 전용. 업로드는 presigned URL + 서버측 EXIF 제거. 제3자 AI로 사진 전송 시 5.1.2 동의 UX 필요.
4. **네트워크**: TLS only, cleartext 예외는 debug 한정, pinning은 초기 미도입(도입 시 backup pin + 강제 업데이트 선행).
5. **난독화/심볼**: Android R8 켜고 release 스모크 테스트, Flutter `--obfuscate --split-debug-info`, RN은 source map 자동 업로드, Web은 비공개 source map 업로드. 모든 심볼을 CI 아티팩트로 보관.
6. **관측성**: 5 플랫폼 공통 도구로 Sentry를 우선 검토(Crashlytics 공식 개요에 RN/Web 언급 없음). 이벤트 사전·PII 마스킹 규칙 공유.
7. **릴리스**: Play staged rollout + Apple phased release 기본 사용, 서버 주도 최소 버전(426) + Remote Config 킬 스위치. OTA는 버그 수정 한정.
8. **무결성**: App Check(App Attest / Play Integrity / reCAPTCHA)는 모니터링 -> enforcement 순으로 도입. 보조 신호로만 사용.
9. **프라이버시 문서 3종 동기화**: privacy manifest, Data safety, 개인정보처리방침을 단일 데이터 인벤토리에서 파생.

---

## 10. 출처 목록

공식 문서 (직접 열람)
- OWASP MASVS: https://mas.owasp.org/MASVS/
- OWASP Top 10 (2025로 리다이렉트): https://top10.owasp.org/
- Android Network security configuration: https://developer.android.com/privacy-and-security/security-config
- Android Cryptography (security-crypto deprecated): https://developer.android.com/privacy-and-security/cryptography
- Android R8 / shrink code: https://developer.android.com/build/shrink-code
- Android runtime permissions: https://developer.android.com/training/permissions/requesting
- Apple App Store Review Guidelines: https://developer.apple.com/app-store/review/guidelines/
- App Store Connect Phased release: https://developer.apple.com/help/app-store-connect/update-your-app/release-a-version-update-in-phases/
- Google Play staged rollout: https://support.google.com/googleplay/android-developer/answer/6346149
- Google Play account deletion: https://support.google.com/googleplay/android-developer/answer/13327111
- Flutter obfuscation: https://docs.flutter.dev/deployment/obfuscate
- flutter_secure_storage: https://pub.dev/packages/flutter_secure_storage
- React Native Hermes: https://reactnative.dev/docs/hermes
- Expo SecureStore: https://docs.expo.dev/versions/latest/sdk/securestore/
- EAS Update: https://docs.expo.dev/eas-update/introduction/
- Shorebird: https://docs.shorebird.dev/
- Firebase App Check: https://firebase.google.com/docs/app-check
- Firebase Crashlytics: https://firebase.google.com/docs/crashlytics
- Firebase Remote Config: https://firebase.google.com/docs/remote-config
- Sentry React Native source maps: https://docs.sentry.io/platforms/react-native/sourcemaps/
- Sentry JavaScript source maps: https://docs.sentry.io/platforms/javascript/sourcemaps/
- Sentry Flutter: https://docs.sentry.io/platforms/flutter/

링크했으나 본문을 확인하지 못한 문서 (재확인 필요)
- Apple Keychain accessibility: https://developer.apple.com/documentation/security/restricting-keychain-item-accessibility
- Apple Privacy manifest files: https://developer.apple.com/documentation/bundleresources/privacy-manifest-files
- OWASP Top 10:2025 목록: https://top10.owasp.org/2025/en/

미조사 항목 (후속 조사 필요)
- ATS 공식 문서, Tink/DataStore 패턴, mapping.txt Play Console 업로드 절차, bitcode 제거 시점, Crashlytics RN/Web 지원, Sentry PII 스크러빙, Apple의 OTA 허용 조항 해석, Play In-App Updates API, GDPR/개인정보 보호법 원문, EXIF GPS 제거 구현 가이드.
