# Android 네이티브 (Kotlin, Jetpack Compose)

공통 기준은 [common/](../README.md#공통-문서)에 있다. 이 문서는 Android에만 있는 API와 함정만 담는다. 근거: [raw/android-native.md](../../raw/android-native.md). 기존 앱: `client/android`(minSdk 26, targetSdk 37), 규칙은 [client/android/CLAUDE.md](../../../client/android/CLAUDE.md)와 [docs/architecture](../../../client/android/docs/architecture/).

## 기존 앱의 스택과 규칙 [찰캌규칙]

| 영역 | 기존 앱 |
| --- | --- |
| UI | Compose, Navigation Compose, `XxxRoute`(stateful) / `XxxScreen`(stateless) |
| 상태 | `ViewModel` + `StateFlow`, `collectAsStateWithLifecycle` |
| DI | 수동 컨테이너(`AppContainer`). Hilt 아님 |
| 네트워크 | Retrofit + OkHttp + kotlinx.serialization, `TokenAuthenticator` |
| 저장 | DataStore Preferences |
| 이미지 | Coil |
| 푸시·분석 | Firebase Messaging, Analytics |
| 검사 | ktlint, Konsist(의존 방향, Screen이 ViewModel·NavController를 쓰지 않음), `./gradlew ktlintCheck testDebugUnitTest` |

의존 방향은 `feature → domain ← data`, `feature/data → core`. 디자인 시스템 토큰만 쓰고 dp·색 리터럴을 쓰지 않는다. 새 플랫폼도 이 구조를 [공통 어휘](../00-principles.md#3-공통-어휘)의 기준으로 삼는다.

## Compose 함정

- 상태는 불변 모델을 **교체**한다. `mutableListOf()`나 `var` 프로퍼티 data class는 변경이 관찰되지 않는다. [공식](https://developer.android.com/develop/ui/compose/state)
- Kotlin 2.0.20부터 strong skipping이 기본이다. `@Stable`/`@Immutable`을 경고 무마용으로 붙이면 화면이 갱신되지 않는다. [공식](https://developer.android.com/develop/ui/compose/performance/stability)
- `LaunchedEffect(true)`는 리뷰 대상이다. 컴포지션에 묶일 뿐 수명주기에 묶이지 않는다(Pager의 화면 밖 페이지에서도 돈다). "보일 때만"이면 `LifecycleEventEffect`. [공식](https://developer.android.com/develop/ui/compose/side-effects)
- Lazy 리스트에 `key`(postId)와 `contentType`을 준다. 좋아요 하나 바뀌었다고 목록 전체가 다시 그려지지 않게 한다.
- 성능은 debug가 아니라 release 빌드로 측정한다.

## ViewModel·코루틴 함정

- `init {}`에서 코루틴을 시작하지 않는다. 멱등 `initialize()`나 `stateIn`으로. [공식](https://developer.android.com/topic/architecture/ui-layer/state-production)
- 스트림 노출은 `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)`. 5초는 회전 중 재시작 방지.
- `viewModelScope`는 짧은 작업용. 화면을 떠나도 끝나야 하는 작업은 WorkManager.
- `catch (e: Exception)`이 `CancellationException`을 삼키지 않게 한다. 디스패처는 주입한다(`MainDispatcherRule`).
- 프로세스 사망: ViewModel은 사라지고 saved state는 남는다. 작성 중 사진 경로·편집 값은 `SavedStateHandle`에 ID·경로만 넣는다(큰 객체 금지). "활동 유지 안 함"으로 확인. [공식](https://developer.android.com/topic/libraries/architecture/saving-states)
- `Configuration.screenWidthDp`는 Android 15부터 시스템 바를 빼지 않는다. `WindowMetrics`를 쓴다.

## SDK 버전과 정책

- Play: **2026-08-31부터 신규 앱·업데이트는 targetSdk 36 이상**. [공식](https://support.google.com/googleplay/android-developer/answer/11926878)
- targetSdk 35+: Android 15 기기에서 edge-to-edge 강제. targetSdk 36: opt-out 불가, **predictive back 기본**(`onBackPressed()`가 불리지 않고 `KEYCODE_BACK`도 안 옴). `BackHandler`/`OnBackPressedCallback`만 쓴다. [공식](https://developer.android.com/about/versions/16/behavior-changes-16)
- targetSdk 36: sw600dp 이상에서 방향·크기 제한이 무시되어 재생성이 잦다. targetSdk 37은 opt-out도 불가. 태블릿·폴더블에서 상태 보존 확인. [공식](https://developer.android.com/about/versions/17/behavior-changes-17)
- 16KB 페이지: 네이티브 `.so`가 있을 때만 영향(2027-02-01 이후 미지원 업데이트 불가).
- edge-to-edge: `enableEdgeToEdge()` + `Scaffold` `innerPadding` 적용(무시하면 가려지고 두 번 적용하면 이중 패딩). 하단 시트·캐러셀은 `safeGestures`.

## 사진과 권한

- Photo Picker(`PickVisualMedia(ImageOnly)`)만 쓰고 `READ_MEDIA_IMAGES`를 선언하지 않는다. Play 정책상 시스템 피커로 충분한 앱은 미디어 권한을 요청할 수 없다. [공식](https://support.google.com/googleplay/android-developer/answer/14115180)
- 피커 URI 권한은 앱 종료·재부팅까지만 유효하다. 즉시 앱 캐시로 복사한다.
- Android 13+ `POST_NOTIFICATIONS` 런타임 권한, 알림 채널 필수. FCM SDK가 권한을 선언하지만 요청은 앱 책임. [공식](https://firebase.google.com/docs/cloud-messaging/android/client)

## 푸시

- `notification` 메시지는 백그라운드에서 SDK가 표시하므로 `onMessageReceived`가 불리지 않는다. 탭 처리는 시작 Activity의 `intent.extras`(새 실행)와 `onNewIntent`(기존 Activity) 양쪽에서. 서버는 `click_action`을 지정하지 않는다. [찰캌규칙]
- 제조사 배터리 최적화·강제 종료 상태에서는 수신이 늦거나 안 될 수 있다. 알림함이 진실이다.

## 백그라운드 작업(도입 시)

기존 앱은 업로드를 포그라운드에서 끝내고 WorkManager 복구를 지원하지 않는다. 도입하면:

- `CoroutineWorker` + `NetworkType.CONNECTED` + 지수 백오프(기본 30초) + `enqueueUniqueWork(KEEP)`. 입력 `Data`는 약 10KB 제한이라 파일 경로만 넘긴다. [공식](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started)
- 사용자가 시작한 업로드는 expedited 작업 또는 `setForeground`. Android 15 targetSdk에서 `dataSync` FGS는 24시간에 6시간 제한, `onTimeout` 구현 필요. 백그라운드에서 FGS 시작 불가(API 31+). [공식](https://developer.android.com/develop/background-work/services/fgs/timeout)

## 저장소와 보안

- `EncryptedSharedPreferences`/`MasterKey`(security-crypto)는 전부 deprecated. Keystore AES-GCM 키로 직접 암호화해 DataStore에 저장한다. [공식](https://developer.android.com/privacy-and-security/cryptography)
- DataStore는 파일당 인스턴스 하나(최상위 delegate). 토큰 파일은 `data_extraction_rules.xml`로 백업 제외.
- R8: release(minify ON)로 로그인·피드·업로드 스모크 테스트. kotlinx.serialization은 기본 규칙이 대부분 커버하지만 이름 있는 companion object가 있는 `@Serializable` 클래스는 규칙 추가. 광범위 `-keep class ** { *; }` 금지. [공식](https://developer.android.com/build/shrink-code)

## App Links(도입 시)

- `autoVerify="true"` + `https://<host>/.well-known/assetlinks.json`(Play App Signing 지문). Android 15+는 변경 반영에 최대 7일. 확인은 `adb shell pm get-app-links <패키지>`. [공식](https://developer.android.com/training/app-links/verify-android-applinks)

## 테스트

- ViewModel: `runTest` + 테스트 디스패처 공유, Flow는 Turbine(`awaitItem`), Fake Repository.
- Compose UI: `createComposeRule()`, 시맨틱 기반 finder. Maestro 공용 식별자는 `testTag` + `testTagsAsResourceId`.
- 네트워크 계층: OkHttp `MockWebServer`(기존 앱 사용 중).

## 체크리스트

- [ ] ktlint, Konsist, 단위 테스트 통과
- [ ] edge-to-edge·predictive back·3버튼/제스처 내비 확인(Android 15·16 에뮬레이터)
- [ ] "활동 유지 안 함"에서 작성 중 상태 복원
- [ ] 미디어 권한 미선언, 알림 권한은 맥락 시점 요청
- [ ] release(minify) 빌드 스모크 테스트, mapping 보관
