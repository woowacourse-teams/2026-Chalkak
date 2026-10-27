---
title: iOS 네이티브 앱 개발 (Swift 6 / SwiftUI) 리서치 원자료
topic: 찰캌(Chalkak) iOS 네이티브 재구현을 위한 iOS 플랫폼 지식
collected: 2026-10-27
language: ko (API 이름·용어는 영어 유지)
status: raw (다른 토픽 파일과 중복 제거 예정)
verification_legend: |
  [확인] = 이번 조사에서 해당 URL을 직접 fetch 하여 본문 확인.
  [검색] = 검색 결과(제목/URL)로만 확인, 본문 미확인. 세부 내용은 공식 문서 재확인 필요.
  [일반지식] = fetch 본문이 비어 있거나 확인 못한 부분. 작성자의 일반 지식이며 출처 URL은 "참고용 관련 페이지"임.
sources:
  - https://developer.apple.com/documentation/swiftui/migrating-from-the-observable-object-protocol-to-the-observable-macro.md
  - https://developer.apple.com/documentation/foundation/downloading-files-in-the-background.md
  - https://developer.apple.com/tutorials/data/documentation/bundleresources/describing-use-of-required-reason-api.md
  - https://developer.apple.com/documentation/usernotifications/registering-your-app-with-apns.md
  - https://developer.apple.com/documentation/xcode/supporting-associated-domains.md
  - https://developer.apple.com/documentation/testing/migratingfromxctest.md
  - https://developer.apple.com/support/offering-account-deletion-in-your-app
  - https://developer.apple.com/app-store/review/guidelines/
  - https://developer.apple.com/app-store/user-privacy-and-data-use/
  - https://developer.apple.com/videos/play/wwdc2025/227/
  - https://developer.apple.com/design/human-interface-guidelines/voiceover.md
  - https://developer.apple.com/help/app-store-connect/manage-app-accessibility/larger-text-evaluation-criteria/
  - https://mackuba.eu/notes/wwdc20/handle-the-limited-photos-library/
  - https://mackuba.eu/2020/07/07/photo-library-changes-ios-14/
  - https://jaredsinclair.com/2025/09/10/observation.html
  - https://github.com/swiftlang/swift-migration-guide/blob/main/Guide.docc/MigrationGuide.md
  - https://www.swift.org/migration/documentation/swift-6-concurrency-migration-guide/dataracesafety/
  - (그 외 검색 결과로만 확인한 URL은 각 섹션 및 "출처 목록" 참고)
---

# iOS 네이티브 앱 개발 (Swift 6 / SwiftUI) - 찰캌 재구현용 원자료

> 조사 한계: developer.apple.com 일부 페이지(HIG 일부, Keychain, ATT Info.plist, PhotosPicker API, swift.org 마이그레이션 가이드 본문)는 fetch 시 본문이 비어 있었다. 해당 부분은 `[일반지식]`으로 표시했고, 확정 전 공식 문서 재확인이 필요하다. 또한 오늘(2026-10-27) 기준 최신 OS 버전 번호(iOS 26 계열 등)는 WWDC25 세션에서 확인된 범위만 기술했다.

---

## 1. 앱 아키텍처 & SwiftUI 데이터 흐름 (@Observable, 상태 소유권)

