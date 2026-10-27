---
title: Android 네이티브 앱 개발 (Kotlin / Jetpack Compose) 가이드 - 찰캌 재구현용
topic: Android native app development (Kotlin, Jetpack Compose, 최신 Android/AGP)
collected: 2026-10-27
language: ko
sources:
  - https://developer.android.com/topic/architecture
  - https://developer.android.com/topic/architecture/ui-layer/state-production
  - https://developer.android.com/topic/architecture/ui-layer/events
  - https://developer.android.com/topic/libraries/architecture/saving-states
  - https://developer.android.com/topic/libraries/architecture/coroutines
  - https://developer.android.com/kotlin/coroutines/coroutines-best-practices
  - https://developer.android.com/develop/ui/compose/state
  - https://developer.android.com/develop/ui/compose/side-effects
  - https://developer.android.com/develop/ui/compose/performance/stability
  - https://developer.android.com/develop/ui/compose/performance/bestpractices
  - https://developer.android.com/develop/ui/compose/system/insets
  - https://developer.android.com/develop/ui/views/layout/edge-to-edge
  - https://developer.android.com/about/versions/15/behavior-changes-15
  - https://developer.android.com/about/versions/16/behavior-changes-16
  - https://developer.android.com/about/versions/17/behavior-changes-17
  - https://developer.android.com/guide/navigation/custom-back/predictive-back-gesture
  - https://developer.android.com/training/data-storage/shared/photo-picker
  - https://developer.android.com/about/versions/14/changes/partial-photo-video-access
  - https://support.google.com/googleplay/android-developer/answer/14115180
  - https://support.google.com/googleplay/android-developer/answer/11926878
  - https://developer.android.com/guide/practices/page-sizes
  - https://developer.android.com/develop/ui/views/notifications/notification-permission
  - https://firebase.google.com/docs/cloud-messaging/android/client
  - https://firebase.google.com/docs/cloud-messaging/customize-messages/set-message-type
  - https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started
  - https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work
  - https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/long-running
  - https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
  - https://developer.android.com/training/app-links/verify-android-applinks
  - https://developer.android.com/topic/performance/app-optimization/enable-app-optimization
  - https://developer.android.com/topic/performance/app-optimization/keep-rules-overview
  - https://github.com/Kotlin/kotlinx.serialization/blob/master/README.md
  - https://developer.android.com/training/dependency-injection/hilt-android
  - https://developer.android.com/training/data-storage/room
  - https://developer.android.com/topic/libraries/architecture/datastore
  - https://developer.android.com/jetpack/androidx/releases/security
  - https://developer.android.com/privacy-and-security/keystore
  - https://developer.android.com/training/testing/fundamentals
  - https://developer.android.com/develop/ui/compose/testing
  - https://github.com/cashapp/turbine
  - https://developer.android.com/guide/topics/ui/accessibility/apps
  - https://developer.android.com/topic/performance/vitals/anr
  - https://developer.android.com/topic/performance/memory-overview
  - https://square.github.io/leakcanary/
notes: |
  - 모든 내용은 위 URL을 WebFetch로 직접 읽고 요약한 것이다. 페이지가 다루지 않아 "일반 지식"으로 보강한 부분은 [일반 지식, 미검증]으로 표기했다.
  - 버전/날짜(AGP 9.3, Room 3.0, Play target API 36 등)는 수집일(2026-10-27) 기준이며 변동 가능하므로 구현 시점에 재확인할 것.
---

# Android 네이티브 앱 개발 가이드 (찰캌 Android 재구현)

범용 네트워킹/재시도/클린 아키텍처 이론은 다른 문서에서 다루므로, 여기서는 Android 전용 API와 정책 위주로 정리한다.

---

## 1. 앱 아키텍처 (UI / Domain / Data, UDF, ViewModel, StateFlow, SavedStateHandle)

