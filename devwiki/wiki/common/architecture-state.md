# 아키텍처, UI 상태, 컴포넌트 분리, 비동기 패러다임

공통 어휘는 [00-principles.md §3](../00-principles.md#3-공통-어휘)을 따른다. 근거: [raw/architecture-testing.md §1~5](../../raw/architecture-testing.md), 플랫폼별 세부는 각 raw 파일의 아키텍처 절.

## 1. 계층과 데이터 흐름

모든 플랫폼 공식 가이드의 공통분모는 네 가지다. [공식]

1. **관심사 분리**: UI 계층(Screen, Component, ViewModel)과 데이터 계층(Repository, Service). domain 계층은 선택.
2. **단방향 데이터 흐름(UDF)**: 상태는 아래로(Repository → ViewModel → UI), 이벤트는 위로(UI → ViewModel → Repository).
3. **단일 진실 원천(SSOT)**: 데이터 타입마다 소유자가 하나다. 소유자는 불변 값으로 노출하고 변경은 함수로만 받는다.
4. **Repository는 인터페이스 뒤에**: 데이터 소스가 하나여도 Repository를 둔다. UI와 ViewModel은 네트워크·DB를 직접 호출하지 않는다.

규칙

- Repository끼리 서로 호출하지 않는다. 두 Repository 데이터가 필요하면 ViewModel이나 UseCase에서 합친다. (Flutter 가이드) [공식]
- Service는 상태를 갖지 않는다. 캐싱, 재시도, 에러 정규화는 Repository가 맡는다. (Flutter 가이드) [공식]
- 계산 가능한 값(필터된 목록, 개수)은 상태로 따로 저장하지 않는다. (Thinking in React) [공식]
- 앱 컴포넌트(Activity, AppDelegate, 루트 위젯)에 데이터나 비즈니스 로직을 두지 않는다. OS가 언제든 파괴할 수 있다. (Android 가이드) [공식]

| 자주 하는 실수 | 대신 |
| --- | --- |
| View/Widget/컴포넌트에서 API 호출 | ViewModel이 Repository를 호출 |
| 모든 Repository 호출마다 1:1 UseCase 래퍼 | 결합·복잡·재사용 중 하나일 때만 UseCase |
| 플랫폼마다 Interactor/Manager/Service 등 이름이 제각각 | 공통 어휘만 사용 |
| 한쪽 플랫폼 구조를 그대로 이식(Redux 스타일을 SwiftUI에 강요) | 어휘와 경계는 맞추고 구현은 플랫폼 관례 |

## 2. UI 상태 모델링

- 화면마다 UiState 하나를 노출한다. 상호 배타적인 상태(로딩, 콘텐츠, 빈 상태, 오류)는 sealed/enum/union으로, 동시에 존재할 수 있는 값은 필드로 둔다. `isLoading`, `hasError`, `isEmpty`를 각각 boolean으로 두면 "로딩 중이면서 오류" 같은 불가능한 상태가 생긴다. [공식](https://developer.android.com/topic/architecture/recommendations)
- UiState는 불변이다. Android `copy()`, Swift struct 값 복사, Dart `copyWith`, TS spread로 새 값을 만든다.
- 다섯 플랫폼이 같은 variant 이름을 쓴다. 예시 [제안]:

```text
FeedUiState    = Loading | Empty(topic) | Content(posts, isRefreshing, paging) | Error(kind, retryable)
LikeUi         = liked: Bool, likeCount: Int, pending: Bool
UploadUiState  = Idle | Preparing | Uploading(progress) | Submitting | Validating | Done(postId) | Failed(reason, retryable)
AiReviewUi     = None | Processing(startedAt) | Completed(result) | Failed(canRequestAgain) | TimedOut
userMessage    = String?   // 토스트·스낵바. 표시 후 소비 콜백으로 null
```

### 일회성 이벤트

"State is; events happen." ViewModel이 UI에 보내는 일회성 이벤트(스낵바, 화면 이동)는 **상태로 모델링하고 UI가 처리 후 소비를 알린다**. Channel·SharedFlow·EventEmitter로 흘려보내면 생산자가 소비자보다 오래 살 때(회전, 백그라운드) 유실된다. [공식](https://developer.android.com/topic/architecture/ui-layer/events)

- 기존 Android 앱의 결정: ViewModel은 "로그인 완료" 같은 피처 결과만 상태로 내고, 목적지는 Route와 NavHost가 정한다. ([ui-state-navigation.md](../../../client/android/docs/architecture/ui-state-navigation.md)) [찰캌규칙]
- Flutter Command 패턴도 같은 원칙이다. 결과를 처리한 뒤 `clearResult()`를 부르지 않으면 notify마다 스낵바가 반복된다. [공식](https://docs.flutter.dev/app-architecture/design-patterns/command)

## 3. 컴포넌트 분리

- **Component(표현)**: 상태를 파라미터로 받고, 변경은 콜백으로 올린다. ViewModel, Repository, Navigator를 import하지 않는다.
- **Screen(컨테이너)**: ViewModel을 연결하는 얇은 계층. ViewModel은 화면 최상단에서만 주입하고, 리스트 아이템에는 ViewModel 대신 `onLikeClick(postId)` 같은 람다를 넘긴다. [공식](https://developer.android.com/develop/ui/compose/state-hoisting)
- **State hoisting**: 상태는 그것을 읽는 컴포넌트들의 가장 낮은 공통 조상으로 올린다. Compose의 state hoisting과 React의 lifting state up은 같은 개념이다. [공식]
- **디자인 시스템 컴포넌트**는 토큰(색, 타이포, 간격)만 쓰고 비즈니스 용어(Post, Sign)를 모른다. 확장 지점은 slot(`@ViewBuilder`, `content: @Composable`, `child`, `children`)으로 둔다. 기존 iOS 앱의 규칙: 공통 컴포넌트는 비즈니스 상태와 내비게이션을 소유하지 않고, Safe Area·바깥 여백·전체 폭은 호출하는 화면이 결정한다. ([client/iOS/CLAUDE.md](../../../client/iOS/CLAUDE.md)) [찰캌규칙]
- **프리뷰 가능성**이 분리의 시험대다. ViewModel 없이 샘플 UiState만으로 Loading/Empty/Error/긴 텍스트/다크 모드/큰 글자 프리뷰가 그려져야 한다. [공식](https://developer.android.com/develop/ui/compose/tooling/previews)

| 플랫폼 | 표현 컴포넌트 | 로컬 UI 상태 | 프리뷰 도구 |
| --- | --- | --- | --- |
| iOS | `PostCardView(post:onLike:)` | `@State` | `#Preview` |
| Android | `PostCard(state, onLike)` | `remember`, `rememberSaveable` | `@Preview` + `PreviewParameterProvider` |
| Flutter | `PostCard(post, onLike)` | `StatefulWidget` | widgetbook 등 [일반지식] |
| RN / Web | `<PostCard post onLike />` | `useState` | Storybook [일반지식] |

## 4. 의존성 주입

DI의 본질은 의존성을 생성자로 받아 테스트에서 바꿔 끼우는 것이다. 프레임워크는 "어디서 만들어 줄지"의 문제일 뿐이다. [공식](https://developer.android.com/training/testing/fundamentals)

| 플랫폼 | 기본형 | 도구 |
| --- | --- | --- |
| iOS | 이니셜라이저 주입(protocol) | 필요 시 swift-dependencies, `@Environment` |
| Android | 생성자 주입 | 기존 앱은 수동 컨테이너(`AppContainer`). Hilt는 선택. 단위 테스트에서는 Fake를 직접 생성 [공식] |
| Flutter | 생성자 주입 | provider(공식 사례), riverpod, get_it |
| RN / Web | 생성자·props·Context | Context, 모듈 단위 팩토리 |

시간(`Clock`), UUID, 네트워크, 푸시 토큰, 이미지 피커처럼 결정적이지 않은 요소는 모두 주입 가능해야 한다. ViewModel 안에서 `Date()`나 `UUID()`를 직접 부르면 테스트가 시간에 묶인다.

## 5. 콜백과 비동기 패러다임

새 코드는 콜백 대신 각 언어의 async 모델을 쓰고, 레거시 콜백 API는 브리지 한 곳에 격리한다. 콜백을 감쌀 때는 **정확히 한 번 resume**하고 취소를 연결한다. 근거: [raw/network-resilience-offline.md §8](../../raw/network-resilience-offline.md)

| 개념 | Swift | Kotlin | Dart | JS/TS |
| --- | --- | --- | --- | --- |
| 비동기 함수 | `async throws` | `suspend fun` | `Future<T>` | `Promise<T>` |
| 병렬 | `async let`, `TaskGroup` | `async` + `awaitAll` | `Future.wait` | `Promise.all` / `allSettled` |
| 스트림 | `AsyncSequence` | `Flow` | `Stream` | AsyncIterator / RxJS |
| 취소 | 협력적 `Task.cancel()` | 협력적 `Job.cancel()` | **내장 없음** → `CancelToken` 전달 | **내장 없음** → `AbortSignal` 전달 |
| 수명 범위 | SwiftUI `.task {}` | `viewModelScope`, `LaunchedEffect` | `dispose()`에서 수동 정리 | `useEffect` cleanup |
| 메인 스레드 | `@MainActor` | `Dispatchers.Main` | UI isolate | 이벤트 루프 단일 스레드 |
| 콜백 브리지 | `withCheckedThrowingContinuation` | `suspendCancellableCoroutine` | `Completer` | `new Promise` |

Dart와 JS의 Future·Promise에는 취소가 없다. 다섯 플랫폼 중 취소 동작이 가장 쉽게 갈리는 지점이므로, 오래 걸리는 Repository 함수는 `CancelToken`/`AbortSignal`을 인자로 받는 것을 규약으로 둔다. [제안]

| 자주 하는 실수 | 결과 |
| --- | --- |
| Kotlin `catch (e: Exception)`이 `CancellationException`까지 삼킴 | 취소가 멈추고 화면을 떠나도 작업이 계속됨. 재던진다 [공식] |
| `GlobalScope`, `Task.detached`, 소유자 없는 Future | 수명 누수 |
| `await` 뒤 Flutter `BuildContext` 사용 | dispose된 위젯 접근. `mounted` 확인 [공식] |
| Promise를 await 없이 방치 | unhandled rejection. 전역 핸들러 필요 |
| 레거시 delegate 콜백에서 UI 직접 갱신 | 메인 스레드 위반. `@MainActor`·`Dispatchers.Main`으로 이동 |

## 체크리스트

- [ ] 화면 = Screen 1 + ViewModel 1 + UiState 1
- [ ] Component는 ViewModel·Repository·Navigator를 import하지 않는다(가능하면 lint로 강제)
- [ ] UiState의 배타 상태는 sealed/enum/union, 불변
- [ ] 일회성 이벤트는 상태 + 소비 콜백
- [ ] Repository는 인터페이스, Fake 구현이 있다
- [ ] Clock·UUID·네트워크·푸시·피커가 주입 가능
- [ ] 장시간 작업은 취소 신호를 받고, 화면 수명에 묶여 있다