### 핵심 개념
- iOS 17+ 부터 SwiftUI는 Swift `Observation`을 지원한다. `ObservableObject` + `@Published` 대신 `@Observable` 매크로를 쓴다. ([출처](https://developer.apple.com/documentation/swiftui/migrating-from-the-observable-object-protocol-to-the-observable-macro.md)) [확인]
- 장점(공식): optional/컬렉션 내부 객체 추적 가능, `State()`/`Environment` 같은 기존 데이터 흐름 프리미티브 사용, **뷰의 `body`가 읽은 프로퍼티가 바뀔 때만** 뷰 갱신(성능 이점). `ObservableObject`는 어떤 `@Published`가 바뀌어도 갱신. ([출처](https://developer.apple.com/documentation/swiftui/migrating-from-the-observable-object-protocol-to-the-observable-macro.md)) [확인]
- 프로퍼티 래퍼 대응표(공식 문서 예시 기준) [확인]:
  | 이전 (ObservableObject) | 이후 (Observation) |
  |---|---|
  | `@StateObject` | `@State` (뷰가 모델을 소유) |
  | `@EnvironmentObject` / `.environmentObject(_)` | `@Environment(Model.self)` / `.environment(_)` |
  | `@ObservedObject` | 일반 프로퍼티(읽기만) 또는 `@Bindable`(바인딩 필요 시) |
  | `@Published` | 불필요 (추적 원치 않으면 `@ObservationIgnored`) |
- 소유권 원칙: 모델을 **생성·소유**하는 뷰(앱 루트 `App` 등)만 `@State private var model = Model()`; 하위 뷰는 파라미터로 받거나 `@Environment`로 읽는다. 하위에서 `$model.field` 바인딩이 필요하면 `@Bindable`. ([출처](https://developer.apple.com/documentation/swiftui/migrating-from-the-observable-object-protocol-to-the-observable-macro.md)) [확인]
- 점진적 마이그레이션 가능: `Observable`과 `ObservableObject` 모델이 한 앱에 공존 가능하나 추적 방식이 달라 미세한 동작 차이가 있다. [확인]

### 공식 가이드라인(권장 방식)
- 새 코드는 `@Observable` + `@State`/`@Environment`/`@Bindable` 조합. 뷰는 `body`에서 필요한 프로퍼티만 읽는다.
- Apple 권장 구조는 "SwiftUI 뷰 = 상태의 함수, 모델 = 단일 진실 원천". 별도의 계층(Clean Architecture 등)은 다른 토픽에서 다루므로 여기서는 iOS 고유 API만 정리. (아키텍처 세부는 Apple 샘플 "Food Truck" 등이 참조되나 이번엔 미확인)
- Apple Docs의 Observation 프레임워크 개요: https://developer.apple.com/documentation/observation.md [검색]

### 자주 하는 실수·놓치기 쉬운 점
- `@State`는 `@StateObject`처럼 초기화 클로저를 lazy 평가하지 않아, 부모가 다시 계산될 때 모델 `init`이 반복 호출되어 모델이 재생성되는 이슈가 지적된다. 무거운 init(네트워크 호출, 구독 설정)을 모델 생성자에 넣지 말 것. ([출처](https://jaredsinclair.com/2025/09/10/observation.html)) [확인, 2차 출처]
- Observation으로 UI 밖(모델-모델) 관찰을 하려면 `withObservationTracking`(1회성 콜백, 재등록 필요)이 필요하고 취소 토큰이 없다. 연속 관찰이 필요하면 설계를 바꾸거나 AsyncStream/Combine을 고려. OS 26의 `Observations`(async sequence)는 하위 OS에 백포트되지 않는다. ([출처](https://jaredsinclair.com/2025/09/10/observation.html)) [확인, 2차 출처]
- `@Observable` 클래스에 `@MainActor`를 붙일지, 네트워크 계층은 별도 actor/Sendable 서비스로 분리할지 명확히 정하지 않으면 isolation 오류가 퍼진다(2절 참고).
- `@ObservationIgnored`가 필요한 프로퍼티(캐시, 의존성 주입된 서비스, Task 핸들)를 추적 대상에 두면 불필요한 갱신이 발생.

### 체크리스트
- [ ] 모델 소유 뷰는 `@State`, 주입은 `@Environment`, 편집은 `@Bindable`
- [ ] 모델 init에 부수효과 없음 (뷰 재계산 시 재생성돼도 안전)
- [ ] 추적 불필요 프로퍼티 `@ObservationIgnored`
- [ ] 최소 배포 타깃 iOS 17+ 확인 (Observation 요구)

---

## 2. Swift Concurrency (async/await, actor, MainActor, Sendable, 취소, strict concurrency)

### 핵심 개념
- Swift 6 언어 모드는 컴파일 타임에 data race를 진단한다. 언어 모드는 **타깃 단위 opt-in**이며 컴파일러 버전과 별개다. 마이그레이션 가이드 주제: Migration Strategy, Data Race Safety, Incremental Adoption, Common Problems 등. ([출처](https://github.com/swiftlang/swift-migration-guide/blob/main/Guide.docc/MigrationGuide.md)) [확인(목차 수준)]
- 가이드 본문 페이지: Data Race Safety / Enable data-race safety checking. ([출처](https://www.swift.org/migration/documentation/swift-6-concurrency-migration-guide/dataracesafety/), [출처](https://www.swift.org/migration/documentation/swift-6-concurrency-migration-guide/enabledataracesafety/)) [검색; 본문 fetch 실패]
- 핵심 개념(일반지식, 위 가이드에서 다루는 영역): isolation domain(actor/global actor), `Sendable`(경계를 넘는 값은 Sendable), `@MainActor`(UI 모델), 구조적 동시성(`async let`, `TaskGroup`), 비구조적 `Task {}` / `Task.detached`. [일반지식]
- Swift Testing 쪽 공식 문서도 "sync XCTest는 main actor, Swift Testing 테스트는 임의 태스크에서 실행"이라 명시 — isolation은 테스트에도 영향. ([출처](https://developer.apple.com/documentation/testing/migratingfromxctest.md)) [확인]

### 공식 가이드라인(권장 방식)
- 점진 도입: 먼저 strict concurrency를 `minimal → targeted → complete`로 올리며 경고를 해결한 뒤 Swift 6 모드 전환(가이드의 Incremental Adoption 주제). ([출처](https://github.com/swiftlang/swift-migration-guide/blob/main/Guide.docc/MigrationGuide.md)) [목차 확인, 세부는 일반지식]
- UI에 닿는 타입(`@Observable` view model)은 `@MainActor`; 값 타입 DTO는 `Sendable` struct로 설계하면 경계 통과가 쉽다. [일반지식]
- 콜백 API는 `withCheckedContinuation`/`withCheckedThrowingContinuation`으로 async 변환 (Apple 테스트 문서도 동일 권장). ([출처](https://developer.apple.com/documentation/testing/migratingfromxctest.md)) [확인]
- WWDC25 "Code-along: Elevate an app with Swift concurrency" 세션이 PhotosPicker + 동시성 예제를 다룸. ([출처](https://developer.apple.com/videos/play/wwdc2025/270/)) [검색]

### 자주 하는 실수·놓치기 쉬운 점
- Task 취소는 **협력적(cooperative)**: `Task.checkCancellation()`/`Task.isCancelled`를 확인하지 않으면 계속 실행된다. SwiftUI `.task {}`는 뷰가 사라지면 자동 취소되지만, `Task {}`로 만든 비구조적 태스크는 직접 취소해야 한다. [일반지식]
- `Task {}` 안에서 `self`를 강하게 캡처해 모델 수명이 늘어나는 retain 문제(8절). Observation 연속 관찰을 Task로 감쌀 때 특히 주의. ([출처](https://jaredsinclair.com/2025/09/10/observation.html)) [확인, 2차]
- `deinit`은 main-actor 격리가 아니므로 `@MainActor` 클래스의 가변 상태를 `deinit`에서 만지면 isolation 오류. ([출처](https://jaredsinclair.com/2025/09/10/observation.html)) [확인, 2차]
- `@unchecked Sendable`·`nonisolated(unsafe)`로 경고를 덮는 행위는 data race를 숨길 뿐. [일반지식]
- actor reentrancy: `await` 지점마다 actor 상태가 바뀔 수 있어, await 전후로 불변식을 가정하면 안 된다(예: 업로드 중복 시작 체크). [일반지식]
- `DispatchQueue.main.async` 혼용: 레거시 delegate 콜백(예: background URLSession)은 임의 큐에서 호출되므로 main으로 hop 필요. Apple 샘플도 `urlSessionDidFinishEvents`에서 main queue로 completion handler를 호출. ([출처](https://developer.apple.com/documentation/foundation/downloading-files-in-the-background.md)) [확인]

### 체크리스트
- [ ] 타깃별 strict concurrency 단계/Swift 6 모드 정책 결정 (신규 프로젝트는 처음부터 Swift 6 권장 [일반지식])
- [ ] View model `@MainActor`, DTO `Sendable`, 네트워크/업로드 매니저는 actor
- [ ] 모든 장기 작업에 취소 지점 + `.task`로 수명 연동
- [ ] `@unchecked Sendable` 사용처에 사유 주석

---

## 3. 앱 라이프사이클 & scenePhase

### 핵심 개념
- SwiftUI `@Environment(\.scenePhase)`: `.active`, `.inactive`, `.background`. `App`의 scene body 또는 뷰에서 읽는다. [일반지식] (Apple Forums에 scenePhase 동작 이슈 스레드 다수 존재: https://developer.apple.com/forums/thread/723352, https://developer.apple.com/forums/thread/778599 [검색])
- UIKit 훅이 여전히 필요한 경우: APNs 토큰 수신, `handleEventsForBackgroundURLSession`. SwiftUI에서는 `@UIApplicationDelegateAdaptor`로 AppDelegate 연결. ([출처](https://developer.apple.com/documentation/foundation/downloading-files-in-the-background.md), [출처](https://developer.apple.com/documentation/usernotifications/registering-your-app-with-apns.md)) [확인(필요성), 어댑터 이름은 일반지식]
- 앱은 foreground / suspended / terminated 상태를 오가며, 백그라운드에서는 기본적으로 suspend. ([출처](https://developer.apple.com/videos/play/wwdc2025/227/)) [확인]

### 공식 가이드라인(권장 방식)
- `.background` 진입 시: 진행 중 작업 상태 저장(드래프트 글/업로드 큐), 민감 화면 가림(스냅샷 노출 방지). `.active` 복귀 시: 데이터 새로고침, 알림 권한 상태 재확인, 업로드 큐 재개. [일반지식]
- 짧은 마무리 작업은 `beginBackgroundTask`/`endBackgroundTask`(+ expiration 핸들러). ([출처](https://developer.apple.com/videos/play/wwdc2025/227/)) [확인]

### 자주 하는 실수·놓치기 쉬운 점
- `scenePhase`는 어디서 읽느냐(App vs View)에 따라 의미(전체 scene vs 해당 scene)가 달라지고, 시스템 알림/시트 등으로 `.inactive`가 잦다. `.inactive`에서 무거운 저장/해제를 하지 말 것. [일반지식, 포럼 스레드 [검색]]
- 앱 종료(terminate) 콜백에 의존한 저장은 불가 — 백그라운드 진입 시 저장.
- 앱을 앱 스위처에서 강제 종료하면 background push가 전달되지 않는다. ([출처](https://developer.apple.com/videos/play/wwdc2025/227/)) [확인]

### 체크리스트
- [ ] scenePhase 변화별 동작(저장/재개/새로고침) 표로 정의
- [ ] AppDelegate 어댑터로 APNs/background session 훅 연결
- [ ] 복귀 시 권한·토큰·세션 상태 재검증

---

## 4. 백그라운드 작업 & 업로드 (BGTaskScheduler, background URLSession)

### 핵심 개념
- 백그라운드 실행은 **기회적(opportunistic)·비보장**. 에너지가 핵심 제약. 작업은 짧고 원자적이며 중단/재개 가능하게 설계. ([출처](https://developer.apple.com/videos/play/wwdc2025/227/)) [확인]
- 종류 [확인, WWDC25-227]:
  - `BGAppRefreshTask`: 사용 직전 콘텐츠 사일런트 갱신. 앱 사용 패턴에 따라 스케줄. SwiftUI: scene에 `.backgroundTask(.appRefresh("id")) { ... }`.
  - `BGProcessingTask`: 무거운 작업(ML, DB 정리). `requiresNetworkConnectivity`, `requiresExternalPower` 등 제약 지정, 시작 시 handler 등록.
  - `BGContinuedProcessingTask` (iOS/iPadOS 26 신규): **사용자가 포그라운드에서 시작한** 작업(export, publish 등)을 백그라운드에서 이어서 수행. 시스템이 진행 UI 표시, 사용자가 취소 가능. 자동 유지보수·백업·**사진 동기화 용도 금지**. 진행률 보고 필수(미보고 시 만료), `expirationHandler`와 `setTaskCompleted(success:)` 필요. Info.plist에 허용 식별자(번들 ID prefix) 등록.
  - 짧은 마무리: `beginBackgroundTask`/`endBackgroundTask`.
- background `URLSession` (업로드/다운로드) [확인, ([출처](https://developer.apple.com/documentation/foundation/downloading-files-in-the-background.md))]:
  - `URLSessionConfiguration.background(withIdentifier:)`; 식별자는 앱 내 고유면 충분(보통 1개 고정 문자열).
  - `sessionSendsLaunchEvents = true`(기본), 비긴급 작업은 `isDiscretionary = true`.
  - 전송은 **별도 프로세스**가 수행 → 제약: **delegate 필수**, HTTP/HTTPS만, 리다이렉트 항상 자동 추종(delegate 미호출), **업로드는 파일 기반(`uploadTask(with:fromFile:)`)만 지원**(Data/Stream 업로드는 앱 종료 후 실패).
  - 앱이 suspend 후 완료되면 `application(_:handleEventsForBackgroundURLSession:completionHandler:)` 호출 → completion handler 저장 → `urlSessionDidFinishEvents(forBackgroundURLSession:)`에서 **main queue**로 실행.
  - 앱이 종료되었다 relaunch되면 **같은 identifier로 세션을 재생성**해야 task가 재연결된다.
  - `countOfBytesClientExpectsToSend/Receive`, `earliestBeginDate`로 스케줄링 힌트 제공.
  - 레이트 리미터: 백그라운드에서 재시작될 때마다 새 task 시작 지연이 증가. 세션 수는 적게(이상적으로 1개), 한 번에 여러 task 시작. 사용자가 앱을 포그라운드로 가져오면 지연 리셋.
  - 백그라운드 모드를 선언한 앱은 기본 세션/데이터 태스크도 사용 가능(모든 백그라운드 네트워킹이 background session일 필요는 없음).
- 업로드 관련 Apple 문서 "Uploading asset resources in the background"(PhotoKit)가 WWDC25 세션에서 연결 자료로 언급됨. ([출처](https://developer.apple.com/videos/play/wwdc2025/227/)) [확인(언급만)]

### 공식 가이드라인(권장 방식)
- 가장 덜 요구하는 API를 선택: 갱신=refresh, 무거운 작업=processing, 사용자 시작 작업=continued processing. 저전력 모드/Background App Refresh/Low Data Mode 존중. 충전 중으로 비필수 작업 미룸. ([출처](https://developer.apple.com/videos/play/wwdc2025/227/)) [확인]
- 사용자가 "게시" 버튼을 눌러 시작한 사진 업로드는 (a) background URLSession의 파일 업로드, 또는 (b) iOS 26+에서 `BGContinuedProcessingTask` 후보. 단 후자는 foreground 시작 + 진행률 필수라는 제약 확인 필요.

### 자주 하는 실수·놓치기 쉬운 점
- 업로드를 `Data`/stream으로 만들어 background session에 넘김 → 앱 종료 시 실패. **임시 파일로 먼저 저장** 후 `fromFile:`. (multipart 본문도 파일로 합성해야 함; 참고 블로그: https://aplus.rs/2024/background-multipart-file-upload-from-ios-app [검색])
- 업로드 완료 때마다 새 background task를 시작 → 레이트 리미터로 점점 지연. ([출처](https://developer.apple.com/documentation/foundation/downloading-files-in-the-background.md)) [확인]
- 세션 재생성 누락 → relaunch 후 delegate 콜백 유실. [확인]
- completion handler를 호출하지 않으면 앱이 계속 깨어 있다고 간주(시스템 페널티). handler를 저장하고 반드시 main에서 호출. [확인 + 일반지식]
- BGTask 식별자를 Info.plist `BGTaskSchedulerPermittedIdentifiers`에 등록하지 않으면 submit 실패. [일반지식]
- BGTask는 시뮬레이터에서 자동 실행되지 않음 — Xcode 디버거 `_simulateLaunchForTaskWithIdentifier` 사용. [일반지식]
- 개발 시 Background Networking Profile 설치로 스케줄링 가시성 확보. ([출처](https://developer.apple.com/documentation/foundation/downloading-files-in-the-background.md)) [확인]

### 체크리스트
- [ ] 업로드 설계: presigned URL 등 서버 방식과 무관하게 클라이언트는 "임시 파일 + background session + 단일 세션"
- [ ] `handleEventsForBackgroundURLSession` 구현 + `urlSessionDidFinishEvents`
- [ ] 앱 시작 시 background session 재생성 (relaunch 대비)
- [ ] 업로드 상태(대기/진행/완료/실패)를 로컬에 영속화, 재시도 정책은 네트워크 토픽 문서 참조
- [ ] Info.plist: Background Modes / BGTaskSchedulerPermittedIdentifiers

---

## 5. URLSession 네트워킹 (iOS 고유 사항)

### 핵심 개념 / 권장
- 현대 코드는 `URLSession.shared.data(for:)` async API 사용; 재시도/에러 모델/클린 아키텍처는 다른 토픽에서 다룸. (iOS 고유 포인트만 정리)
- `URLSessionConfiguration`: `waitsForConnectivity`, `allowsConstrainedNetworkAccess`(Low Data Mode), `allowsExpensiveNetworkAccess`, `timeoutIntervalForRequest/Resource`. 대용량 업로드는 background session(4절). [일반지식; 4절 확인 부분은 별도 출처]
- ATS(App Transport Security)가 기본 HTTPS 강제. 예외 설정은 App Review에서 근거를 요구할 수 있음. [일반지식]
- 이미지 업로드 전 클라이언트 리사이즈/압축(HEIC→JPEG 등)은 서버 정책 문서와 대조. 업로드 크기 힌트는 `countOfBytesClientExpectsToSend`. ([출처](https://developer.apple.com/documentation/foundation/downloading-files-in-the-background.md)) [확인]

### 자주 하는 실수
- 리다이렉트가 background session에서는 delegate로 안 온다는 점 (4절) 놓침. [확인]
- `URLSession` 인스턴스를 요청마다 생성(delegate 보유 시 강한 참조로 누수 — `finishTasksAndInvalidate()` 필요). [일반지식]
- 인증 토큰 갱신(401 처리) 경합: 여러 요청이 동시에 refresh 시도 → actor로 단일화. [일반지식]

### 체크리스트
- [ ] 공용 `URLSession` 1개 + 업로드용 background `URLSession` 1개
- [ ] Low Data Mode / 셀룰러 정책 결정
- [ ] 토큰 갱신 actor

---

## 6. Keychain

### 핵심 개념
- 토큰·시크릿은 `UserDefaults`가 아닌 Keychain(`SecItemAdd/SecItemCopyMatching/SecItemUpdate/SecItemDelete`)에 저장. 공식 문서: Adding a password to the keychain, Restricting keychain item accessibility. ([출처](https://developer.apple.com/documentation/security/adding-a-password-to-the-keychain), [출처](https://developer.apple.com/documentation/security/restricting-keychain-item-accessibility)) [검색; 본문 fetch는 비어 있음]
- `kSecAttrAccessible` 상수 [일반지식]: `WhenUnlocked`, `AfterFirstUnlock`(백그라운드 작업에서 접근 필요 시), `*ThisDeviceOnly`(백업/이전 제외), `WhenPasscodeSetThisDeviceOnly`(패스코드 설정 필수). `kSecAttrAccessibleAlways`는 deprecated.

### 권장
- 백그라운드 업로드/푸시 처리에서 토큰이 필요하면 `AfterFirstUnlock(ThisDeviceOnly)` 계열 — `WhenUnlocked`는 잠금 상태 백그라운드 작업에서 읽기 실패. [일반지식]
- APNs **디바이스 토큰은 캐시 금지**(매 실행 시 재요청), 반면 인증 토큰(access/refresh)은 Keychain. ([출처](https://developer.apple.com/documentation/usernotifications/registering-your-app-with-apns.md)) [확인(디바이스 토큰 부분)]

### 실수
- 앱 삭제 후 재설치 시 Keychain 항목이 남아 있을 수 있음(이전 로그인 상태가 부활) → 첫 실행 플래그로 정리. [일반지식]
- `SecItemAdd` 중복 에러(`errSecDuplicateItem`) 처리 누락 → update로 폴백. [일반지식]
- Keychain 호출을 main thread에서 반복 수행(느림) / `@MainActor` isolation에서 동기 호출. [일반지식]
- 시뮬레이터와 기기의 Keychain 동작 차이. [일반지식]

### 체크리스트
- [ ] 토큰 저장소 wrapper(actor 또는 Sendable) 1개
- [ ] 로그아웃/계정삭제 시 Keychain 정리
- [ ] 접근성 속성 결정(백그라운드 접근 여부)

---

## 7. 사진: PhotosPicker / 카메라 / 사진 라이브러리 권한

### 핵심 개념
- **PHPicker / SwiftUI `PhotosPicker`**: 별도 프로세스에서 실행되고 사용자가 고른 항목만 앱에 전달 → **라이브러리 권한 불필요**. `UIImagePickerController`를 대체하며 다중 선택·검색 지원. ([출처](https://mackuba.eu/2020/07/07/photo-library-changes-ios-14/), [출처](https://mackuba.eu/notes/wwdc20/handle-the-limited-photos-library/)) [확인, 2차 출처]
- SwiftUI에서 `PhotosPickerItem.loadTransferable(type:)`로 `Data` 등 `Transferable`을 비동기 로드(`async throws`, 결과 optional). ([출처](https://developer.apple.com/documentation/photokit/photospickeritem/4030159-loadtransferable)) [검색 + 일반지식; 본문 비어 있음]. WWDC22 "What's new in the Photos picker". ([출처](https://developer.apple.com/videos/play/wwdc2022/10023/)) [검색]
- **Limited Library**(iOS 14+): 사용자가 일부 사진만 공유 가능. 앱은 그 선택만 본다. `PHPhotoLibrary.authorizationStatus(for: .readWrite)`가 `.limited` 반환. 선택 변경 UI는 `presentLimitedLibraryPicker(from:)`, 변경 감지는 `PHPhotoLibraryChangeObserver`. 구 API `authorizationStatus()`/`requestAuthorization(_:)`는 deprecated이며 limited에서도 `.authorized`를 반환. ([출처](https://mackuba.eu/notes/wwdc20/handle-the-limited-photos-library/)) [확인, 2차]
- **Add-only** 접근: 저장만 필요하면 `requestAuthorization(for: .addOnly)` + `NSPhotoLibraryAddUsageDescription`. ([출처](https://mackuba.eu/2020/07/07/photo-library-changes-ios-14/)) [확인, 2차]
- 자동 "선택 변경" 알림 억제는 `PHPhotoLibraryPreventAutomaticLimitedAccessAlert` — 자체 UI를 만든 뒤에만. [확인, 2차]
- 카메라: `NSCameraUsageDescription` 필요, `AVCaptureDevice.authorizationStatus(for: .video)` / `requestAccess`, 커스텀 카메라는 `AVCaptureSession`. [일반지식]

### 공식 가이드라인(권장 방식)
- App Review 5.1.1: 권한 purpose string은 데이터 사용 방식을 명확·완전하게; **가능하면 out-of-process picker/share sheet를 쓰고 전체 Photos 접근을 피함**; 사용자가 권한을 거부해도 핵심 기능을 막지 말 것. ([출처](https://developer.apple.com/app-store/review/guidelines/)) [확인]
- 권한은 필요한 동작 시점에 요청(상태가 undetermined일 때만 프롬프트 표시). ([출처](https://mackuba.eu/notes/wwdc20/handle-the-limited-photos-library/)) [확인, 2차]
- 5.1.2(v): 사진 데이터를 이용해 사용자의 명시적 의사 없이 타인에게 연락하지 말 것. [확인]

### 자주 하는 실수·놓치기 쉬운 점
- 권한이 필요 없는데 `.readWrite` 전체 접근을 요청 → 리뷰 지적 + 전환율 하락. 찰캌의 "사진 선택해서 게시"는 PhotosPicker로 충분.
- EXIF 위치정보 포함 업로드: 사용자 의도 없이 GPS가 서버로 가면 프라이버시 이슈 → 업로드 전 제거 정책 필요(서버 정책 문서와 대조). [일반지식]
- `loadTransferable`이 `nil` 반환/실패하는 경우(iCloud 원본 다운로드 중, 포맷 미지원) 처리 누락. HEIC/Live Photo/대용량 처리. [일반지식]
- 큰 원본을 `Data`로 메모리에 한 번에 로드 → 메모리 급증. 파일 URL 기반 `Transferable`(`FileRepresentation`)로 임시 파일에 받고 다운샘플링(`CGImageSource` thumbnail). [일반지식]
- 이미지 방향(EXIF orientation) 무시.
- Info.plist usage description 누락 시 접근 순간 크래시. [일반지식]

### 체크리스트
- [ ] 게시 플로우: PhotosPicker(권한 없음) + 카메라 촬영(Camera 권한)
- [ ] 카메라/사진 저장 usage description 문구 작성(한국어)
- [ ] 권한 거부/제한 상태 UI 대안 제공
- [ ] 다운샘플링·EXIF 제거·HEIC→JPEG 변환 파이프라인

---

## 8. APNs 푸시

### 핵심 개념
- 디바이스 토큰 = 앱+기기 고유 주소. 같은 기기의 다른 앱과 공유 불가. ([출처](https://developer.apple.com/documentation/usernotifications/registering-your-app-with-apns.md)) [확인]
- 순서: Xcode에서 Push Notifications capability 추가(→ `aps-environment` entitlement) + 개발자 계정의 App ID에서 push 활성화 → 앱 실행 시마다 `UIApplication.shared.registerForRemoteNotifications()` → `application(_:didRegisterForRemoteNotificationsWithDeviceToken:)` 에서 서버로 전송, 실패는 `didFailToRegisterForRemoteNotificationsWithError`에서 플래그 후 재시도. [확인]
- 알림 표시 권한은 별개: `UNUserNotificationCenter.current().requestAuthorization(options:)`. [일반지식] (토큰 등록 자체는 권한 없이도 가능, 사용자 가시 알림은 권한 필요)

### 권장
- **디바이스 토큰을 로컬에 캐시하지 말고** 매 실행 시 받은 값을 서버에 갱신 전송(복원/새 기기/OS 재설치 시 토큰 변경). 사용자는 여러 기기를 가질 수 있으므로 서버는 사용자당 다중 토큰 저장. 전송 시 사용자 식별정보와 함께 암호화된 연결로. ([출처](https://developer.apple.com/documentation/usernotifications/registering-your-app-with-apns.md)) [확인]
- 권한 프롬프트는 맥락 있는 시점(예: 첫 좋아요/알림 설정 화면)에 요청. 5.1.2(i): **푸시를 켜야만 기능을 쓸 수 있게 만들지 말 것**. ([출처](https://developer.apple.com/app-store/review/guidelines/)) [확인]
- Background(silent) push는 항상 재량·저우선순위·병합(coalesce)되며 강제 종료한 앱에는 전달 안 됨 → 중요한 동기화의 유일 수단으로 쓰지 말 것. ([출처](https://developer.apple.com/videos/play/wwdc2025/227/)) [확인]
- 알림 탭 처리: `UNUserNotificationCenterDelegate.userNotificationCenter(_:didReceive:)`에서 payload → 딥링크(Universal Link 또는 앱 내 라우팅) 연결. [일반지식]

### 실수
- 개발/프로덕션 환경 혼동(sandbox vs production 토큰·서버 엔드포인트 불일치 → `BadDeviceToken`). [일반지식]
- 로그아웃 시 서버의 토큰-사용자 매핑 해제 누락 → 다른 계정에 알림 전달. [일반지식]
- 포그라운드 수신 시 `willPresent` 미구현 → 알림 배너 안 보임. [일반지식]
- 거부 상태에서 설정으로 안내하는 UI 없음. [일반지식]

### 체크리스트
- [ ] 로그인 후/실행 시마다 토큰 서버 갱신, 로그아웃 시 해제
- [ ] 권한 요청 시점 설계 + 거부 시 대체 UX
- [ ] 알림 탭 → 화면 라우팅
- [ ] sandbox/production 구분

---

## 9. Universal Links

### 핵심 개념 [확인] ([출처](https://developer.apple.com/documentation/xcode/supporting-associated-domains.md))
- 앱과 웹사이트를 Associated Domains로 연결. 서버에 `apple-app-site-association`(확장자 없음, JSON) + 앱에 `com.apple.developer.associated-domains` entitlement(`applinks:도메인`).
- 파일 위치: `https://<FQDN>/.well-known/apple-app-site-association`, **HTTPS + 유효 인증서 + 리다이렉트 없음**.
- `applinks.details[].appIDs`는 `<TeamID>.<BundleID>`, `components`로 경로/쿼리/프래그먼트 매칭(`exclude`로 제외).
- 서브도메인마다 entitlement 항목과 별도 AASA 파일 필요. `*.` 와일드카드는 appclips 외 서비스에서 가능. 도메인에 경로/슬래시 포함 금지.
- iOS 14+부터 앱은 AASA를 서버가 아닌 **Apple CDN**에서 받는다. CDN은 24시간 내 요청하고, 기기는 설치 후 약 주 1회 갱신 확인. 비공개 서버 개발 시 `?mode=developer` 같은 alternate mode로 CDN 우회(세부 모드는 entitlement 문서).

### 권장
- SwiftUI에서는 `.onOpenURL`(및 필요 시 `onContinueUserActivity(NSUserActivityTypeBrowsing)`)로 수신 후 내비게이션 상태에 매핑. [일반지식]
- 푸시 payload·공유 링크(게시물 상세 `/posts/{id}`)를 같은 라우터로 처리. [일반지식]

### 실수
- AASA가 리다이렉트되는 도메인(www ↔ apex)에서 실패. [확인(리다이렉트 금지)]
- 변경 후 즉시 반영 기대 — CDN 캐시 지연. [확인]
- Safari 주소창 직접 입력은 Universal Link가 동작하지 않음(동일 도메인 내 탐색 등), 롱프레스 메뉴 선택으로 앱 열기 선호가 기억될 수 있음. [일반지식]
- 콜드 스타트 시 링크 도착 시점에 인증/데이터가 준비 전 → pending route 큐잉. [일반지식]

### 체크리스트
- [ ] AASA 호스팅 및 `curl` 검증, 서브도메인별 entitlement
- [ ] 라우터 하나로 push/Universal Link/URL scheme 통합
- [ ] 비로그인/콜드스타트 케이스

---

## 10. HIG 핵심 (내비게이션, 안전 영역, Dynamic Type, 터치 영역)

### 핵심 개념 / 권장
- 터치 영역 최소 **44x44pt** (HIG 일반 가이드, 본문 직접 확인 못함) [일반지식]. 작은 아이콘 버튼은 `.frame(minWidth: 44, minHeight: 44)` + `.contentShape(Rectangle())`.
- Safe area: 콘텐츠는 safe area 안에 두고 배경만 `ignoresSafeArea`. 키보드는 `safeAreaInset`/기본 키보드 회피. [일반지식]
- 내비게이션: `NavigationStack`(+ `NavigationPath`/값 기반 `navigationDestination`), 상위 구분은 `TabView`. 모달은 sheet/fullScreenCover. [일반지식]
- **Dynamic Type**: App Store Connect "Supports Larger Text" 기준 — 기본 대비 **최소 200%** 또는 시스템 최대까지 확대 가능해야 하고, AX3에서 200% 초과·AX5에서 300% 초과. 겹침 없이 reflow, 정보 숨김 금지, 2줄 이상 wrapping 허용. Tab Bar는 Dynamic Type으로 커지지 않으므로 Large Content Viewer 등 대안 제공. 다국어(긴 단어, RTL)도 테스트. ([출처](https://developer.apple.com/help/app-store-connect/manage-app-accessibility/larger-text-evaluation-criteria/)) [확인]
- Dark Mode, 시스템 색/SF Symbols/시맨틱 폰트(`.body`, `.headline`) 사용 권장. [일반지식]

### 실수
- 고정 폰트 크기(`.system(size:)`)와 고정 높이 컨테이너로 큰 글씨에서 잘림 → `@ScaledMetric`, `ViewThatFits`, `dynamicTypeSize` 분기로 가로 → 세로 레이아웃 전환. [일반지식]
- 이미지 그리드/피드 카드에서 캡션 truncation으로 정보 소실. [확인(기준)]
- 커스텀 제스처(스와이프 삭제 등)만 제공하고 대안 없음 → 접근성 위배(11절).
- 내비게이션 상태를 뷰마다 분산 → 딥링크 처리 불가.

### 체크리스트
- [ ] 모든 탭 대상 44pt 이상
- [ ] Xcode Preview로 AX 최대 크기 / Dark / 다국어 확인
- [ ] 단일 Router(`NavigationPath`)로 딥링크 대응

---

## 11. 접근성 (VoiceOver)

### 핵심 개념 [확인] ([출처](https://developer.apple.com/design/human-interface-guidelines/voiceover.md))
- 모든 핵심 인터페이스 요소에 **대체 라벨** 제공; 커스텀 요소에도 필수; UI 변경 시 최신 유지.
- 의미 있는 이미지는 **설명**하고, 순수 장식 이미지는 VoiceOver에서 **제외**(`accessibilityHidden(true)`). 주변 캡션이 있으면 이미지 자체가 전달하는 정보만 서술.
- 화면 제목/헤딩으로 구조 제공, 관련 요소(이미지+캡션)는 그룹핑해서 한 프레임으로 읽히게. 레이아웃/콘텐츠 변경은 `AccessibilityNotification`으로 알림. 가능하면 rotor 지원(`AccessibilityRotorEntry`).
- 상세 API(일반지식): `.accessibilityLabel/.accessibilityValue/.accessibilityHint`, `.accessibilityElement(children: .combine)`, `.accessibilityAddTraits(.isButton/.isHeader)`, `.accessibilityAction(named:)` 로 스와이프 제스처 대체. [일반지식]
- App Store Connect에 "VoiceOver evaluation criteria" 문서가 있어 Accessibility Nutrition Label 선언 기준으로 쓰임. ([출처](https://developer.apple.com/help/app-store-connect/manage-app-accessibility/voiceover-evaluation-criteria/)) [검색]

### 자주 하는 실수
- 좋아요 버튼이 아이콘만 있고 라벨 없음 ("하트" / 상태·개수 누락) → "좋아요, 12개, 선택됨" 형태의 label+value+trait.
- 사진 피드에서 이미지 대체 텍스트 없음 → 사용자 입력 캡션 또는 AI 생성 설명 활용 가능(AI 데이터 전송 동의는 14절).
- 시각적으로만 의미 전달(색 하나로 상태 표시).
- 롱프레스/스와이프에만 있는 동작을 custom action으로 노출 안 함.

### 체크리스트
- [ ] 아이콘 버튼 전부 라벨, 장식 이미지 hidden
- [ ] 카드 단위 `.combine` + 커스텀 액션(좋아요/신고/공유)
- [ ] Accessibility Inspector + 실제 VoiceOver로 주요 플로우 점검

---

## 12. 테스트 (Swift Testing, XCTest, XCUITest)

### 핵심 개념 [확인] ([출처](https://developer.apple.com/documentation/testing/migratingfromxctest.md))
- Swift Testing: `import Testing`, `@Test` 함수(이름 규칙 없음), 스위트는 struct/actor/class(XCTestCase 상속 불필요, struct/actor 권장), `#expect`(실패해도 계속), `#require`(실패 시 throw/중단, optional unwrap 겸용), `Issue.record`, `confirmation`(콜백 기반 비동기), `withKnownIssue`, 트레이트(`.enabled(if:)`, `.disabled(if:)`, `.serialized`), `Attachment`.
- `setUp/tearDown` → `init()`/`deinit`(teardown 필요 시 class/actor).
- **병렬 실행이 기본**(XCTest는 스위트 내 순차). 전역 공유 상태가 있으면 `.serialized`.
- XCTest 동기 테스트는 main actor, Swift Testing은 임의 태스크 → UI 모델 테스트는 `@MainActor` 명시.
- 한 파일에 XCTest와 Testing 혼용 가능, 상호운용 모드(`none/limited/complete/strict`; 툴체인 6.4+에서 기본값 변화).
- 비동기 검증은 가능하면 `await`; 콜백만 있으면 continuation 또는 `confirmation`.
- 수치 오차 비교(`accuracy:`)는 Swift Testing에 없음 → swift-numerics `isApproximatelyEqual`.
- XCUITest/성능 테스트(XCTMetric)는 Swift Testing 미지원 영역으로 XCTest 유지 (이번 fetch 본문에는 명시 없음; 일반지식). [일반지식]

### 권장
- 단위/도메인 로직 = Swift Testing, UI 자동화 = XCUITest (accessibility identifier 기반 쿼리). 네트워크는 `URLProtocol` 스텁 또는 프로토콜 주입. [일반지식]
- UITest는 `launchArguments`로 목 서버/온보딩 스킵. [일반지식]

### 실수
- 병렬 실행 + 싱글톤/UserDefaults 공유로 플레이키 테스트 → `.serialized` 또는 의존성 주입.
- `@MainActor` 모델을 비격리 테스트에서 직접 접근해 isolation 오류.
- XCUITest에서 레이블 텍스트로 찾다가 로컬라이제이션 변경 시 깨짐 → `accessibilityIdentifier`.
- Sleep 기반 대기.

### 체크리스트
- [ ] 신규 단위 테스트는 Swift Testing
- [ ] 핵심 플로우(로그인→게시→좋아요) XCUITest 스모크
- [ ] 업로드 큐/토큰 갱신 같은 동시성 코드 테스트

---

## 13. App Store 심사 (자주 걸리는 사유) & 계정 삭제

### 핵심 개념 [확인] ([출처](https://developer.apple.com/app-store/review/guidelines/))
- **1.2 UGC**: 게시 전 부적절 콘텐츠 필터링 수단, **신고 기능과 적시 대응**, **사용자 차단**, 연락처 공개. 위반 콘텐츠 제거. AI 콘텐츠 생성 앱도 이 조항 적용. → 찰캌(사진 게시/좋아요/사인)은 UGC 앱이므로 신고·차단·필터링 UI 필수.
- **2.1 앱 완성도**: 크래시/명백한 버그는 거절. **로그인 앱은 데모 계정 제공** 및 백엔드 라이브 유지, 플레이스홀더 제거. (데모 계정이 어렵다면 데모 모드 승인 요청 가능)
- **2.3**: 정확한 메타데이터, 스크린샷은 실제 사용 화면, 새 기능은 Notes for Review에 구체적으로.
- **4.2 최소 기능**: 웹사이트 재포장 앱 금지.
- **4.8 로그인 서비스**: 구글/페이스북 등 제3자 소셜 로그인을 주 계정 수단으로 제공하면 동등한 대안(이름·이메일만 수집, 이메일 비공개 가능, 광고용 상호작용 수집 불가 — 사실상 Sign in with Apple) 제공. 자사 계정만 쓰는 경우 예외.
- **5.1.1**: 앱 내·메타데이터에 개인정보처리방침 링크, 수집 전 동의, 권한 purpose string 명확화, out-of-process picker 우선, 거부해도 핵심 기능 유지, **계정 생성 앱은 앱 내 계정 삭제 제공**, 소셜 로그인 자격 해지 수단.
- **5.1.2(i)**: 개인정보를 **제3자 AI를 포함한 외부에 공유**하려면 명확히 공개하고 **명시적 허가**를 받아야 함. 푸시·위치 등 시스템 기능 활성화를 기능 사용 조건으로 요구 금지.
- **계정 삭제 세부** ([출처](https://developer.apple.com/support/offering-account-deletion-in-your-app)) [확인]: 앱 내에서 시작 가능해야 하며 비활성화만으로는 불충분; 전체 계정과 개인 데이터 삭제 제공; 웹이 필요하면 삭제 페이지로 직접 링크; 재인증/이메일 코드 등 확인 단계는 허용, 지원센터 연락 강제는 불가(고규제 산업 제외); 처리 시간·법적 보관 데이터 고지; Sign in with Apple 사용자는 REST API로 토큰 revoke; **사용자 생성 콘텐츠(사진, 게시물 등)도 계정과 함께 삭제**(법적 보관 제외, 고지); GDPR 대상자만이 아닌 모든 사용자에 제공; 구독이 있으면 Apple 청구 지속을 고지.

### 실수
- 데모 계정/테스트 데이터 없이 제출 → 2.1 거절. 리뷰 시점에 서버 꺼져 있음.
- 신고/차단 없이 UGC 제출 → 1.2 거절.
- 계정 삭제 버튼 누락 또는 "탈퇴 문의는 이메일로" 방식.
- purpose string이 일반적("앱 기능을 위해") → 거절. 구체적으로.
- Sign in with Apple 없이 소셜 로그인만 제공(4.8).
- AI 사진 리뷰 기능에서 사진을 외부 AI 서비스로 전송하는데 앱 내 동의 UI/방침 고지 없음(5.1.2(i)).

### 체크리스트
- [ ] 신고·차단·필터링·연락처 + 약관(UGC 정책) 동의 플로우
- [ ] 데모 계정 + Notes for Review
- [ ] 앱 내 계정 삭제(콘텐츠 포함) + SIWA revoke
- [ ] purpose string 구체화, 개인정보처리방침 링크(앱 내·메타데이터)
- [ ] 소셜 로그인 도입 시 SIWA 병행

---

## 14. Privacy Manifest / Required Reason API / ATT

### 핵심 개념 [확인] ([출처](https://developer.apple.com/tutorials/data/documentation/bundleresources/describing-use-of-required-reason-api.md))
- Required Reason API: 핑거프린팅에 악용될 수 있는 API를 쓸 때 **privacy manifest(`PrivacyInfo.xcprivacy`)의 `NSPrivacyAccessedAPITypes`**에 카테고리(`NSPrivacyAccessedAPIType`)와 승인된 사유 코드(`NSPrivacyAccessedAPITypeReasons`)를 선언. **2024-05-01부터 미선언 앱은 App Store Connect에서 거절**.
- 앱 코드와 **서드파티 SDK는 각자** 번들의 manifest에 선언해야 하며 서로 대신해 줄 수 없음. 실행 파일/동적 라이브러리를 포함한 각 번들에 manifest 필요.
- 선언한 사유 **목적 이외 사용 금지**, 추적 목적 사용 금지. 목록에 없는 사유는 요청 양식으로 신청 가능.
- 카테고리 예(일반지식, 목록은 위 문서의 하위 키 문서 참조): 파일 타임스탬프, 시스템 부팅 시간, 디스크 공간, 활성 키보드, `UserDefaults`. `UserDefaults`는 거의 모든 앱이 사용하므로 사유 선언이 사실상 필수. [일반지식]
- 참고 2차 자료(검색 결과): https://nutrient.io/blog/privacy-manifests-on-ios, https://bugfender.com/blog/apple-privacy-requirements/ [검색]

### ATT (App Tracking Transparency) [확인] ([출처](https://developer.apple.com/app-store/user-privacy-and-data-use/))
- 추적 = 타사 앱/웹/오프라인 데이터와 결합해 타깃 광고·광고 측정에 쓰거나 데이터 브로커에 공유. 이를 하거나 IDFA 접근 시 ATT 프롬프트 필요. 허가 없으면 IDFA는 0.
- 응답 존중(다른 동의창보다 우선), 추적 동의를 기능 조건/보상으로 삼기 금지, 해시된 이메일 등으로 우회 금지, 핑거프린팅 금지, SDK 책임은 앱.
- `identifierForVendor`는 동일 개발사 앱 간 분석에 ATT 없이 가능(타사와 결합 불가).
- Info.plist `NSUserTrackingUsageDescription` 필요(프롬프트 문구). ([출처](https://developer.apple.com/documentation/bundleresources/information-property-list/nsusertrackingusagedescription)) [검색; 본문 일반지식]
- 해당 페이지에는 EU 관련 변경(대체 ATT 프롬프트, 1년 후 재프롬프트 허용 등)이 "iOS/iPadOS 27.2+"로 적혀 있다 — 미래 OS 정보이므로 적용 시 원문 재확인 필요. [확인]

### 실수
- 광고/분석 SDK를 붙이고 ATT·manifest 미점검 → 업로드 시 경고/거절.
- 자체 분석만 쓰면 ATT 불필요한데 불필요하게 프롬프트를 띄움(전환율 하락). 찰캌이 광고/타사 데이터 결합을 하지 않으면 ATT는 필요 없다.
- App Privacy "영양 라벨" 응답과 실제 수집 데이터 불일치. [일반지식]

### 체크리스트
- [ ] `PrivacyInfo.xcprivacy` 작성(UserDefaults 등), SDK별 manifest 확인(Xcode의 Privacy Report 활용 [일반지식])
- [ ] App Store Connect App Privacy 응답 = 실제 수집(사진, 사용자 ID, 푸시 토큰, 진단 등)
- [ ] ATT 필요 여부 결정(대부분 불필요)

---

## 15. 메모리 누수 (retain cycle) & 메인 스레드 UI 갱신

### 핵심 개념 (대부분 일반지식)
- 클로저가 `self`를 강하게 캡처하고 `self`가 그 클로저를 저장하면 순환: `[weak self]` / `[unowned self]`. Combine `sink`, NotificationCenter 옵저버, Timer, delegate(`weak var delegate`), 저장된 `Task`(장기 루프 + `self` 캡처)가 대표 원인. [일반지식]
- `URLSession`은 delegate를 강하게 보유 → 세션을 `invalidateAndCancel`/`finishTasksAndInvalidate` 하지 않으면 누수. [일반지식]
- Observation 관찰용 `Task`는 로컬 변수 캡처로 의도보다 오래 살 수 있고, `deinit`에서 취소해야 하는데 `deinit`은 main actor 비격리. ([출처](https://jaredsinclair.com/2025/09/10/observation.html)) [확인, 2차]
- SwiftUI 뷰는 값 타입이라 뷰 자체는 순환을 만들지 않지만, 뷰가 만든 모델/클로저/`@StateObject`류가 문제. 모델이 View 클로저를 보유하지 않게.
- UI 갱신은 반드시 main thread/`@MainActor`. Swift 6 모드에서는 컴파일러가 위반을 잡아 준다. 레거시 delegate/콜백은 임의 큐 → `await MainActor.run` 또는 `Task { @MainActor in }`. Apple 문서의 background session 샘플도 main 큐로 hop. ([출처](https://developer.apple.com/documentation/foundation/downloading-files-in-the-background.md)) [확인]
- 진단 도구: Xcode Memory Graph Debugger, Instruments(Leaks/Allocations), Main Thread Checker, `deinit` 로그. [일반지식]

### 실수 / 체크리스트
- [ ] 피드 이미지 캐시 크기 제한(`NSCache`) 및 다운샘플링
- [ ] 저장된 Task/Combine 구독은 소유자 수명에 맞춰 cancel
- [ ] delegate는 weak, 클로저 캡처 리스트 점검
- [ ] 화면 pop 후 `deinit` 호출 여부 로그로 확인

---

## 16. 일반적인 실수 모음 (iOS 한정)

- 시뮬레이터에서만 테스트 → 실제 기기에서 푸시, 카메라, background session, 메모리 압박 검증 필요. [일반지식]
- 첫 실행 시 모든 권한 한꺼번에 요청 → 거부율 상승, 심사에서 지적 가능(5.1.1/5.1.2). ([출처](https://developer.apple.com/app-store/review/guidelines/)) [확인]
- 토큰 UserDefaults 저장 (Keychain으로). [일반지식]
- 디바이스 토큰 캐시. [확인]
- `@State`에 무거운 모델 init. [확인, 2차]
- background 업로드를 Data로. [확인]
- AASA 리다이렉트/캐시 오해. [확인]
- Dynamic Type/VoiceOver 후순위 → 늦게 구조 변경 필요. [확인(기준)]
- 서드파티 SDK의 privacy manifest 미확인. [확인]
- 오래된 문서(UIKit/ObservableObject/GCD 기반 블로그)를 그대로 복사 — iOS 17+/Swift 6 기준으로 변환.

---

## 찰캌 적용 포인트

| 찰캌 기능 | iOS 구현 시 핵심 | 근거 절 |
|---|---|---|
| 일일 주제별 사진 게시 | `PhotosPicker`(권한 불필요) + 카메라(Camera 권한은 촬영 시점에). 다운샘플링/EXIF 제거/HEIC 변환 후 업로드 | 7 |
| 이미지 업로드 | 임시 파일 저장 → background `URLSession` 단일 세션 `uploadTask(fromFile:)`. 세션 재생성·`handleEventsForBackgroundURLSession`. 사용자 시작 업로드는 iOS 26 `BGContinuedProcessingTask`도 검토(진행률 필수) | 4 |
| 업로드 상태 UI | `@Observable @MainActor` UploadQueue 모델, 상태 영속화, 취소/재시도 | 1,2 |
| 좋아요/사인 | 낙관적 업데이트는 모델 레벨, Task 취소/중복 요청(actor reentrancy) 주의. 아이콘 버튼 44pt + VoiceOver label/value/trait | 2,10,11 |
| 푸시 알림 | 매 실행 시 디바이스 토큰 서버 갱신(캐시 금지), 다중 기기 지원, 로그아웃 시 해제, 알림 탭 → 딥링크 라우터. 권한 프롬프트는 맥락 시점. 푸시 비활성 시에도 기능 사용 가능해야 함 | 8,13 |
| 공유 링크/알림 딥링크 | Universal Links(AASA는 리다이렉트 없는 `/.well-known/`), `.onOpenURL` → 단일 Router | 9 |
| AI 사진 리뷰 | 사진이 외부 AI로 전송되면 5.1.2(i) 명시적 동의·고지 필요, privacy 라벨/방침에 반영. 결과 텍스트를 접근성 설명으로 활용 가능 | 13,11 |
| UGC 운영 | 신고·차단·필터링·연락처·약관 동의(1.2), 데모 계정(2.1) | 13 |
| 계정 | 앱 내 계정 삭제(게시 사진·좋아요 등 UGC 포함 삭제), 소셜 로그인 시 SIWA 병행(4.8), 토큰은 Keychain | 6,13 |
| 프라이버시 | `PrivacyInfo.xcprivacy`(UserDefaults 등), SDK manifest, ATT는 광고/타사 결합 없으면 불필요 | 14 |
| 품질 | Swift Testing(도메인/업로드 큐), XCUITest 스모크, strict concurrency/Swift 6 모드, Memory Graph로 누수 점검 | 2,12,15 |
| 백엔드 정책 연동 | 업로드 제한/포맷/주제 시각 등 규칙은 `docs/business-rules`(읽기 전용)와 대조해 구현; 규칙이 없거나 API와 충돌하면 백엔드 확인 대상으로만 보고(CLAUDE.md 지침) | - |

미확인/후속 조사 후보: ① Keychain·PhotosPicker·HIG 터치 영역의 공식 본문 재확인(이번 fetch 실패), ② Swift 6.2 "approachable concurrency"(default main actor isolation) 공식 설정 확인, ③ Privacy manifest 카테고리별 사유 코드 전체 표, ④ `BGContinuedProcessingTask` 최소 OS와 업로드 적합성 검증, ⑤ 최신 OS 번호/최소 배포 타깃 결정.

---

## 출처 목록

직접 fetch 하여 본문 확인 [확인]
1. Migrating from the Observable Object protocol to the Observable macro - https://developer.apple.com/documentation/swiftui/migrating-from-the-observable-object-protocol-to-the-observable-macro.md
2. Downloading files in the background - https://developer.apple.com/documentation/foundation/downloading-files-in-the-background.md
3. Describing use of required reason API - https://developer.apple.com/tutorials/data/documentation/bundleresources/describing-use-of-required-reason-api.md
4. Registering your app with APNs - https://developer.apple.com/documentation/usernotifications/registering-your-app-with-apns.md
5. Supporting associated domains - https://developer.apple.com/documentation/xcode/supporting-associated-domains.md
6. Migrating a test from XCTest (Swift Testing) - https://developer.apple.com/documentation/testing/migratingfromxctest.md
7. Offering account deletion in your app - https://developer.apple.com/support/offering-account-deletion-in-your-app
8. App Review Guidelines - https://developer.apple.com/app-store/review/guidelines/ (본문 앞 100,000자까지 확인; 5.1.4 이후 미확인)
9. User Privacy and Data Use (ATT) - https://developer.apple.com/app-store/user-privacy-and-data-use/
10. Finish tasks in the background (WWDC25-227) - https://developer.apple.com/videos/play/wwdc2025/227/
11. HIG VoiceOver - https://developer.apple.com/design/human-interface-guidelines/voiceover.md
12. Larger Text evaluation criteria - https://developer.apple.com/help/app-store-connect/manage-app-accessibility/larger-text-evaluation-criteria/
13. Handle the limited photos library (WWDC20 notes, mackuba.eu, 2차) - https://mackuba.eu/notes/wwdc20/handle-the-limited-photos-library/
14. Photo library changes in iOS 14 (mackuba.eu, 2차) - https://mackuba.eu/2020/07/07/photo-library-changes-ios-14/
15. We Need to Talk About Observation (Jared Sinclair, 2차) - https://jaredsinclair.com/2025/09/10/observation.html
16. Swift Migration Guide (MigrationGuide.md, 목차 수준) - https://github.com/swiftlang/swift-migration-guide/blob/main/Guide.docc/MigrationGuide.md

검색 결과로만 확인(본문 미확인 또는 fetch 본문 빈약) [검색]
17. Swift 6 Data Race Safety - https://www.swift.org/migration/documentation/swift-6-concurrency-migration-guide/dataracesafety/
18. Enable data-race safety checking - https://www.swift.org/migration/documentation/swift-6-concurrency-migration-guide/enabledataracesafety/
19. Adding a password to the keychain - https://developer.apple.com/documentation/security/adding-a-password-to-the-keychain
20. Restricting keychain item accessibility - https://developer.apple.com/documentation/security/restricting-keychain-item-accessibility
21. PhotosPickerItem.loadTransferable(type:) - https://developer.apple.com/documentation/photokit/photospickeritem/4030159-loadtransferable
22. What's new in the Photos picker (WWDC22) - https://developer.apple.com/videos/play/wwdc2022/10023/
23. Code-along: Elevate an app with Swift concurrency (WWDC25-270) - https://developer.apple.com/videos/play/wwdc2025/270/
24. NSUserTrackingUsageDescription - https://developer.apple.com/documentation/bundleresources/information-property-list/nsusertrackingusagedescription
25. VoiceOver evaluation criteria - https://developer.apple.com/help/app-store-connect/manage-app-accessibility/voiceover-evaluation-criteria/
26. Observation framework - https://developer.apple.com/documentation/observation.md
27. scenePhase 관련 Apple Forums - https://developer.apple.com/forums/thread/723352 , https://developer.apple.com/forums/thread/778599
28. Background multipart file upload from iOS app (2차) - https://aplus.rs/2024/background-multipart-file-upload-from-ios-app
29. Privacy manifests 2차 자료 - https://nutrient.io/blog/privacy-manifests-on-ios , https://bugfender.com/blog/apple-privacy-requirements/
