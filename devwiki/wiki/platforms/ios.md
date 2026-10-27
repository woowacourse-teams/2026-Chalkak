# iOS 네이티브 (Swift, SwiftUI)

공통 기준은 [common/](../README.md#공통-문서)에 있다. 이 문서는 iOS에만 있는 API와 함정만 담는다. 근거: [raw/ios-native.md](../../raw/ios-native.md). 기존 앱: `client/iOS`(SwiftUI, 배포 타깃 iOS 18.0, [client/iOS/CLAUDE.md](../../../client/iOS/CLAUDE.md)).

## 권장 스택

| 영역 | 선택 | 메모 |
| --- | --- | --- |
| UI | SwiftUI, `NavigationStack` + 값 기반 경로 | UIKit은 APNs·background session 훅에만(`@UIApplicationDelegateAdaptor`) |
| 상태 | `@Observable @MainActor` ViewModel | iOS 17+. `ObservableObject`는 새 코드에 쓰지 않음 [공식](https://developer.apple.com/documentation/swiftui/migrating-from-the-observable-object-protocol-to-the-observable-macro) |
| 동시성 | Swift 6 언어 모드, DTO는 `Sendable` struct, 토큰·업로드 관리자는 `actor` | [공식](https://www.swift.org/migration/documentation/swift-6-concurrency-migration-guide/dataracesafety/) |
| 네트워크 | 공용 `URLSession` 하나 + (도입 시) 업로드용 background session 하나 | |
| 저장 | Keychain(토큰), 파일 캐시, 필요 시 SwiftData | |
| 테스트 | Swift Testing(단위), XCUITest·Maestro(UI) | [공식](https://developer.apple.com/documentation/testing/migratingfromxctest) |

## SwiftUI·Observation 함정

- 모델을 **만들고 소유하는 뷰만** `@State private var model = Model()`. 하위 뷰는 파라미터나 `@Environment`로 받고, 바인딩이 필요하면 `@Bindable`.
- `@State`는 `@StateObject`와 달리 초기값을 lazy 평가하지 않는다. 부모가 다시 그려질 때 모델 `init`이 반복 호출될 수 있으므로 `init`에 네트워크 호출·구독 같은 부수효과를 두지 않는다. 로딩은 `.task {}`에서 시작한다. [공식·미열람]
- 추적할 필요 없는 프로퍼티(주입된 서비스, `Task` 핸들, 캐시)는 `@ObservationIgnored`.
- 화면 밖(모델 간) 관찰은 `withObservationTracking`이 1회성이라 불편하다. 연속 관찰은 `AsyncStream`으로 설계한다.

## Swift Concurrency 함정

- `@MainActor` 클래스의 `deinit`은 메인 액터 격리가 아니다. 거기서 가변 상태를 만지지 않는다.
- `Task {}` 안에서 `self` 강한 캡처 → 수명 연장. 화면 수명 작업은 `.task {}`로, 그 외는 핸들을 보관했다가 취소.
- actor 재진입: `await` 전후로 상태가 바뀔 수 있다. "업로드 중이면 시작 안 함" 같은 검사는 `await` 뒤에 다시 확인한다.
- `@unchecked Sendable`, `nonisolated(unsafe)`로 경고를 덮지 않는다. 쓰면 이유를 주석으로 남긴다.
- 레거시 delegate 콜백(background session 등)은 임의 큐에서 온다. UI 반영은 메인 액터로 넘긴다.

## 앱 수명주기

- `scenePhase`: `.inactive`는 알림 센터·시트 등으로 자주 온다. 무거운 작업은 `.background`에서. 종료 콜백에 의존한 저장은 불가능하다.
- 짧은 마무리 작업은 `beginBackgroundTask`/`endBackgroundTask`. [공식](https://developer.apple.com/videos/play/wwdc2025/227/)
- 복귀 시: 권한 상태, 토큰, 오늘의 주제 날짜를 다시 확인한다.

## 백그라운드 업로드(도입 시)

[media-upload.md §4](../common/media-upload.md#4-백그라운드-업로드)의 결정이 먼저다. 도입하면:

- `URLSessionConfiguration.background(withIdentifier:)` + **임시 파일에서** `uploadTask(with:fromFile:)`. `Data` 바디는 앱이 종료되면 실패한다.
- 앱 시작 시 같은 식별자로 세션을 다시 만들고, `handleEventsForBackgroundURLSession`의 completion handler를 저장했다가 `urlSessionDidFinishEvents`에서 메인 스레드로 호출한다. 호출하지 않으면 시스템 페널티. [공식](https://developer.apple.com/documentation/foundation/downloading-files-in-the-background)
- 백그라운드 세션에서는 리다이렉트가 delegate로 오지 않는다. 완료마다 새 백그라운드 작업을 시작하면 시스템이 점점 늦춘다.
- 개발 중에는 Background Networking 프로필로 스케줄링을 관찰한다.

## Keychain

- `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly`(잠금 상태 백그라운드에서 토큰이 필요할 때) 또는 `WhenUnlockedThisDeviceOnly`. `Always`는 deprecated. [공식·미열람]
- `errSecDuplicateItem`이면 update로 폴백. 메인 스레드에서 반복 호출하지 않는다.
- 앱을 지워도 항목이 남는다. 첫 실행 표시가 없으면 Keychain을 비운다.

## 사진

- `PhotosPicker` + `loadTransferable`. 큰 원본은 `Data`보다 파일 표현(`FileRepresentation`)으로 받아 `CGImageSource` 썸네일 API로 다운샘플한다.
- `loadTransferable`이 `nil`·실패를 돌려줄 수 있다(iCloud 원본 다운로드 중, Live Photo). 오류 상태로 처리한다.
- WebP 인코딩은 ImageIO 지원 여부를 기존 iOS 앱 구현에서 먼저 확인한다. [media-upload.md §3.1](../common/media-upload.md#31-인코딩)
- 카메라는 `NSCameraUsageDescription`. 사진 저장 기능을 넣으면 add-only 권한(`NSPhotoLibraryAddUsageDescription`)만.

## APNs·푸시

- Push Notifications capability(`aps-environment`), 실행마다 `registerForRemoteNotifications()`. APNs 기기 토큰은 캐시하지 않는다. 찰캌 서버에는 **FCM 토큰**을 등록한다(Firebase Messaging이 APNs 토큰을 매핑). [공식](https://developer.apple.com/documentation/usernotifications/registering-your-app-with-apns)
- `UNUserNotificationCenter.delegate`는 `didFinishLaunching` 안에서 지정. `willPresent`를 구현하지 않으면 포그라운드 배너가 안 뜬다.
- 사용자가 앱 스위처에서 강제 종료하면 background(silent) push가 오지 않는다. 표시용 알림은 온다.
- sandbox와 production 환경 혼동(`BadDeviceToken`)에 주의.
- 탭 처리 결과는 [push-deeplink-navigation.md §1](../common/push-deeplink-navigation.md#1-푸시-탭--도착-화면-다섯-플랫폼-동일).

## Universal Links(도입 시)

- `https://<도메인>/.well-known/apple-app-site-association`, HTTPS, 리다이렉트 없음. iOS 14+는 Apple CDN이 파일을 가져가므로 반영이 늦다. 개발 중에는 `?mode=developer`. [공식](https://developer.apple.com/documentation/xcode/supporting-associated-domains)
- 수신은 `.onOpenURL`과 `onContinueUserActivity`. 푸시·링크를 같은 라우터로 보낸다.

## 접근성·HIG

- Dynamic Type: App Store Connect "Larger Text" 기준은 최소 200%까지 겹침 없이 reflow. `@ScaledMetric`, `ViewThatFits`. [공식](https://developer.apple.com/help/app-store-connect/manage-app-accessibility/larger-text-evaluation-criteria/)
- 카드 단위 `.accessibilityElement(children: .combine)`, 좋아요는 label + value("12개") + 선택 trait. 장식 이미지는 `accessibilityHidden`.
- 스와이프 전용 동작은 `accessibilityAction(named:)`로도 노출.

## 심사

[security-privacy-release.md §5](../common/security-privacy-release.md#5-스토어-심사-필수-항목)의 항목에 더해:

- 2.1: 심사용 **데모 계정 또는 데모 모드**와 살아 있는 서버가 필요하다. 소셜 로그인만 있는 찰캌은 심사 계정 준비 방법을 미리 정한다.
- Privacy manifest(`PrivacyInfo.xcprivacy`)의 Required Reason API 선언(예: `UserDefaults`). 서드파티 SDK의 manifest 포함 여부.

## 체크리스트

- [ ] `@Observable` 모델 `init`에 부수효과 없음, 소유는 `@State` 한 곳
- [ ] Swift 6 모드에서 경고 없이 빌드, `@unchecked Sendable` 사유 주석
- [ ] 토큰 갱신이 `actor` 하나에서 single-flight
- [ ] Keychain 접근 클래스 결정, 재설치 정리
- [ ] 콜드 스타트 푸시 탭 처리
- [ ] Dynamic Type 최대, VoiceOver 핵심 흐름 확인