### 핵심 개념
- 최소 2개 레이어: UI 레이어(데이터 표시) + 데이터 레이어(비즈니스 로직 노출). 도메인 레이어(UseCase)는 복잡하거나 재사용되는 로직이 있을 때만 선택적으로 둔다. ([출처](https://developer.android.com/topic/architecture))
- UDF(단방향 데이터 흐름): 상태는 아래(데이터 소스 -> UI)로, 이벤트는 위(UI -> SSOT)로 흐른다. 데이터 타입마다 단일 소유자(SSOT)가 있고, 불변 타입으로 노출하며 변경 함수/이벤트만 제공한다. 오프라인 우선 앱의 SSOT는 보통 DB. ([출처](https://developer.android.com/topic/architecture))
- Activity/Service/BroadcastReceiver 같은 앱 컴포넌트에는 데이터·비즈니스 로직을 두지 않는다(OS가 언제든 파괴 가능). ([출처](https://developer.android.com/topic/architecture))
- 데이터 레이어 = Repository(데이터 타입당 하나) + DataSource(소스 하나당 하나: 파일/네트워크/DB). ([출처](https://developer.android.com/topic/architecture))
- ViewModel은 화면이 백스택에 있는 동안 유지되는 state holder. ([출처](https://developer.android.com/topic/architecture))

### 공식 가이드라인(권장 방식)
- UI 상태는 `private val _uiState = MutableStateFlow(...)` + `val uiState: StateFlow<...> = _uiState.asStateFlow()`로 노출하고, 변경은 `update { it.copy(...) }`로 원자적으로. ([출처](https://developer.android.com/topic/architecture/ui-layer/state-production))
- 스트림 입력은 `combine(...).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)`. 5초 타임아웃은 구성 변경 중 업스트림이 재시작되지 않게 한다. 화면이 백스택/오프스크린 탭에 남아도 유지해야 하면 `Lazily`. ([출처](https://developer.android.com/topic/architecture/ui-layer/state-production))
- "State is; events happen." ViewModel에서 발생하는 UI 액션은 항상 UI 상태 업데이트로 모델링한다. Channel/SharedFlow로 일회성 이벤트를 노출하는 것은 비권장(생산자가 소비자보다 오래 살 때 전달 보장 불가). 스낵바 같은 메시지는 상태에 담고, 표시 후 `userMessageShown()`처럼 ViewModel에 알려 비운다. ([출처](https://developer.android.com/topic/architecture/ui-layer/events))
- 비즈니스 로직 = ViewModel/도메인/데이터, UI 동작 로직(내비게이션, 스낵바 표시, 확장 여부) = UI. 리스트 아이템에 ViewModel을 넘기지 말고 `onItemClick: (Item) -> Unit` 람다를 호이스팅. 탭으로 인한 내비게이션은 `dropUnlessResumed { }`로 중복 호출 방지. ([출처](https://developer.android.com/topic/architecture/ui-layer/events))
- 상태 보존 3계층: ViewModel(구성 변경 생존, 프로세스 사망 시 소멸) / saved state(프로세스 사망 생존, 사용자 종료 시 소멸) / 영구 저장소. ([출처](https://developer.android.com/topic/libraries/architecture/saving-states))
- `SavedStateHandle`에는 "다시 로드하기 위한 최소 정보"(ID, 짧은 쿼리)만 저장. 비트맵/큰 객체 금지, 복합 데이터는 DB/DataStore에 두고 ID만 저장. 직렬화는 구성 변경 시 메인 스레드에서 일어나므로 무거우면 프레임 드롭. `PersistableBundle` 대신 `Bundle`. ([출처](https://developer.android.com/topic/libraries/architecture/saving-states))
- 비즈니스 로직 상태는 ViewModel + `SavedStateHandle`, UI 로직 상태(스크롤, 입력 중 텍스트)는 `rememberSaveable`/`rememberSerializable`. `SavedStateHandle`은 kotlinx.serialization `saved {}` 확장을 지원. ([출처](https://developer.android.com/topic/libraries/architecture/saving-states))

### 자주 하는 실수 / 놓치기 쉬운 점
- ViewModel `init {}`에서 코루틴 시작 금지(초기화 전 this 유출, Compose State에서는 `IllegalStateException` 가능). 멱등 `initialize()` 사용. ([출처](https://developer.android.com/topic/architecture/ui-layer/state-production))
- `viewModelScope`는 짧은 요청용. 5초 이상 걸리는 작업은 WorkManager로. ([출처](https://developer.android.com/topic/architecture/ui-layer/state-production))
- ViewModel 내 Compose `mutableStateOf`를 백그라운드 스레드에서 갱신할 때는 `Snapshot.withMutableSnapshot { }`로 감싼다. ([출처](https://developer.android.com/topic/architecture/ui-layer/state-production))
- 도메인 레이어를 무조건 만들지 말 것(공식도 선택 사항).
- `ViewModel`을 사용자가 완전히 종료(최근 앱에서 스와이프, `finish()`)하면 saved state도 사라진다. 이는 정상 동작이며 영구 저장소만 남는다. ([출처](https://developer.android.com/topic/libraries/architecture/saving-states))

### 체크리스트
- [ ] 화면당 `UiState` 단일 data class(또는 sealed) + `StateFlow` 노출, Mutable 비공개
- [ ] 일회성 이벤트를 상태로 모델링하고 소비 콜백 제공
- [ ] 프로세스 사망 후에도 복구돼야 하는 값(선택된 주제 ID, 작성 중 텍스트 등)은 `SavedStateHandle`/`rememberSaveable`
- [ ] 큰 데이터는 Bundle에 넣지 않고 ID만 저장

---

## 2. Compose 상태 & 리컴포지션 함정

### 핵심 개념
- `remember`: 컴포지션에 값을 저장. 구성 변경(회전)에서는 사라진다. `rememberSaveable`은 Bundle 저장 가능한 값을 구성 변경/프로세스 재생성에서 복원(사용자가 완전히 종료하면 사라짐). Bundle에 못 넣는 타입은 `@Parcelize`, `mapSaver`, `listSaver`. ([출처](https://developer.android.com/develop/ui/compose/state))
- 상태 호이스팅: `value: T` + `onValueChange: (T) -> Unit`로 stateless 컴포저블 구성. 읽는 모든 컴포저블의 최소 공통 부모 이상, 변경 가능한 가장 높은 수준 이상에 두고, 같은 이벤트로 바뀌는 두 상태는 함께 호이스팅. ([출처](https://developer.android.com/develop/ui/compose/state))
- 안정성(stability): 불변이거나 변경을 Compose가 알 수 있는 타입이 stable. `var` 프로퍼티를 가진 data class는 unstable. Kotlin 2.0.20부터 strong skipping이 기본: stable 파라미터는 `equals()`, unstable은 `===`로 비교하여 스킵 판단. 컴포저가 없는 모듈의 클래스는 unstable로 취급. ([출처](https://developer.android.com/develop/ui/compose/performance/stability))
- `List/Set/Map`은 unstable이지만 strong skipping에서는 같은 참조면 스킵. 다만 공식 문서는 kotlinx immutable 컬렉션이나 `@Immutable` 남발이 오히려 요소별 `equals()` 비교 비용을 만들 수 있다고 경고. ([출처](https://developer.android.com/develop/ui/compose/performance/stability))

### 공식 가이드라인(권장 방식)
- Android에서 Flow 수집은 `collectAsStateWithLifecycle()`(`androidx.lifecycle:lifecycle-runtime-compose`). 기본 STARTED 이상일 때만 수집하며 `minActiveState`로 변경 가능. `collectAsState()`는 플랫폼 비의존용으로 라이프사이클을 고려하지 않는다. ([출처](https://developer.android.com/develop/ui/compose/state), [출처](https://developer.android.com/topic/libraries/architecture/coroutines))
- 부수 효과 API 선택: ([출처](https://developer.android.com/develop/ui/compose/side-effects))
  - `LaunchedEffect(keys)`: 컴포지션 진입 시 코루틴 시작, 키 변경 시 재시작, 이탈 시 취소.
  - `rememberCoroutineScope`: 클릭 같은 이벤트 핸들러에서 launch.
  - `rememberUpdatedState`: 오래 사는 이펙트가 재시작 없이 최신 콜백/값을 참조.
  - `DisposableEffect`: 리스너 등록/해제(`onDispose` 필수). 변하는 의존성(`lifecycleOwner`)은 키로.
  - `SideEffect`: 리컴포지션 성공 후 Compose 상태를 비Compose 코드(분석 등)에 게시.
  - `produceState`, `snapshotFlow`(cold, `LaunchedEffect`에서 수집), `derivedStateOf`.
  - 규칙: 이펙트 블록에서 쓰는 모든 변수는 키이거나 `rememberUpdatedState`로 감싼다.
- 성능 베스트 프랙티스: ([출처](https://developer.android.com/develop/ui/compose/performance/bestpractices))
  - 비싼 계산은 `remember(inputs)`로, 가능하면 ViewModel로 이동.
  - `LazyColumn`/`LazyVerticalGrid`의 `items`에 안정적 `key`(ID)와 `contentType` 지정. 인덱스/`hashCode()` 금지.
  - 상태 읽기 지연: `Modifier.offset { }`, `drawBehind { }` 같은 람다 변형자로 레이아웃/드로우 단계에서 읽기.
  - 백워드 라이트(이미 읽은 상태를 같은 컴포지션에서 쓰기) 금지.
  - 성능은 release 빌드에서 측정(디버그는 왜곡).
- 컨테이너 상태 보존: 사진 피드처럼 목록이 큰 화면은 `rememberLazyListState()`가 `rememberSaveable` 기반이라 회전 시 스크롤 복원 [일반 지식, 미검증].

### 자주 하는 실수 / 놓치기 쉬운 점
- `ArrayList`/`mutableListOf()`/가변 data class를 상태로 쓰면 변경이 관찰되지 않아 stale UI. 불변 리스트를 교체하는 방식으로. ([출처](https://developer.android.com/develop/ui/compose/state))
- `LaunchedEffect(true)`는 "while(true)만큼 수상하다" - 정말 호출 위치 수명에 맞출 때만. ([출처](https://developer.android.com/develop/ui/compose/side-effects))
- `rememberCoroutineScope`를 컴포지션 시작 작업에 쓰는 오용 -> `LaunchedEffect` 사용. ([출처](https://developer.android.com/develop/ui/compose/side-effects))
- `derivedStateOf`를 같은 빈도로 바뀌는 값 합성(`"$first $last"`)에 쓰면 순수 오버헤드. ([출처](https://developer.android.com/develop/ui/compose/side-effects))
- 컴포저블 본문에서 직접 부수 효과 실행(리컴포지션이 중단/폐기될 수 있음). ([출처](https://developer.android.com/develop/ui/compose/side-effects))
- `LaunchedEffect`는 컴포지션에 묶이지 Activity 라이프사이클에 묶이지 않는다. `HorizontalPager`의 화면 밖 페이지에서도 돈다. "보일 때만" 필요한 효과는 `LifecycleEventEffect` 등 사용. ([출처](https://developer.android.com/topic/libraries/architecture/coroutines))
- 키로 큰 컬렉션을 `remember`에 넘기면 매 리컴포지션 `equals()` 비용. ([출처](https://developer.android.com/develop/ui/compose/performance/bestpractices))
- `@Stable/@Immutable`을 컴파일러 경고 무마용으로 붙이면 stale UI. ([출처](https://developer.android.com/develop/ui/compose/performance/stability))
- 뷰(Views) 코드에서 `lifecycleScope.launch { flow.collect }`는 백그라운드에서도 수집이 지속된다. `repeatOnLifecycle(STARTED)` 사용 [일반 지식, 미검증; 공식 Views 페이지 참고].

### 체크리스트
- [ ] Flow는 `collectAsStateWithLifecycle()`로 수집
- [ ] Lazy 리스트는 `key`/`contentType` 지정
- [ ] 상태는 불변 모델 + 교체 방식, `var` 프로퍼티 data class 지양
- [ ] 이펙트 키/`rememberUpdatedState` 점검, `LaunchedEffect(true)` 코드리뷰 대상
- [ ] 성능 이슈는 release + 컴파일러 리포트로 확인

---

## 3. Kotlin Coroutines & Flow 베스트 프랙티스

### 핵심 개념
- 구조적 동시성: 코루틴은 스코프에 속하고 스코프가 취소되면 자식도 취소. 화면 수명 작업은 `coroutineScope`/`supervisorScope`로 호출자와 함께 취소, 화면보다 오래 살아야 하는 작업(예: 북마크)은 `Application`이나 상위 스코프에서 주입받은 외부 `CoroutineScope`를 사용. ([출처](https://developer.android.com/kotlin/coroutines/coroutines-best-practices))
- 취소는 협력적: 블로킹 루프에서는 `ensureActive()`로 확인. `withContext`, `delay` 등 kotlinx 중단 함수는 이미 취소 가능. ([출처](https://developer.android.com/kotlin/coroutines/coroutines-best-practices))

### 공식 가이드라인(권장 방식)
1. Dispatcher를 하드코딩하지 말고 주입(테스트에서 교체). `viewModelScope`는 `Dispatchers.Main` 고정이므로 테스트에서 `Dispatchers.setMain`. ([출처](https://developer.android.com/kotlin/coroutines/coroutines-best-practices))
2. suspend 함수는 main-safe로: 블로킹 작업을 가진 클래스가 내부에서 `withContext`로 스레드 이동. 호출자는 디스패처를 몰라도 되게. ([출처](https://developer.android.com/kotlin/coroutines/coroutines-best-practices))
3. 코루틴은 View가 아니라 ViewModel이 생성. 단일 값이면 ViewModel의 suspend 함수도 허용. ([출처](https://developer.android.com/kotlin/coroutines/coroutines-best-practices))
4. 가변 타입(`MutableStateFlow`)을 외부에 노출 금지. ([출처](https://developer.android.com/kotlin/coroutines/coroutines-best-practices))
5. 데이터/도메인 레이어는 일회성은 suspend 함수, 시간에 따른 변화는 `Flow`로 노출해 호출자가 실행/취소를 제어. ([출처](https://developer.android.com/kotlin/coroutines/coroutines-best-practices))
6. `GlobalScope` 금지(스코프/디스패처 하드코딩, 테스트 어려움). ([출처](https://developer.android.com/kotlin/coroutines/coroutines-best-practices))
7. 예외: `viewModelScope`/`lifecycleScope`에서 시작한 코루틴의 예상 가능한 예외는 `IOException`처럼 구체 타입으로 catch. `Exception`/`Throwable` 광범위 catch 지양, `CancellationException`은 삼키지 말고 재던지기. ([출처](https://developer.android.com/kotlin/coroutines/coroutines-best-practices))
8. 테스트: `StandardTestDispatcher`(큐잉, `advanceUntilIdle`)/`UnconfinedTestDispatcher`(즉시 실행), `runTest`의 `testScheduler`를 공유해 결정적 테스트. ([출처](https://developer.android.com/kotlin/coroutines/coroutines-best-practices))
- `viewModelScope`는 ViewModel clear 시 자동 취소. 필요하면 스코프를 생성자 주입. ([출처](https://developer.android.com/topic/libraries/architecture/coroutines))

### Android 특화 보충
- `CoroutineExceptionHandler`는 `launch`로 시작한 루트 코루틴의 "처리되지 않은 예외"에만 작동하며 `async`의 예외는 `await()`에서 던져진다. 구조적 동시성상 자식 실패는 부모로 전파되므로, 개별 실패 격리가 필요하면 `supervisorScope` 사용 [일반 지식, 미검증: kotlinlang.org 코루틴 예외 문서(https://kotlinlang.org/docs/exception-handling.html)를 구현 시 확인; 본 조사에서는 직접 fetch하지 않음].
- 사진 업로드처럼 화면을 떠나도 완료돼야 하는 작업은 `viewModelScope`가 아니라 WorkManager(6장)로 이관. ([출처](https://developer.android.com/topic/architecture/ui-layer/state-production))

### 자주 하는 실수
- `catch (e: Exception)`이 `CancellationException`을 삼켜 취소가 멈춘다. (위 7번)
- 루프 안 CPU/IO 작업에서 취소 체크 누락.
- 테스트에서 여러 dispatcher가 서로 다른 scheduler를 쓴다(`testScheduler` 미공유).
- 외부 스코프에서 `launch` 후 `join()` 없이 반환하면 호출자가 완료를 기다리지 못함(공식 예시는 `.join()`). ([출처](https://developer.android.com/kotlin/coroutines/coroutines-best-practices))

### 체크리스트
- [ ] `@IoDispatcher` 등 Qualifier로 디스패처 주입
- [ ] Repository 함수는 main-safe
- [ ] `GlobalScope` 사용 금지(lint/리뷰)
- [ ] `CancellationException` 재던지기 규칙 팀 컨벤션화

---

## 4. Activity/프로세스 라이프사이클, 구성 변경, 프로세스 사망

### 핵심 개념
- 구성 변경(회전, 멀티윈도우, 리사이즈): Activity 파괴·재생성. ViewModel과 saved state 모두 생존.
- 시스템에 의한 프로세스 사망(백그라운드에서 kill): ViewModel 소실, saved state 생존.
- 사용자 완전 종료: 영구 저장소만 생존. ([출처](https://developer.android.com/topic/libraries/architecture/saving-states))
- saved state는 Activity가 stop될 때 기록된다(stop 전 변경은 다음 stop까지 지연). ([출처](https://developer.android.com/topic/libraries/architecture/saving-states))

### 공식 가이드라인 / 최신 변경
- Android 16(API 36) 타깃부터 sw600dp 이상(태블릿/폴더블)에서 `screenOrientation`, `resizableActivity`, 비율 제한이 무시되어 회전·리사이즈에 의한 Activity 재생성이 더 잦아진다. 상태 보존을 제대로 해야 한다. Android 17 타깃은 opt-out 불가. ([출처](https://developer.android.com/about/versions/16/behavior-changes-16), [출처](https://developer.android.com/about/versions/17/behavior-changes-17))
- 프로세스 사망 테스트: 개발자 옵션 "Don't keep activities"로 Activity 파괴를 재현 [일반 지식, 미검증; 공식 saving-states 페이지에는 설명 없음].

### 자주 하는 실수
- 업로드 중인 선택 이미지 URI를 ViewModel 메모리에만 두기 -> 프로세스 사망 시 소실. 임시 저장(Room/파일) 또는 `SavedStateHandle`(URI 문자열)와 WorkManager 입력으로.
- 화면 크기별 레이아웃을 `Configuration.screenWidthDp`로 판단 -> Android 15부터 시스템바 미제외 값. `WindowInsets`/`WindowMetricsCalculator` 사용. ([출처](https://developer.android.com/about/versions/15/behavior-changes-15))

### 체크리스트
- [ ] "Don't keep activities" ON 상태로 주요 플로우(글쓰기/사진 선택/로그인) 수동 테스트
- [ ] 회전/폴더블 크기 변경에서 입력 중 텍스트·선택 사진 유지

---

## 5. 이미지 선택: Photo Picker & 런타임 권한 (Android 14+ 부분 접근)

### 핵심 개념
- Photo Picker: 사용자가 선택한 항목에만 앱이 접근하며, 저장소 권한이 필요 없다(접근 모델상 암시; 문서는 별도 선언 없음). `androidx.activity` 1.7.0+의 `PickVisualMedia`/`PickMultipleVisualMedia(maxItems)`. 사용 불가 시 `ACTION_OPEN_DOCUMENT` 폴백(이때 maxItems 무시). Android 11(API 30)+ 네이티브, API 19~29 및 일부 Go 기기는 Google Play 서비스 백포트(매니페스트에 `ModuleDependencies` 서비스 선언 필요). ([출처](https://developer.android.com/training/data-storage/shared/photo-picker))
- Android 14(API 34) 부분 사진 접근(Selected Photos Access): 사용자가 일부만 허용 가능. `READ_MEDIA_VISUAL_USER_SELECTED` 선언 여부에 따라 앱 제어/시스템 제어(호환 모드). ([출처](https://developer.android.com/about/versions/14/changes/partial-photo-video-access))
- Play 정책: API 33+ 타깃 앱은 시스템 피커(Photo Picker)로 핵심 기능을 제공할 수 없을 때만 `READ_MEDIA_IMAGES/VIDEO` 요청 가능. 커스텀 피커라는 이유만으로 허용되지 않으며 Play Console 선언 필요. 2025-05-28부터 전면 의무. ([출처](https://support.google.com/googleplay/android-developer/answer/14115180))

### 공식 가이드라인(권장 방식)
- 찰캌처럼 "사진을 골라 올리는" 앱은 Photo Picker만 사용하고 미디어 권한은 선언하지 않는 것이 정책상 가장 안전. ([출처](https://support.google.com/googleplay/android-developer/answer/14115180), [출처](https://developer.android.com/training/data-storage/shared/photo-picker))
- Compose: `rememberLauncherForActivityResult(PickVisualMedia())` + `PickVisualMediaRequest(PickVisualMedia.ImageOnly)`. 다중 선택은 `PickMultipleVisualMedia(5)`; 플랫폼 상한은 `MediaStore.getPickImagesMaxLimit()`. ([출처](https://developer.android.com/training/data-storage/shared/photo-picker))
- 선택한 URI 접근은 기본적으로 기기 재부팅/앱 종료까지만 유효. 지연 업로드(WorkManager)에 쓰려면 `takePersistableUriPermission()`을 호출하거나 앱 내부 캐시로 복사(앱당 최대 5,000개 grant). ([출처](https://developer.android.com/training/data-storage/shared/photo-picker))
- 자체 갤러리 UI가 불가피한 경우 매니페스트: `READ_EXTERNAL_STORAGE`(maxSdk 32), `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`, `READ_MEDIA_VISUAL_USER_SELECTED`. 권한은 API 34/33/그 이하 분기로 한 번에 요청. 상태 확인은 `checkSelfPermission`으로 매번(저장하지 말 것), `onResume`에서 재확인, 필요한 시점에 요청, 필요 미디어 타입만 요청. ([출처](https://developer.android.com/about/versions/14/changes/partial-photo-video-access))
- Android 16: 사용자가 미디어 접근을 제한할 때 앱이 소유한 사진은 피커에서 미리 선택된 상태(API 36 타깃). ([출처](https://developer.android.com/about/versions/16/behavior-changes-16))

### 자주 하는 실수
- 권한 상태를 DataStore/SharedPreferences에 캐시 -> 권한 리셋/하이버네이션/설정 변경으로 불일치. ([출처](https://developer.android.com/about/versions/14/changes/partial-photo-video-access))
- 이전에 접근 가능했던 URI가 나중에 읽히지 않을 수 있음을 처리하지 않음; 빈 라이브러리를 "미디어 없음"으로 오해. ([출처](https://developer.android.com/about/versions/14/changes/partial-photo-video-access))
- 앱 시작 시 일괄 권한 요청(맥락 없음).
- 피커 반환 URI의 EXIF 위치정보 접근은 `ACCESS_MEDIA_LOCATION`이 필요할 수 있음 [일반 지식, 미검증]. 업로드 전 EXIF GPS 제거 정책은 백엔드/보안 팀과 합의.

### 체크리스트
- [ ] 업로드 UX는 Photo Picker 전용, `READ_MEDIA_*` 미선언 유지
- [ ] 선택 URI는 즉시 앱 캐시로 복사 후 업로드(또는 persistable 권한)
- [ ] 다중 선택 상한 정책 정의(`PickMultipleVisualMedia(maxItems)`)

---

## 6. WorkManager로 신뢰성 있는 업로드 & Foreground Service 제약

### 핵심 개념
- WorkManager: 정확한 시각이 필요 없지만 반드시 완료돼야 하는 작업(이미지 업로드, 동기화). 시스템이 제약 조건과 최적화에 따라 실행 시점 결정. `Worker`/`CoroutineWorker`, 결과 `success/failure/retry`. ([출처](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started))
- 제약: `NetworkType`, `BatteryNotLow`, `RequiresCharging`, `DeviceIdle`, `StorageNotLow`. 실행 중 제약이 깨지면 중단 후 재시도. 백오프 기본 EXPONENTIAL 30초, 최소 10초(`MIN_BACKOFF_MILLIS`). ([출처](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work))

### 공식 가이드라인(권장 방식)
- 사용자가 시작한 즉시성 있는 짧은 작업은 expedited(`setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)`). 쿼터는 포그라운드에서는 적용되지 않음. Android 12 미만에서는 포그라운드 서비스로 실행될 수 있어 `getForegroundInfo()` 구현 필요(없으면 크래시). `setForeground()`는 `IllegalStateException`(Android 12+는 `ForegroundServiceStartNotAllowedException`)을 try/catch. ([출처](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work))
- 장시간(10분 이상) 업로드/다운로드: `CoroutineWorker` + `setForeground(ForegroundInfo)`. 진행률은 `setForeground`를 다시 호출해 알림 갱신. 취소 액션은 `WorkManager.createCancelPendingIntent(id)`. API 34 타깃은 foreground service type 필수: 매니페스트에 `SystemForegroundService`를 `tools:node="merge"`로 `foregroundServiceType` 선언 + `ForegroundInfo` 생성 시 타입 전달. 업로드/다운로드에는 일반적으로 `dataSync` 계열이 맞으나 문서는 직접 명시하지 않으므로 FGS 타입 문서로 확인. ([출처](https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/long-running))
- 유니크 작업(`enqueueUniqueWork` + `ExistingWorkPolicy`)으로 중복 업로드 방지, `Data` 입력 크기는 약 10KB 제한이라 큰 페이로드는 DB/파일 경로로 전달 [문서 확인 권장: define-work 페이지는 "약 10KB, API 레퍼런스 확인"이라고만 언급]. ([출처](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work))
- 업로드 같은 재시도는 서버 멱등키와 결합(범용 재시도 설계는 타 문서 참고).

### Foreground Service 제약
- API 31+ 타깃은 백그라운드에서 FGS 시작 불가(`ForegroundServiceStartNotAllowedException`). 예외: 사용자 보이는 상태에서의 전환, 알림/위젯/버블 상호작용, 정확 알람, `BOOT_COMPLETED`(단 Android 14+/15+ 추가 제한), 그리고 high-priority FCM 수신(`RemoteMessage.getPriority()`가 `PRIORITY_HIGH`인지 확인; 시스템이 강등할 수 있음). ([출처](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start))
- Android 14: 서비스 유형별 필요 권한 확인, while-in-use 권한(카메라/위치/마이크)은 백그라운드 생성 시 `SecurityException`. ([출처](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start))
- Android 15 타깃: `dataSync`/`mediaProcessing` FGS는 24시간당 총 6시간. 초과 시 `Service.onTimeout()` 호출 후 몇 초 내 `stopSelf()` 안 하면 `RemoteServiceException`. 권장 대안은 WorkManager. `BOOT_COMPLETED` 수신기에서 `dataSync` 등 일부 유형 FGS 시작 불가. ([출처](https://developer.android.com/about/versions/15/behavior-changes-15))
- Android 16: WorkManager는 JobScheduler 기반이라 장시간 워커가 job 쿼터를 소진할 수 있음. 대안으로 직접 FGS 또는 user-initiated data transfer(UIDT) job(일반 job 쿼터 면제). ([출처](https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/long-running))
- Android 17: 백그라운드 오디오는 FGS 필요(오디오 앱 아님, 참고). ([출처](https://developer.android.com/about/versions/17/behavior-changes-17))

### 자주 하는 실수
- `viewModelScope`/`GlobalScope`에서 업로드 -> 화면 이탈/프로세스 사망 시 유실.
- 선택 URI의 임시 권한 만료(5장) 상태로 Worker 지연 실행.
- Android 15에서 `dataSync` FGS 타임아웃 `onTimeout` 미구현.
- 알림 권한 거부 시에도 FGS 자체는 실행되지만 알림이 드로어에 보이지 않는다(작업 관리자에는 표시). ([출처](https://developer.android.com/develop/ui/views/notifications/notification-permission))

### 체크리스트
- [ ] 업로드 = `CoroutineWorker` + 네트워크 제약 + 지수 백오프 + 유니크 작업
- [ ] 큰 페이로드는 파일/DB 경로로 전달
- [ ] `getForegroundInfo()` 구현, FGS 타입 매니페스트 선언
- [ ] 진행률 UI는 `WorkInfo` Flow 관찰, 취소 PendingIntent 제공

---

## 7. POST_NOTIFICATIONS (Android 13+) & FCM

### 핵심 개념
- API 33+에서 `POST_NOTIFICATIONS` 런타임 권한. 매니페스트 선언 필요. Android 13 이상 타깃이면 요청 시점을 앱이 제어. 12L 이하 타깃이면 채널 생성 후 첫 Activity 시작 시 시스템이 자동 프롬프트. ([출처](https://developer.android.com/develop/ui/views/notifications/notification-permission))
- FCM SDK(23.0.6+)가 매니페스트에 권한을 선언하지만 런타임 요청은 앱 책임. 권한 승인 전에는 알림 표시 불가. ([출처](https://firebase.google.com/docs/cloud-messaging/android/client))

### 공식 가이드라인(권장 방식)
- 첫 실행에 묻지 말고 맥락 있는 사용자 행동(알림 벨 탭, 팔로우 등) 또는 이후 실행에서 요청. `shouldShowRequestPermissionRationale()`이 true면 설명 UI 먼저. 발송 전 `areNotificationsEnabled()` 확인. 스와이프로 닫으면 상태 불변. ([출처](https://developer.android.com/develop/ui/views/notifications/notification-permission))
- 업그레이드 앱은 조건 충족 시(기존 채널 존재, 사용자가 명시적으로 끄지 않음) 자동 부여. ([출처](https://developer.android.com/develop/ui/views/notifications/notification-permission))
- FCM 클라이언트: `FirebaseMessagingService`를 `com.google.firebase.MESSAGING_EVENT` 인텐트 필터로 선언, 기본 아이콘/색/채널 ID 메타데이터 설정. `onNewToken`은 최초 생성 및 변경(복원, 재설치, 데이터 삭제) 시 호출 -> 서버로 전송. 문서는 Firebase Installation ID(FID) 사용을 권장하고 레지스트리 토큰은 향후 제거 예정(deprecated)이라 명시. Android 12L 이하에서는 권한 부여 전 수신 알림이 유실될 수 있어 채널을 일찍 생성하거나 13+ 타깃. 자동 초기화를 껐다면 권한 승인 후 재활성화. Play 서비스 호환성은 `onCreate`/`onResume`에서 확인. ([출처](https://firebase.google.com/docs/cloud-messaging/android/client))
- 메시지 유형: notification 메시지는 백그라운드에서 SDK가 트레이에 표시, 포그라운드에서는 앱 콜백 처리. data 메시지는 앱이 포·백그라운드 모두 처리. 둘 다 포함하면 백그라운드에서 알림은 트레이에 뜨고 data는 사용자가 탭할 때만 전달. ([출처](https://firebase.google.com/docs/cloud-messaging/customize-messages/set-message-type))
- 찰캌 푸시(좋아요/사인/AI 리뷰 완료)는 사용자 가시 알림이므로 notification(+data) 메시지 + 딥링크 데이터 권장 [권장안; 최종 설계는 백엔드와 합의].

### 자주 하는 실수
- 앱 시작 즉시 권한 요청 -> 거부율 상승 및 "다시 묻지 않음" 상태.
- 토큰 서버 등록을 로그인 전후 어느 쪽에서도 보장하지 않음(로그아웃 시 토큰 무효화/해제 필요) [일반 지식, 미검증].
- data 메시지에서 `onMessageReceived` 내 무거운 작업 수행 -> 짧게 끝내고 WorkManager로. [일반 지식, 미검증: 시간 제한 수치는 본 조사에서 확인 못함]
- 알림 채널 미생성(Android 8+에서 알림 미표시).

### 체크리스트
- [ ] `POST_NOTIFICATIONS` 요청은 맥락 있는 시점 + 거부 후 설정 화면 유도 UI
- [ ] `onNewToken`/FID 서버 동기화, 로그아웃 시 해제
- [ ] 알림 채널(예: 좋아요, 사인, AI 리뷰) 사전 생성
- [ ] 푸시 탭 -> App Links/딥링크로 해당 게시물 이동

---

## 8. App Links

### 핵심 개념
- `android:autoVerify="true"` + `VIEW`/`BROWSABLE`/`DEFAULT` + `http`/`https` 스킴 인텐트 필터. 설치 시 각 호스트의 `https://<host>/.well-known/assetlinks.json`(Digital Asset Links)을 검증. 설치 후 최소 20초 대기. ([출처](https://developer.android.com/training/app-links/verify-android-applinks))
- Android 11 이하는 매니페스트의 모든 호스트가 검증돼야 기본 핸들러가 된다. 한 도메인에는 기기당 한 앱만 연결. ([출처](https://developer.android.com/training/app-links/verify-android-applinks))

### 공식 가이드라인 / 디버깅
- 확인: `adb shell pm get-app-links PACKAGE`, 재검증: `adb shell pm set-app-links --package PACKAGE 0 all` 후 `adb shell pm verify-app-links --re-verify PACKAGE`(Android 12+). 상태값: `verified`, `none`, `1024+`(기기 고유 오류) 등. 실패 시 `Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS`로 사용자 연결 유도. ([출처](https://developer.android.com/training/app-links/verify-android-applinks))
- Android 15+는 assetlinks.json 변경이 주기적 재검증으로 최대 7일 걸려 반영, 14 이하는 설치/업데이트 시에만. ([출처](https://developer.android.com/training/app-links/verify-android-applinks))

### 자주 하는 실수
- 디버그/릴리스 서명 지문 불일치(assetlinks에 Play App Signing 인증서 지문 필요) [일반 지식, 미검증].
- 리다이렉트되는 assetlinks.json, 잘못된 Content-Type, 서버 캐싱 지연으로 검증 실패. (캐싱 지연은 공식 언급) ([출처](https://developer.android.com/training/app-links/verify-android-applinks))
- 딥링크 인텐트의 파라미터를 검증 없이 신뢰(인증/권한 우회) [일반 지식].

### 체크리스트
- [ ] 공유 링크 도메인에 assetlinks.json 배포(서명 지문 정확)
- [ ] 출시 빌드에서 `pm get-app-links` 로 `verified` 확인
- [ ] 비로그인/미설치 시 웹 폴백

---

## 9. Edge-to-edge 강제 (Android 15+), Predictive back, Target SDK

### 핵심 개념
- API 35 타깃 + Android 15 기기: 앱은 기본 edge-to-edge. 상태바/내비바 뒤로 그려지며 인셋 처리가 없으면 콘텐츠가 가려진다. `setStatusBarColor`, `setNavigationBarColor`(제스처 내비), `setDecorFitsSystemWindows` 등은 deprecated/비활성. 3버튼 내비는 80% 불투명 스크림. 디스플레이 컷아웃 `SHORT_EDGES/NEVER/DEFAULT`는 `ALWAYS`로 취급. ([출처](https://developer.android.com/about/versions/15/behavior-changes-15))
- API 36 타깃: `windowOptOutEdgeToEdgeEnforcement` opt-out 속성이 비활성화되어 선택 불가(Android 15 기기 + 35 타깃에서는 여전히 동작). ([출처](https://developer.android.com/about/versions/16/behavior-changes-16))
- Predictive back: `OnBackPressedCallback`(AndroidX Activity 1.6.0+) 사용. Android 16(API 36) 타깃은 predictive back이 기본 활성이며 `onBackPressed()`가 호출되지 않고 `KEYCODE_BACK`도 전달되지 않는다. 임시 opt-out은 `android:enableOnBackInvokedCallback="false"`. ([출처](https://developer.android.com/about/versions/16/behavior-changes-16), [출처](https://developer.android.com/guide/navigation/custom-back/predictive-back-gesture))

### 공식 가이드라인(권장 방식)
- Compose: Activity `onCreate`에서 `enableEdgeToEdge()`, Material3 `Scaffold`의 `innerPadding`을 콘텐츠(또는 Lazy 리스트 `contentPadding`)에 적용. 인셋 종류: `safeDrawing`(가장 흔함), `safeGestures`, `safeContent`, `ime`. ([출처](https://developer.android.com/develop/ui/compose/system/insets), [출처](https://developer.android.com/about/versions/15/behavior-changes-15))
  - 보충 [일반 지식, 미검증: 해당 페이지가 직접 다루지 않음]: 중복 패딩은 `consumeWindowInsets`/중첩 Scaffold의 `contentWindowInsets`로 방지, 키보드는 `imePadding()`.
- Views: AppBarLayout는 자동 처리 안 됨(`fitsSystemWindows`), 스크롤 콘텐츠는 `clipToPadding=false` + 시스템바 인셋 패딩. ([출처](https://developer.android.com/develop/ui/views/layout/edge-to-edge))
- 풀스크린 이미지 뷰어(상세 보기)는 `WindowInsetsControllerCompat`으로 시스템바 숨김/표시, `*IgnoringVisibility` 인셋. ([출처](https://developer.android.com/develop/ui/views/layout/edge-to-edge), [출처](https://developer.android.com/develop/ui/compose/system/insets))
- 뒤로 가기 처리: Compose에서는 `BackHandler`/`PredictiveBackHandler`(activity-compose 1.8.0+). 콜백 활성 여부는 관찰 가능한 상태로 구동(핸들러 안 조건 분기 금지). UI 로직 전용, 비즈니스 로직·로깅은 콜백이 이벤트를 소비하므로 부적절(Android 16+는 `PRIORITY_SYSTEM_NAVIGATION_OBSERVER` 사용). ([출처](https://developer.android.com/guide/navigation/custom-back/predictive-back-gesture))
- `android:enableOnBackInvokedCallback`는 앱/Activity 단위 설정, 마이그레이션 시 화면 단위 점진 적용 가능. ([출처](https://developer.android.com/guide/navigation/custom-back/predictive-back-gesture))

### Google Play Target SDK / 정책 (수집일 2026-10-27 기준)
- 2026-08-31부터 신규 앱과 업데이트는 모바일 기준 Android 16(API 36) 이상 타깃 필수. 기존 앱은 신규 사용자 노출 유지를 위해 API 35 이상. 연장은 2026-11-01까지 요청 가능(Play Console 양식). 해당 도움말의 일부 표에 날짜가 혼재하므로 Play Console의 안내를 최종 기준으로 확인. ([출처](https://support.google.com/googleplay/android-developer/answer/11926878))
- 16KB 페이지 크기: API 35+ 타깃 앱은 64비트 기기에서 16KB 지원 필요, 2027-02-01 이후 미지원 업데이트는 출시 불가. 네이티브(.so) 라이브러리를 쓰는 경우에만 영향(순수 Kotlin/Java는 이미 지원). AGP 8.5.1+ 및 NDK r28+면 기본 호환. APK Analyzer/`zipalign -c -P 16 -v 4`로 확인. ([출처](https://developer.android.com/guide/practices/page-sizes))
- Android 17(API 37) 타깃 변화 중 찰캌 관련: 대형 화면 방향/크기 제한 opt-out 불가, ECH/CT 기본, 로컬 네트워크 권한 등. ([출처](https://developer.android.com/about/versions/17/behavior-changes-17))

### 자주 하는 실수
- `Scaffold`의 `innerPadding` 무시 -> 바에 가려짐. 또는 이중 적용 -> 이중 패딩.
- `Configuration.screenWidthDp/HeightDp`가 Android 15부터 시스템바를 제외하지 않음. ([출처](https://developer.android.com/about/versions/15/behavior-changes-15))
- 구형 `onBackPressed()`/`KEYCODE_BACK` 가로채기 유지 -> API 36 타깃에서 동작 안 함. ([출처](https://developer.android.com/about/versions/16/behavior-changes-16))
- 스플래시 화면 크래시(Android 15): core-splashscreen 1.2.0-alpha01+ 또는 컷아웃 모드 조정. ([출처](https://developer.android.com/about/versions/15/behavior-changes-15))
- 하단 시트/캐러셀이 시스템 제스처와 충돌 -> `safeGestures`. ([출처](https://developer.android.com/develop/ui/compose/system/insets))

### 체크리스트
- [ ] targetSdk 36, compileSdk 최신, 신규 규칙 테스트(Android 15/16 에뮬레이터)
- [ ] 모든 화면 edge-to-edge + 인셋 처리, 키보드/컷아웃/제스처 내비 점검
- [ ] 뒤로 가기는 `BackHandler`/`PredictiveBackHandler`만 사용
- [ ] 네이티브 라이브러리 유무 확인 후 16KB 정렬 검증

---

## 10. R8 / ProGuard (Retrofit, Gson, kotlinx.serialization, 리플렉션)

### 핵심 개념
- R8은 릴리스 빌드에서 코드 제거·이름 변경·최적화. 리플렉션/JNI로만 접근되는 코드는 R8이 볼 수 없어 제거/난독화될 수 있고, 런타임에 `ClassNotFoundException`/`NoSuchMethodException`을 유발. ([출처](https://developer.android.com/topic/performance/app-optimization/keep-rules-overview))
- 최신 AGP(9.3+)는 `optimization { enable = true }` 새 DSL, 규칙은 `src/<variant>/keepRules/*.keep`. 9.3 미만은 `isMinifyEnabled = true`, `isShrinkResources = true`, `proguard-android-optimize.txt`. AGP 9.0부터 `proguard-android.txt`(`-dontoptimize` 포함) 미지원. R8 full mode는 AGP 8.0부터 기본. ([출처](https://developer.android.com/topic/performance/app-optimization/enable-app-optimization))

### 공식 가이드라인(권장 방식)
- keep 규칙은 최대한 좁게(광범위 규칙은 최적화를 막음). 라이브러리 consumer rule은 병합됨. 자체 리플렉션 코드는 Jetpack Keep 어노테이션으로 규칙 자동 생성 가능. 리플렉션이 계속 문제되는 라이브러리는 교체 고려. R8 Configuration Analyzer(AGP 9.3 계열)로 과도한 규칙 탐지. ([출처](https://developer.android.com/topic/performance/app-optimization/keep-rules-overview), [출처](https://developer.android.com/topic/performance/app-optimization/enable-app-optimization))
- kotlinx.serialization: 라이브러리 기본 규칙이 대부분 커버하나 "이름 있는 companion object"를 가진 `@Serializable` 클래스는 커스텀 규칙(InnerClasses 속성 유지, `$$serializer` 및 `INSTANCE` 유지 등) 필요. ([출처](https://github.com/Kotlin/kotlinx.serialization/blob/master/README.md))
- Retrofit/Gson: 본 조사에서 공식 R8 규칙 페이지를 확인하지 못했다(Retrofit 소개 페이지는 R8 언급 없음). Gson은 리플렉션 기반이라 모델 클래스/필드 keep 또는 `@SerializedName` 사용이 필요하다는 것이 통설이나 [일반 지식, 미검증], 신규 개발에서는 R8 친화적인 kotlinx.serialization(+Retrofit 컨버터) 채택이 안전. 구현 시 각 라이브러리 README/consumer rules 확인 필요.
- 릴리스 빌드(minify ON)를 반드시 실제 기기에서 테스트하고 적용된 규칙을 확인. 난독화 스택트레이스는 mapping 파일로 복원. ([출처](https://developer.android.com/topic/performance/app-optimization/keep-rules-overview), [출처](https://developer.android.com/topic/performance/app-optimization/enable-app-optimization))

### 자주 하는 실수
- 디버그 빌드에서만 테스트 -> 릴리스에서 JSON 파싱/API 호출 크래시.
- `-keep class ** { *; }` 같은 광범위 규칙으로 최적화 무력화.
- 동적 코드 로딩/리플렉션(`MessageQueue` 등) 사용 시 Android 17 타깃에서 깨질 수 있음(private 멤버 리플렉션, static final 수정 불가). ([출처](https://developer.android.com/about/versions/17/behavior-changes-17))

### 체크리스트
- [ ] 릴리스 빌드 CI에서 minify ON 스모크 테스트(로그인, 목록, 업로드)
- [ ] 직렬화 모델은 kotlinx.serialization 우선, 이름 있는 companion 주의
- [ ] mapping 파일을 Crashlytics/Play에 업로드

---

## 11. DI (Hilt), Room, DataStore, 보안 저장소

### Hilt
- 구성: `@HiltAndroidApp` Application, `@AndroidEntryPoint` Activity(ComponentActivity), `@HiltViewModel` + `@Inject constructor`, Compose에서 `hiltViewModel()`. Java 17 필요. 주입 필드는 private 불가. ([출처](https://developer.android.com/training/dependency-injection/hilt-android))
- 바인딩: 생성자 주입 우선, 인터페이스는 `@Binds`, 서드파티는 `@Provides`, 같은 타입 여러 개는 Qualifier(디스패처, OkHttp 클라이언트 등). 기본은 unscoped이며 스코프는 필요할 때만(스코프된 객체는 컴포넌트 수명 동안 메모리에 남음). `SavedStateHandle`은 `ViewModelComponent`에서 기본 제공. ([출처](https://developer.android.com/training/dependency-injection/hilt-android))
- 함정: Activity/Fragment 컨텍스트를 `SingletonComponent` 객체에 주입하면 누수 -> `@ApplicationContext`. 그리고 `@InstallIn` 컴포넌트와 스코프 불일치는 컴파일 에러. ([출처](https://developer.android.com/training/dependency-injection/hilt-android))

### Room
- `@Database`/`@Entity`/`@Dao`, suspend DAO. 단일 프로세스 앱은 DB 싱글턴. 최신 문서는 Room 3.0(`androidx.room3`, KSP 필수, `setDriver(AndroidSQLiteDriver())`)을 안내한다. 마이그레이션·Flow 반환·스키마 export는 해당 페이지에 없으므로 Room 하위 문서 확인(Flow 반환 DAO는 UI에서 `stateIn`으로 연결하는 것이 일반적 패턴 [일반 지식]). ([출처](https://developer.android.com/training/data-storage/room))
- 찰캌 활용: 피드 캐시(오프라인), 업로드 큐 메타데이터.

### DataStore
- Preferences(키-값) vs Proto(타입 안전). 파일당 프로세스당 인스턴스 1개(최상위 delegate `preferencesDataStore`), 중복 생성 시 `IllegalStateException`. 데이터 타입 불변 유지. 손상 시 `CorruptionException` -> `corruptionHandler` 지정. 크거나 관계형 데이터는 Room. ([출처](https://developer.android.com/topic/libraries/architecture/datastore))
- 암호화 없음. 민감/비민감을 분리 파일로 두고 `data_extraction_rules.xml`로 백업 제외. ([출처](https://developer.android.com/topic/libraries/architecture/datastore))

### EncryptedSharedPreferences 지원 중단 / Keystore
- `androidx.security:security-crypto` 1.1.0(2025-07-30 stable)에서 1.1.0-alpha07/beta01부터 "모든 API"가 deprecated: "플랫폼 API와 Android Keystore 직접 사용으로 대체". 문서가 `EncryptedSharedPreferences`를 이름으로 명시하진 않으나 라이브러리 전 API가 대상. 제거 시점/마이그레이션 코드는 문서에 없음. ([출처](https://developer.android.com/jetpack/androidx/releases/security))
- 권장 방향: 토큰(액세스/리프레시)은 Android Keystore의 AES/GCM 키(`KeyGenParameterSpec`, 비내보내기)로 암호화한 값을 DataStore/파일에 저장하는 방식 [구성은 일반 패턴; Keystore 자체 근거는 아래 출처]. Keystore: 키 재료가 앱 프로세스에 들어오지 않음, `KeyInfo`로 하드웨어 보호 수준 확인(API 29+ `getSecurityLevel()`), StrongBox는 느리고 알고리즘 제한이 있어 필요 시에만 + `StrongBoxUnavailableException` 폴백, 키 권한은 생성 후 변경 불가, 암호 연산은 메인 스레드 금지. ([출처](https://developer.android.com/privacy-and-security/keystore))
- Auto Backup/기기 간 전송 대상에서 토큰 파일을 제외해야 한다. ([출처](https://developer.android.com/topic/libraries/architecture/datastore))

### 체크리스트
- [ ] 신규 코드에 `EncryptedSharedPreferences`/`MasterKey` 도입 금지, Keystore 직접 사용 래퍼 설계
- [ ] DataStore 인스턴스는 최상위 단일 선언, 토큰 파일은 백업 제외
- [ ] DAO는 suspend/Flow, 마이그레이션 테스트 포함

---

## 12. 테스트 (JUnit, Turbine, Compose UI test, Robolectric)

### 핵심 개념
- 로컬(JVM) 테스트: 빠름. 인스트루먼티드: 기기/에뮬레이터. Robolectric은 로컬에서 Android 프레임워크를 시뮬레이션하는 "큰 로컬 테스트". 테스트 크기: small(단위)/medium(통합)/big(E2E). ([출처](https://developer.android.com/training/testing/fundamentals))
- 테스트 가능한 설계: 레이어 분리, 비즈니스 로직을 ViewModel/도메인에, `Context` 의존 제거, 인터페이스+DI로 fake 주입. ([출처](https://developer.android.com/training/testing/fundamentals))

### 가이드라인
- ViewModel 테스트: 디스패처 주입 + `runTest` + `Dispatchers.setMain`(3장). 테스트 디스패처는 `testScheduler` 공유. ([출처](https://developer.android.com/kotlin/coroutines/coroutines-best-practices))
- Turbine: `flow.test { awaitItem() ... }`. 블록 종료 시 flow 취소 + 미소비 이벤트가 있으면 실패. `cancelAndIgnoreRemainingEvents()`/`expectMostRecentItem()`로 정리. 타임아웃은 벽시계 기준(기본 3초)이라 `runTest` 가상 시간을 따르지 않음. 공유 Flow는 `test` 호출 후 emit(앞선 emit는 유실). 여러 flow는 `turbineScope`+`testIn(backgroundScope)`. ([출처](https://github.com/cashapp/turbine))
  - StateFlow는 conflation으로 중간 값이 생략될 수 있어 모든 중간 상태를 검증하려 하면 flaky [일반 지식, 미검증].
- Compose UI 테스트: `createComposeRule()`(Activity 불필요) / `createAndroidComposeRule<Activity>()`, 의존성 `ui-test-junit4` + `ui-test-manifest`(debug). 시맨틱 기반 finder(`onNodeWithText`, `performClick`, `assertIsDisplayed`)와 자동 동기화. 테스트에는 stateless 컴포저블에 fake UiState 주입. ([출처](https://developer.android.com/develop/ui/compose/testing))
- Compose + Robolectric 로컬 실행은 가능하나 해당 문서에서 확인하지 못함 [일반 지식, 미검증; 구현 시 Compose testing 하위 문서 확인].
- fake > mock 선호는 공식 예시가 fake Repository 주입이라는 점에서 일관됨. ([출처](https://developer.android.com/training/testing/fundamentals))

### 체크리스트
- [ ] ViewModel 단위 테스트(상태 전이) + Turbine
- [ ] 핵심 화면 Compose UI 테스트(피드, 업로드, 권한 UI), Robolectric 로컬 병행 여부 결정
- [ ] 마이그레이션/Worker 테스트(`TestListenableWorkerBuilder` 등 [일반 지식])

---

## 13. Material 3 필수 사항 & 접근성

- 터치 타깃 최소 48dp x 48dp(마우스/트랙패드 같은 정밀 입력은 예외). Compose의 `Button`, `IconButton`, `ListItem`은 자동 충족하고, 커스텀 클릭 요소는 `sizeIn(minWidth = 48.dp, minHeight = 48.dp)`로 직접 보장. 텍스트 대비: 18sp 미만(또는 14sp 미만 굵은 글씨) 4.5:1, 그 외 3:1. Accessibility Scanner로 점검. ([출처](https://developer.android.com/guide/topics/ui/accessibility/apps))
- 시맨틱: 이미지/아이콘 `contentDescription`(장식은 null), 묶음은 `mergeDescendants = true`, 클릭 동작에 `onClickLabel`. (좋아요/사인 버튼에 상태 설명 필요) ([출처](https://developer.android.com/guide/topics/ui/accessibility/principles) - 본 조사에서 해당 페이지 내용을 fetch로 확인함)
- Material 3 컴포넌트는 인셋 처리를 일부 자동화(`Scaffold`). ([출처](https://developer.android.com/about/versions/15/behavior-changes-15))
- 보충: m3.material.io 세부 가이드(색상 역할, 타이포 스케일, 동적 색상, 내비게이션 컴포넌트 선택, 적응형 레이아웃)는 이번 조사에서 직접 fetch하지 못했다 -> 디자인 시스템 확정 시 https://m3.material.io 에서 확인 필요.

체크리스트: [ ] 작은 아이콘 버튼(좋아요, 더보기) 48dp 터치 영역 [ ] 대비 4.5:1 [ ] TalkBack 라벨 [ ] 폰트 확대(200%) 레이아웃 점검 [일반 지식]

---

## 14. ANR / StrictMode / 메모리 누수 / 기기 파편화

### ANR
- 임계: 입력 디스패치 5초, 브로드캐스트 `onReceive` 5초(포그라운드 액티비티 있을 때), `startForegroundService` 후 `startForeground` 5초, 서비스/JobService는 수 초. JobService ANR은 Android 14+에서 명시 보고. ([출처](https://developer.android.com/topic/performance/vitals/anr))
- 원인: 메인 스레드 IO/긴 계산, 느린 Binder 동기 호출, `synchronized` 경합, 데드락. 대응: 무거운 처리는 ViewModel/데이터 레이어로 이동 + `withContext(Dispatchers.IO)`, 락 대신 코루틴 `Mutex.withLock`, 짧은 `onReceive`(길면 WorkManager, `goAsync` 시 `finish()` 필수). 진단: `/data/anr` 트레이스, `adb bugreport`, StrictMode, `ApplicationExitInfo`(API 30+). ([출처](https://developer.android.com/topic/performance/vitals/anr))
- Play Console 나쁜 동작 임계값 수치는 본 조사에서 확인하지 못함(별도 Android vitals 페이지 참조). ([출처](https://developer.android.com/topic/performance/vitals/anr) 에서 링크만 안내)

### StrictMode
- 개발 중 메인 스레드 디스크/네트워크 접근(`ThreadPolicy`)과 `Closeable` 누수·Activity 누수(`VmPolicy`)를 탐지. 디버그 빌드에서만 활성화, `penaltyLog()` 우선 [일반 지식, 미검증: StrictMode 레퍼런스 본문을 fetch로 읽지 못함]. ANR/Keystore 문서가 StrictMode로 메인 스레드 위반을 찾으라고 권고. ([출처](https://developer.android.com/topic/performance/vitals/anr), [출처](https://developer.android.com/privacy-and-security/keystore))
- Android 15: `StrictMode.detectUnsafeIntentLaunch()`로 안전하지 않은 인텐트 사용 탐지. ([출처](https://developer.android.com/about/versions/15/behavior-changes-15))

### 메모리 누수 / Context 누수
- 공식 메모리 개요: 참조를 해제해야 GC 가능, 핫패스(애니메이션 프레임)에서 할당 최소화, `getMemoryClass()`로 캐시 크기 결정, `ComponentCallbacks2` TRIM_MEMORY 대응, 캐시된 프로세스는 메모리 부족 시 kill. ([출처](https://developer.android.com/topic/performance/memory-overview))
- Context 누수는 Activity 참조를 싱글턴/static/오래 사는 객체에 보관할 때 발생 [일반 지식; 해당 공식 페이지는 다루지 않음]. 대응: Application Context(Hilt `@ApplicationContext`) 사용, ViewModel에 View/Activity/Composable 참조 금지, `DisposableEffect`로 리스너 해제(2장). LeakCanary로 자동 탐지(현재 2.14). ([출처](https://square.github.io/leakcanary/))
- 이미지 앱 특성: 대형 비트맵 디코딩은 다운샘플링 + 이미지 로딩 라이브러리(Coil 등) 사용 [일반 지식, 미검증; 공식 "Bitmaps and memory" 페이지는 링크만 확인].
- Android 17 위젯 `RemoteViews` 비트맵 메모리 상한(6*w*h 바이트) 등 위젯을 만들 경우 주의. ([출처](https://developer.android.com/about/versions/17/behavior-changes-17))

### 기기 파편화
- minSdk 선택은 Photo Picker 백포트(API 19+, Play 서비스 필요), `POST_NOTIFICATIONS`(33+), 부분 미디어 접근(34+), FGS 타입 규칙(34+) 등 분기 비용과 함께 결정. 분기 코드를 `Build.VERSION.SDK_INT` 체크 + 단일 래퍼에 격리. ([출처](https://developer.android.com/about/versions/14/changes/partial-photo-video-access))
- 대형 화면(폴더블/태블릿): API 36+에서 방향/크기 제한 무시, 적응형 레이아웃 필수. ([출처](https://developer.android.com/about/versions/16/behavior-changes-16))
- OEM의 배터리 최적화/백그라운드 제한은 FCM·WorkManager 신뢰성에 영향 [일반 지식, 미검증].
- 로컬 네트워크 권한, 비SDK 인터페이스 제한 등 OS 별 변경은 targetSdk 갱신 때마다 behavior-changes 문서 확인. ([출처](https://developer.android.com/about/versions/17/behavior-changes-17))

### 체크리스트
- [ ] 디버그에 StrictMode + LeakCanary
- [ ] 메인 스레드 IO/Keystore 연산 없음
- [ ] 저사양 기기(2~3GB RAM)에서 피드 스크롤/이미지 메모리 프로파일링
- [ ] 에뮬레이터 매트릭스: API 26(또는 minSdk) / 33 / 34 / 35 / 36 + 폴더블

---

## 15. 흔한 실수 모음 (요약)

1. `collectAsState()` 사용으로 백그라운드에서도 수집 -> `collectAsStateWithLifecycle()`.
2. 일회성 이벤트를 Channel/SharedFlow로 노출하여 유실 -> 상태로 모델링.
3. `CancellationException` 삼킴, `GlobalScope`, 하드코딩 Dispatcher.
4. 업로드를 `viewModelScope`에서 수행 -> WorkManager.
5. 앱 시작 시 알림/미디어 권한 일괄 요청; 권한 상태 캐시.
6. `READ_MEDIA_IMAGES` 선언으로 Play 정책 위반 위험 -> Photo Picker.
7. edge-to-edge 인셋 미처리, `onBackPressed` 유지.
8. R8 릴리스 빌드 미검증, Gson 모델 난독화.
9. `EncryptedSharedPreferences` 신규 도입(deprecated).
10. DataStore 중복 인스턴스, 토큰 파일 백업 포함.
11. Lazy 리스트 key 누락, unstable 모델로 인한 과도한 리컴포지션.
12. 프로세스 사망/대형 화면 재생성 테스트 부재.
13. targetSdk 미갱신(2026-08-31 이후 API 36 필수), 네이티브 라이브러리 16KB 미대응.

---

## 찰캌 적용 포인트

| 기능 | 권장 Android 구현 | 근거 섹션 |
|---|---|---|
| 일일 주제 피드 | Repository(Room 캐시 + 원격) -> `StateFlow<FeedUiState>`, `LazyColumn` key=postId, `collectAsStateWithLifecycle` | 1, 2, 11 |
| 좋아요/사인 토글 | ViewModel 함수 + 낙관적 업데이트는 상태 교체(`update{copy}`), 실패 시 메시지 상태 + 스낵바 소비 패턴 | 1, 3 |
| 사진 선택 | Photo Picker(`PickVisualMedia`/`PickMultipleVisualMedia`), 미디어 권한 미선언 | 5 |
| 이미지 업로드 | 선택 URI를 앱 캐시로 복사 -> `CoroutineWorker`(네트워크 제약, 백오프, 유니크 작업, 필요 시 `setForeground` + FGS 타입) -> 진행률 `WorkInfo` 관찰 | 5, 6 |
| AI 사진 리뷰 | 서버 비동기 결과를 푸시 + 상태 폴링/갱신으로 수신; 완료 알림은 FCM | 7 |
| 푸시 알림 | FCM(`onNewToken` 서버 동기화), `POST_NOTIFICATIONS` 맥락 요청, 채널 분리, 탭 -> App Links 딥링크 | 7, 8 |
| 인증 토큰 보관 | Keystore AES/GCM + DataStore, 백업 제외 (EncryptedSharedPreferences 사용 안 함) | 11 |
| 공유 링크 | App Links(`assetlinks.json`), 미설치 웹 폴백 | 8 |
| UI 플랫폼 정책 | targetSdk 36, edge-to-edge, predictive back, 48dp 터치 타깃, 대형 화면 대응 | 9, 13 |
| 빌드/배포 | R8 ON, kotlinx.serialization 우선, 16KB(네이티브 SDK 사용 시), mapping 업로드 | 9, 10 |
| 품질 | StrictMode/LeakCanary(디버그), ViewModel+Turbine, Compose UI 테스트, 프로세스 사망 시나리오 | 12, 14 |

구현 시 추가 합의가 필요한 항목:
- 업로드 이미지의 EXIF/위치정보 제거 정책, 업로드 재시도 시 서버 멱등성(백엔드).
- 푸시 payload 스키마(notification vs data, 딥링크 필드).
- minSdk 결정(Photo Picker 백포트, 알림 권한 분기 범위).
- DI/직렬화/이미지 로더 라이브러리 최종 선택(본 조사는 Hilt, kotlinx.serialization 중심으로 정리; Retrofit/Gson R8 세부와 m3.material.io 세부는 미검증).

---

## 출처 목록

공식(Android / Google / Firebase)
- 아키텍처: https://developer.android.com/topic/architecture
- 상태 생산: https://developer.android.com/topic/architecture/ui-layer/state-production
- UI 이벤트: https://developer.android.com/topic/architecture/ui-layer/events
- UI 상태 저장: https://developer.android.com/topic/libraries/architecture/saving-states
- 라이프사이클 코루틴: https://developer.android.com/topic/libraries/architecture/coroutines
- 코루틴 베스트 프랙티스: https://developer.android.com/kotlin/coroutines/coroutines-best-practices
- Compose 상태: https://developer.android.com/develop/ui/compose/state
- Compose 부수 효과: https://developer.android.com/develop/ui/compose/side-effects
- Compose 안정성: https://developer.android.com/develop/ui/compose/performance/stability
- Compose 성능 베스트 프랙티스: https://developer.android.com/develop/ui/compose/performance/bestpractices
- Compose 인셋: https://developer.android.com/develop/ui/compose/system/insets
- Views edge-to-edge: https://developer.android.com/develop/ui/views/layout/edge-to-edge
- Android 15 동작 변경: https://developer.android.com/about/versions/15/behavior-changes-15
- Android 16 동작 변경: https://developer.android.com/about/versions/16/behavior-changes-16
- Android 17 동작 변경: https://developer.android.com/about/versions/17/behavior-changes-17
- Predictive back: https://developer.android.com/guide/navigation/custom-back/predictive-back-gesture
- Photo Picker: https://developer.android.com/training/data-storage/shared/photo-picker
- 부분 사진/비디오 접근(Android 14): https://developer.android.com/about/versions/14/changes/partial-photo-video-access
- 알림 권한: https://developer.android.com/develop/ui/views/notifications/notification-permission
- WorkManager 시작: https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started
- WorkManager 작업 정의: https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work
- WorkManager 장시간 작업: https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/long-running
- FGS 백그라운드 시작 제한: https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
- App Links 검증: https://developer.android.com/training/app-links/verify-android-applinks
- 16KB 페이지 크기: https://developer.android.com/guide/practices/page-sizes
- 앱 최적화(R8) 활성화: https://developer.android.com/topic/performance/app-optimization/enable-app-optimization
- Keep 규칙 개요: https://developer.android.com/topic/performance/app-optimization/keep-rules-overview
- Hilt: https://developer.android.com/training/dependency-injection/hilt-android
- Room: https://developer.android.com/training/data-storage/room
- DataStore: https://developer.android.com/topic/libraries/architecture/datastore
- Security 라이브러리 릴리스: https://developer.android.com/jetpack/androidx/releases/security
- Android Keystore: https://developer.android.com/privacy-and-security/keystore
- 테스트 기본: https://developer.android.com/training/testing/fundamentals
- Compose 테스트: https://developer.android.com/develop/ui/compose/testing
- 접근성(터치 타깃/대비): https://developer.android.com/guide/topics/ui/accessibility/apps
- 접근성 원칙(콘텐츠 설명): https://developer.android.com/guide/topics/ui/accessibility/principles
- ANR: https://developer.android.com/topic/performance/vitals/anr
- 메모리 개요: https://developer.android.com/topic/performance/memory-overview
- FCM Android 클라이언트: https://firebase.google.com/docs/cloud-messaging/android/client
- FCM 메시지 유형: https://firebase.google.com/docs/cloud-messaging/customize-messages/set-message-type
- Google Play 대상 API 수준 요구사항: https://support.google.com/googleplay/android-developer/answer/11926878
- Google Play 사진/동영상 권한 정책: https://support.google.com/googleplay/android-developer/answer/14115180

2차(평판 있는 오픈소스 프로젝트)
- kotlinx.serialization README: https://github.com/Kotlin/kotlinx.serialization/blob/master/README.md
- Turbine: https://github.com/cashapp/turbine
- LeakCanary: https://square.github.io/leakcanary/

조사에서 직접 확인하지 못한 항목(구현 시 재확인)
- kotlinlang.org 코루틴 예외 처리 문서, `CoroutineExceptionHandler` 세부
- Retrofit/Gson 공식 R8 규칙, StrictMode 레퍼런스 본문, m3.material.io 세부 가이드, Robolectric 공식 문서
- Play Console Android vitals ANR/크래시 나쁜 동작 임계값, FCM 처리 시간 제한 수치
