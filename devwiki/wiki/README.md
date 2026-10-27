# wiki — 찰캌 5개 플랫폼 개발 기준

raw 리서치 9개를 주제별로 중복 없이 다시 묶은 문서다. 한 개념은 한 문서에만 적고, 다른 문서는 링크만 건다.

## 먼저 읽을 것

1. [00-principles.md](00-principles.md) — 기능 동등성 원칙, 같아야 하는 것과 달라도 되는 것, 5개 플랫폼 공통 어휘
2. [chalkak/feature-map.md](chalkak/feature-map.md) — 찰캌 기능, 규칙 ID, API, 기존 iOS·Android 화면 대응표
3. 작업할 플랫폼 문서 하나 ([platforms/](#플랫폼별-문서))
4. 작업 주제에 맞는 공통 문서 ([common/](#공통-문서))
5. PR 전에 [checklists/definition-of-done.md](checklists/definition-of-done.md)

## 공통 문서

| 문서 | 다루는 질문 |
| --- | --- |
| [common/architecture-state.md](common/architecture-state.md) | 계층을 어떻게 나누나, UI 상태와 일회성 이벤트는 어떻게 모델링하나, 컴포넌트는 어떻게 쪼개나, 콜백과 async는 플랫폼별로 어떻게 대응되나 |
| [common/networking-errors-retry.md](common/networking-errors-retry.md) | 예외를 어떻게 분류하고 사용자에게 보여 주나, 무엇을 몇 번 재시도하나, 타임아웃·취소·토큰 갱신은 어떻게 하나 |
| [common/offline-optimistic-polling.md](common/offline-optimistic-polling.md) | 오프라인에서 무엇을 보여 주나, UI를 먼저 바꾸고 백그라운드로 통신하려면, AI 리뷰 같은 긴 작업은 어떻게 기다리나 |
| [common/media-upload.md](common/media-upload.md) | 사진을 권한 없이 고르고, WebP로 줄이고, 앱이 백그라운드로 가도 업로드를 끝내려면 |
| [common/push-deeplink-navigation.md](common/push-deeplink-navigation.md) | 푸시 권한·토큰·탭 처리, 딥링크, 뒤로 가기를 다섯 플랫폼에서 같은 결과로 만들려면 |
| [common/security-privacy-release.md](common/security-privacy-release.md) | 토큰은 어디에 두나, 난독화는 무엇을 막지 못하나, 심볼 파일, 스토어 심사, 계정 삭제, 단계적 출시, 크래시 리포팅 |
| [common/ux-accessibility.md](common/ux-accessibility.md) | HIG와 Material의 차이, 로딩·빈·오류·오프라인 상태, 피드백과 되돌리기, 접근성, 안전 영역, 시간대 |
| [common/testing-parity.md](common/testing-parity.md) | 테스트하기 쉬운 구조, Fake 우선, 플랫폼별 테스트 도구, 다섯 클라이언트의 기능 동등성 검증 |

## 플랫폼별 문서

공통 문서에 없는 플랫폼 고유 API, 설정 함정, 권장 스택만 담았다.

| 문서 | 권장 스택 요약 |
| --- | --- |
| [platforms/ios.md](platforms/ios.md) | SwiftUI + Observation(`@Observable`), Swift 6 concurrency, Keychain, Swift Testing |
| [platforms/android.md](platforms/android.md) | 기존 앱 기준: Compose + ViewModel/StateFlow, 수동 DI, Retrofit + kotlinx.serialization, DataStore, Coil, ktlint·Konsist, R8 |
| [platforms/flutter.md](platforms/flutter.md) | 공식 아키텍처 가이드(View/ViewModel/Repository/Service), `Result`/Command, Dio, go_router, firebase_messaging |
| [platforms/react-native.md](platforms/react-native.md) | Expo SDK 56 + Expo Router + TypeScript strict, TanStack Query, expo-image, FCM 기기 토큰, EAS |
| [platforms/web.md](platforms/web.md) | React SPA + PWA(Workbox), TanStack Query, Playwright. CORS·토큰 보관·웹 푸시는 서버 합의 먼저 |

## 기타

- [checklists/definition-of-done.md](checklists/definition-of-done.md) — 플랫폼 공통 완료 기준
- [open-questions.md](open-questions.md) — 백엔드 확인 대상, 팀 결정 대상, 재검증이 필요한 리서치 항목
