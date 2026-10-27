# raw — 리서치 원본

2026-10-27에 주제별 리서치 에이전트 9개가 공식 문서와 신뢰할 수 있는 자료를 조사해 저장한 원본이다. 각 파일은 출처 URL과 검증 표기를 포함한다. 정리된 결론은 [../wiki/README.md](../wiki/README.md)에 있다.

| 파일 | 주제 | 줄 수 | 직접 열람한 공식 문서 | 주의 |
| --- | --- | --- | --- | --- |
| [ios-native.md](ios-native.md) | Swift 6, SwiftUI, Observation, 백그라운드 업로드, APNs, Universal Links, 심사 | 511 | Observation 마이그레이션, background URLSession, APNs 등록, Associated Domains, Swift Testing, 계정 삭제, App Review Guidelines, ATT, WWDC25-227 | Keychain·PhotosPicker·44pt 본문은 열람 실패(`[일반지식]`) |
| [android-native.md](android-native.md) | Kotlin, Compose, 코루틴, WorkManager, Photo Picker, FCM, edge-to-edge, R8, Keystore | 553 | 인용 URL 전부 열람 | Retrofit/Gson R8 규칙, StrictMode, m3.material.io 세부는 미검증 |
| [flutter.md](flutter.md) | 아키텍처 가이드, Result/Command, 상태관리, 성능, FCM, 권한, 난독화, flavor | 507 | docs.flutter.dev, dart.dev, pub.dev 약 25개 | Riverpod, iOS flavor 가이드 수집 실패 |
| [react-native.md](react-native.md) | Expo, New Architecture, Hermes, 리스트 성능, TanStack Query, 푸시, EAS Update | 501 | reactnative.dev, docs.expo.dev, Expo SDK 56 변경 로그 | Zustand/RTK, FlashList 세부 API 미검증 |
| [web-pwa.md](web-pwa.md) | PWA, Service Worker, Workbox, Web Push, iOS Safari 제약, 이미지 업로드, CWV, 보안 | 399 | WebKit Web Push, web.dev storage, MDN Background Sync, web.dev INP | 나머지 약 65개 URL은 정식 URL이지만 본문 미열람 |
| [network-resilience-offline.md](network-resilience-offline.md) | 에러 분류, 재시도, 타임아웃, 토큰 갱신, 오프라인 우선, 낙관적 UI, 업로드, 폴링 | 534 | Android offline-first, Flutter offline-first·optimistic, AWS backoff, TanStack, useOptimistic, Idempotency-Key 초안 | 찰캌 API와 다른 일반 제안이 있다. wiki의 찰캌 기준을 따른다. |
| [architecture-testing.md](architecture-testing.md) | 계층 구조, 공통 어휘, DI·Fake, UI 상태 모델, 테스트 피라미드, 패리티 검증 | 482 | Android 아키텍처·테스트, Flutter 가이드·테스트, Maestro 지원 플랫폼 | Apple 문서 본문 열람 실패 |
| [security-obfuscation-release.md](security-obfuscation-release.md) | MASVS, 토큰 저장, 네트워크 보안, 난독화·심볼, 비밀, 크래시 리포팅, 릴리스, 프라이버시 | 371 | Android NSC·암호화·R8, App Review Guidelines, 단계적 출시, Flutter obfuscate, Expo SecureStore·EAS Update, App Check, Sentry | GDPR/개인정보보호법 원문 미확인 |
| [ios-android-compat-ux.md](ios-android-compat-ux.md) | iOS↔Android 동작 차이 11개 영역, HIG vs M3, 로딩·빈·오류 상태 | 261 | POST_NOTIFICATIONS, edge-to-edge, FCM 메시지 유형 | 나머지는 `[공식·미열람]` |

## 리서치 원본에서 바로잡은 점

리서치 에이전트는 찰캌 코드를 보지 않고 일반론으로 썼다. 아래는 wiki에서 찰캌 사실로 바로잡았다.

- **사인은 토글 기능이 아니다.** 회원이 등록하는 손글씨 서명 이미지(PNG, 최대 1 MiB)다. raw 일부가 "좋아요/사인 토글"로 쓴 부분은 좋아요에만 해당한다. ([sign.md](../../docs/business-rules/rules/sign.md))
- **좋아요 API는 이미 멱등이다.** `PUT /api/v1/posts/{postId}/likes`(등록)와 `DELETE ...`(취소)이고, 중복 등록과 없는 좋아요 취소 모두 성공 처리한다. (LIKE-003)
- **AI 리뷰 폴링은 지수 백오프가 아니라 3초 고정 간격, 약 5분 창이다.** ([ai-review 연동 안내](../../docs/business-rules/guides/ai-review.md))
- **게시물 업로드 입력은 WebP, 최대 5 MiB다.** 업로드 URL은 5분, 업로드 ID는 1시간 유효하다. (POST-004)

## 링크 검증

이 컨테이너의 네트워크 정책 때문에 대부분의 문서 도메인이 차단되어 전체 링크를 기계적으로 다시 확인하지 못했다. 접근 가능했던 105개 URL은 200을 반환했다. `developer.apple.com/design/human-interface-guidelines/refresh-content-controls`는 404였으므로 HIG의 다른 경로로 바꿔야 한다.
