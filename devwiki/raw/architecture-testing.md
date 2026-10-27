# Testable Clean Architecture, 컴포넌트 분리, 테스트 전략 (5 플랫폼)

> 조사일: 2026-10-27 / 대상: iOS(SwiftUI), Android(Compose), Flutter, React Native, Web
> 목적: 5개 클라이언트가 동일한 기능 동작을 갖도록 "공통 아키텍처 어휘"와 "패리티 검증 방법"을 정리한다.
> 표기 규칙: 모든 주장에는 [출처](URL)를 단다. 공식 문서를 직접 확인하지 못한 항목은 `[미확인]`으로 표시한다.
> 주의: Apple 문서(developer.apple.com)는 JS 렌더링 페이지라 본문을 직접 가져오지 못했다. Apple 관련 서술 중 본문 근거가 없는 것은 `[미확인: 일반 지식]`으로 표시했다.

---

## 0. 한눈에 보기 (TL;DR)

- 모든 플랫폼 공식 가이드의 공통분모: **관심사 분리(Separation of Concerns) + 단방향 데이터 흐름(UDF) + 단일 진실 공급원(SSOT) + 인터페이스 뒤의 Repository**.
- Domain(UseCase) 계층은 **선택**. Android/Flutter 공식 가이드 모두 "필요할 때만" 추가하라고 명시한다.
- 테스트 더블은 **Fake 우선**(Android 공식), Mock은 상호작용 검증이 꼭 필요할 때만.
- UI 컴포넌트는 **stateless(표현) + 화면 단위 container(ViewModel 연결)** 로 나누고, ViewModel은 화면 최상단에서만 주입한다.
- 5개 클라이언트 패리티는 **공유 Gherkin 시나리오 + OpenAPI 기반 공유 mock/fixture + 공통 analytics 이벤트 명세 + Maestro 공통 E2E 플로우**로 검증한다.
- Maestro: Android/iOS 시뮬레이터 지원, Flutter/RN은 UI 레이어 자동화로 지원, Web은 **Beta, Chromium만** 지원.

---

## 1. 계층형 아키텍처 (UI / Domain / Data, UDF, SSOT, Repository)

### 1.1 핵심 개념

| 개념 | 정의 |
|---|---|
| Separation of concerns | 앱을 클래스/모듈/레이어로 나누고 책임을 분리. Android 가이드가 "가장 중요한 원칙"으로 지정 ([출처](https://developer.android.com/topic/architecture)) |
| Drive UI from data models | 영속적이고 UI와 독립된 데이터 모델에서 UI를 구동 ([출처](https://developer.android.com/topic/architecture)) |
| SSOT | 데이터 타입마다 수정 권한을 가진 소유자가 하나. 소유자는 불변 타입으로 노출하고 변경은 함수/이벤트로 받음 ([출처](https://developer.android.com/topic/architecture)) |
| UDF | 상태는 아래로(데이터 소스 → Repository → UI), 이벤트는 위로(UI → SSOT) 흐름 ([출처](https://developer.android.com/topic/architecture)) |
| UI layer | 데이터를 표시. UI element + state holder(ViewModel) ([출처](https://developer.android.com/topic/architecture)) |
| Domain layer (optional) | 복잡하거나 재사용되는 비즈니스 로직을 use case(interactor)로 캡슐화 ([출처](https://developer.android.com/topic/architecture/domain-layer)) |
| Data layer | Repository(데이터 타입별 1개) + Data source(소스별 1개: 파일/네트워크/DB) ([출처](https://developer.android.com/topic/architecture)) |

### 1.2 공식 가이드라인

**Android**
- 의존 방향: UI → (domain) → data. 앱 컴포넌트(Activity 등)는 데이터/상태를 들고 있지 않는다 ([출처](https://developer.android.com/topic/architecture)).
- Repository: "Strongly recommended" — 데이터 소스가 하나여도 Repository를 만든다. UI/ViewModel이 DB/네트워크에 직접 접근하지 않는다 ([출처](https://developer.android.com/topic/architecture/recommendations)).
- ViewModel은 Android 라이프사이클(Activity/Context/Resources)에 의존하지 않는다 ([출처](https://developer.android.com/topic/architecture/recommendations)).
- ViewModel은 **화면 단위**에서 사용하고, 재사용 UI 조각에는 일반 state holder 클래스를 쓴다 ([출처](https://developer.android.com/topic/architecture/recommendations)).

**Flutter**
- 두 레이어(UI, Data) 위에 MVVM: Model = Repository+Service, View = Widget, ViewModel = 데이터를 UI 상태로 변환 ([출처](https://docs.flutter.dev/app-architecture/guide)).
- Repository는 SSOT이며 서로를 알지 못한다(조합은 ViewModel 또는 domain layer에서). Service는 상태가 없고 데이터 소스당 1개 ([출처](https://docs.flutter.dev/app-architecture/guide)).
- View에는 비즈니스 로직 금지. 허용 범위: 단순 show/hide 조건, 애니메이션, 디바이스 정보 기반 레이아웃, 단순 라우팅 ([출처](https://docs.flutter.dev/app-architecture/guide)).
- ViewModel은 View와 1:1, Repository와는 N:M. Command(함수)로 이벤트를 받는다 ([출처](https://docs.flutter.dev/app-architecture/guide)).

**Apple (SwiftUI / Observation)** — 본문 직접 확인 실패, 일반 지식 `[미확인]`
- `@Observable` 클래스(iOS 17+, Observation)를 모델/ViewModel로 사용, `@State`로 소유, `@Bindable`로 바인딩, `@Environment`로 주입. 각 데이터의 소유자는 하나(SSOT) ([출처: Apple 문서 페이지, 본문 미확인](https://developer.apple.com/documentation/swiftui/managing-model-data-in-your-app)).
- Apple은 "MVVM을 써라"처럼 단일 아키텍처를 강제하지 않는다. View = 상태의 함수라는 SwiftUI 모델 위에서 Observable 모델 + Repository 프로토콜을 두는 방식이 일반적이다 `[미확인: 일반 지식]`.

**React / Web** — "Thinking in React"
1. UI를 컴포넌트 계층으로 분해 → 2. props만으로 정적 버전 → 3. **최소 state** 식별(계산 가능한 값은 state 아님) → 4. state를 공통 부모로 **끌어올림(lifting state up)** → 5. 콜백 props로 **inverse data flow** ([출처](https://react.dev/learn/thinking-in-react)).
- 데이터는 props로 아래로, 변경은 콜백으로 위로 — UDF와 동일한 구조 ([출처](https://react.dev/learn/thinking-in-react)).
- 서버 상태(캐시)의 SSOT를 Repository 역할의 모듈/훅(예: TanStack Query 등)에 두는 패턴은 공식 문서 직접 확인을 못 했다 `[미확인]`.

### 1.3 플랫폼별 매핑 표 (공통 어휘)

| 공통 어휘 | Android (Compose) | iOS (SwiftUI) | Flutter | React Native | Web (React) |
|---|---|---|---|---|---|
| View / UI element | `@Composable` | `View` struct | `Widget` | Function component | Function component |
| State holder (screen) | `ViewModel` + `StateFlow<UiState>` | `@Observable` class (ViewModel) `[미확인]` | `ChangeNotifier` ViewModel (+ commands) | custom hook / store (예: `useXxxViewModel`) | custom hook / store |
| UI 상태 모델 | `sealed interface` / `data class` | `enum` + associated values `[미확인]` | `sealed class` / Result | discriminated union (TS) | discriminated union (TS) |
| 이벤트 (UI → 상위) | 람다 파라미터, ViewModel 메서드 | 클로저, ViewModel 메서드 | callback, command | callback props | callback props |
| Repository (SSOT, 인터페이스) | `interface XRepository` + impl | `protocol XRepository` | `abstract class XRepository` | TS `interface` | TS `interface` |
| Data source / Service | Retrofit service, Room DAO | URLSession client | `ApiClient` Service (stateless) | fetch/axios client | fetch client |
| Domain (optional) | `XxxUseCase` | UseCase struct (선택) | use-case (선택) | 순수 함수 모듈 | 순수 함수 모듈 |
| State hoisting | state + `onXChange` 파라미터 | `@Binding` / 값+클로저 | 값 + callback | lifting state up | lifting state up |
| DI | Hilt (생성자 주입) | 이니셜라이저 주입 / swift-dependencies | provider / get_it / riverpod | Context / 생성자 | Context / 생성자 |

근거: Android [아키텍처](https://developer.android.com/topic/architecture), Flutter [가이드](https://docs.flutter.dev/app-architecture/guide), React [Thinking in React](https://react.dev/learn/thinking-in-react). iOS/RN 열의 세부 타입은 팀 컨벤션 제안이며 공식 문서가 직접 규정한 것은 아니다.

### 1.4 자주 하는 실수
- Activity/Fragment/View/Widget이 데이터를 직접 가져오거나 변환한다 ([출처](https://developer.android.com/topic/architecture)).
- Repository끼리 서로 호출한다 (Flutter 가이드는 금지) ([출처](https://docs.flutter.dev/app-architecture/guide)).
- 같은 데이터를 ViewModel과 Repository가 각각 복제 보관한다 → SSOT 위반.
- state를 계산 가능한 값(필터된 리스트, 카운트)까지 중복 보관한다 ([출처](https://react.dev/learn/thinking-in-react)).
- 단일 소스인데 Repository를 생략하고 ViewModel이 API를 직접 호출한다 ([출처](https://developer.android.com/topic/architecture/recommendations)).

### 1.5 체크리스트
- [ ] 데이터 타입별 SSOT 소유자가 문서화되어 있는가 (예: Post 목록, Like 상태, 토픽)
- [ ] UI → Repository 직접 호출이 없는가
- [ ] Repository는 인터페이스(프로토콜/abstract/TS interface)로 노출되는가
- [ ] 상태는 아래로, 이벤트는 위로만 흐르는가
- [ ] 계산 가능한 값은 state로 저장하지 않았는가

---

## 2. Clean Architecture / MVVM / MVI 트레이드오프와 과설계 경고

### 2.1 핵심 개념
- **MVVM**: View ↔ ViewModel(상태 + 로직) ↔ Model(Repository). Flutter 공식 가이드가 채택 ([출처](https://docs.flutter.dev/app-architecture/guide)). Android의 "UI layer: UI element + state holder" 구조도 사실상 MVVM/UDF ([출처](https://developer.android.com/topic/architecture)).
- **MVI**: 단일 State + Intent(Action) + Reducer 형태의 엄격한 UDF. 공식 가이드가 MVI를 명칭으로 권장하지는 않으며, Android의 "단일 uiState + 이벤트 메서드"가 이에 가깝다 ([출처](https://developer.android.com/topic/architecture/recommendations)). MVI 자체 정의는 공식 출처 미확인 `[미확인]`.
- **Clean Architecture**: 의존성이 안쪽(도메인)으로 향하는 동심원 구조. Android 가이드는 "domain layer"라는 용어가 clean architecture에서의 의미와 다르다고 명시한다 ([출처](https://developer.android.com/topic/architecture/domain-layer)).

### 2.2 공식 가이드라인 (과설계 관련)
- Android: "add use cases only when required". UseCase를 강제하면 "단순한 함수 호출 위임에 불과한 클래스가 늘어 이득 없이 복잡도만 증가"한다 ([출처](https://developer.android.com/topic/architecture/domain-layer)).
- Android Recommendations: 유스케이스는 "큰 앱에서 recommended" (ViewModel 간 재사용 또는 ViewModel 복잡도 감소 목적) ([출처](https://developer.android.com/topic/architecture/recommendations)).
- Flutter: use-case는 (1) 여러 Repository 데이터 병합, (2) 매우 복잡한 로직, (3) 여러 ViewModel에서 재사용될 때만. "모든 데이터 접근마다 use-case를 만들지 말 것". 클래스/보일러플레이트/mock이 늘어나는 비용이 있다 ([출처](https://docs.flutter.dev/app-architecture/guide)).
- 두 가이드 모두 "guidelines이며 앱 상황에 맞게 조정"하라고 한다 ([출처](https://developer.android.com/topic/architecture/recommendations), [출처](https://docs.flutter.dev/app-architecture/guide)).

### 2.3 트레이드오프 표

| 패턴 | 장점 | 단점/위험 | 찰캌 권고 |
|---|---|---|---|
| MVVM + UDF (UiState 1개) | 5 플랫폼 공통 어휘로 매핑 쉬움, 공식 가이드와 일치 | ViewModel 비대화 | **기본 채택** |
| MVI (Reducer/Intent) | 상태 전이 예측 가능, 로그/테스트 용이 | 보일러플레이트 증가 | 복잡 화면(업로드/AI 리뷰 진행)에서만 선택적 |
| Full Clean Architecture (UseCase 전면) | 계층 강제 | 얇은 위임 클래스 폭증 | **비권장** |
| Domain layer(선택) | 재사용 로직 분리 | 남용 시 복잡도 | 필요 시만 (예: "오늘의 토픽 + 내 사인 병합") |

### 2.4 자주 하는 실수
- 모든 Repository 호출마다 UseCase 1:1 래퍼 생성.
- 5개 플랫폼에 서로 다른 계층 명칭(Interactor/Service/Manager 혼용) 사용 → 패리티 리뷰 불가.
- 플랫폼 관용(Idiom) 무시하고 한쪽 구조를 그대로 이식 (예: Redux 스타일을 SwiftUI에 강요).

### 2.5 체크리스트
- [ ] UseCase 도입 시 "병합/복잡/재사용" 중 어떤 사유인지 PR에 기록했는가
- [ ] 계층 명칭 사전(§1.3)과 일치하는가
- [ ] 한 화면 = 1 ViewModel = 1 UiState 원칙인가

---

## 3. 의존성 주입(DI)과 테스트 더블 (Fake vs Mock)

### 3.1 핵심 개념
- DI의 본질은 **의존성을 생성자(이니셜라이저)로 받아** 테스트에서 교체 가능하게 하는 것. Android: "인터페이스에 의존하고, DI 프레임워크 없이라도 DI를 사용" ([출처](https://developer.android.com/training/testing/fundamentals)).
- Flutter도 각 레이어가 생성자로 의존 컴포넌트를 받는 구조이고, DI는 "그 인스턴스를 어디서 만드는가"에 대한 답이다 ([출처](https://docs.flutter.dev/app-architecture/case-study/dependency-injection)).

**Test double 분류 (Fowler)**: Dummy / Fake(실제 동작하는 단축 구현, 예: in-memory DB) / Stub(고정 응답) / Spy(호출 기록) / Mock(기대 호출이 사전 설정, 행위 검증) ([출처](https://martinfowler.com/articles/mocksArentStubs.html)). 정의는 출처마다 다를 수 있다 ([출처](https://developer.android.com/training/testing/fundamentals/test-doubles)).

### 3.2 공식 가이드라인
- **Android**: Fake는 "preferred" — 프레임워크 불필요, 가볍고, 테스트 소스셋에만 존재. Stub보다 Fake, Spy보다 Fake/Mock 선호 ([출처](https://developer.android.com/training/testing/fundamentals/test-doubles)).
- **Android Hilt**: 단위 테스트에는 Hilt가 필요 없다 — 클래스를 Fake로 직접 생성. UI/E2E에서는 `@TestInstallIn`으로 모든 테스트에 Fake 모듈 교체(권장), 개별 테스트는 `@UninstallModules`(빌드 시간 증가 주의) ([출처](https://developer.android.com/training/dependency-injection/hilt-testing)).
- **Flutter**: ViewModel 단위 테스트는 Flutter 라이브러리에 의존하지 않고 Repository의 Fake만 필요. Widget 테스트도 같은 Fake를 재사용(ViewModel 자체는 fake하지 않음). Repository 테스트는 Service(ApiClient)를 Fake ([출처](https://docs.flutter.dev/app-architecture/case-study/testing)). 실제 DI는 `package:provider`로 최상위 `MultiProvider`에 Service/Repository 등록, 라우트 레벨에서 ViewModel 생성 ([출처](https://docs.flutter.dev/app-architecture/case-study/dependency-injection)).
- **iOS**: swift-dependencies는 SwiftUI environment에서 영감받은 라이브러리. `@Dependency(\.key)`로 선언, 테스트/프리뷰에서 `.dependencies` trait로 override (예: clock을 immediate로, `date.now` 고정) ([출처](https://github.com/pointfreeco/swift-dependencies)). 라이브러리 없이 "protocol + 이니셜라이저 주입"이 기본형 `[미확인: 일반 지식]`.
- **React Native**: "가능하면 실제 객체, mock은 fallback". 네트워크 같은 외부 의존성과 네이티브 모듈만 mock ([출처](https://reactnative.dev/docs/testing-overview)).
- **Web/React**: Context로 Repository 구현을 주입하는 패턴은 공식 문서 직접 확인 못함 `[미확인]`. 네트워크 경계는 MSW로 가로채기 ([출처](https://mswjs.io/docs/)).
- **Fowler**: 고전적(classical) TDD는 실제 객체 우선, 필요할 때만 더블 / mockist는 협력자를 거의 항상 mock ([출처](https://martinfowler.com/articles/mocksArentStubs.html)). 테스트는 내부 호출 순서가 아닌 관찰 가능한 동작을 검증해야 리팩터링에 안 깨진다 ([출처](https://martinfowler.com/articles/practical-test-pyramid.html)).

### 3.3 플랫폼별 매핑 표

| 항목 | Android | iOS | Flutter | React Native | Web |
|---|---|---|---|---|---|
| DI 기본형 | 생성자 주입 | 이니셜라이저 주입 | 생성자 주입 | 생성자/props/Context | 생성자/props/Context |
| DI 도구 | Hilt ([출처](https://developer.android.com/training/dependency-injection/hilt-testing)) | swift-dependencies ([출처](https://github.com/pointfreeco/swift-dependencies)) | provider ([출처](https://docs.flutter.dev/app-architecture/case-study/dependency-injection)), get_it/riverpod `[미확인]` | Context `[미확인]` | Context `[미확인]` |
| 단위 테스트에서 교체 | Fake 직접 생성 | Fake 직접 주입 / `.dependencies` | `FakeXRepository implements XRepository` | 인터페이스 구현체 주입 | 인터페이스 구현체 주입 |
| 통합/UI 테스트에서 교체 | `@TestInstallIn` | `.dependencies` trait / launch argument | `Provider.value(fake)` | 테스트 Provider | 테스트 Provider / MSW |
| Fake 위치 | test 소스셋 | Test 타깃 | `test/` | `__tests__` / test utils | test utils |

### 3.4 자주 하는 실수
- 단위 테스트에서 DI 프레임워크(Hilt 등)를 띄운다 — 불필요 ([출처](https://developer.android.com/training/dependency-injection/hilt-testing)).
- Mock의 호출 횟수/순서를 검증해 리팩터링마다 테스트가 깨진다 ([출처](https://martinfowler.com/articles/practical-test-pyramid.html)).
- Fake가 운영 구현과 동작이 달라지는 drift — Fake와 실제 구현이 같은 계약 테스트를 공유하게 한다(제안).
- ViewModel 내부에서 `Date()`/`UUID()`/싱글톤을 직접 호출 → 시간/난수 고정 불가 ([출처](https://github.com/pointfreeco/swift-dependencies)).
- Flutter에서 ViewModel 자체를 mock해서 위젯 테스트 → 로직 회귀 누락 (공식은 실제 ViewModel + fake repo) ([출처](https://docs.flutter.dev/app-architecture/case-study/testing)).

### 3.5 체크리스트
- [ ] 모든 Repository/Service에 인터페이스가 있고 Fake 구현이 있는가
- [ ] 시간(Clock), UUID, 네트워크, 푸시 토큰, 이미지 피커 등 비결정 요소가 주입 가능한가
- [ ] 단위 테스트가 DI 컨테이너 없이 생성자 호출만으로 가능한가
- [ ] Mock은 "호출 자체가 요구사항"인 경우(analytics 전송, 푸시 등록)에만 쓰는가

---

## 4. 모바일 UI 컴포넌트 분리 (Presentational vs Container, State Hoisting, Design System, Slot, Preview)

### 4.1 핵심 개념
- **Stateless(표현) 컴포넌트**: 상태를 파라미터로 받고 변경은 콜백으로 보고. 재사용/테스트 쉬움 ([출처](https://developer.android.com/develop/ui/compose/state-hoisting)).
- **Stateful/Container**: 상태를 소유하거나 ViewModel과 연결하는 얇은 래퍼.
- **State hoisting**: 상태를 호출자로 끌어올림. 규칙: 필요할 때만 hoist, **가장 낮은 공통 조상**으로, 상태는 아래로·이벤트는 위로 ([출처](https://developer.android.com/develop/ui/compose/state-hoisting)).
- React의 lifting state up이 동일 개념 ([출처](https://react.dev/learn/thinking-in-react)).

### 4.2 공식 가이드라인
- ViewModel을 자식 composable에 전달하지 말고 화면 최상단에서만 주입, 하위에는 상태+람다 전달 ([출처](https://developer.android.com/develop/ui/compose/state-hoisting)).
- 비즈니스 로직/화면 UI 상태는 ViewModel, UI 요소 상태(스크롤, 펼침)는 composable 또는 plain state holder ([출처](https://developer.android.com/develop/ui/compose/state-hoisting)).
- 프리뷰는 네트워크/파일 접근/대부분의 ViewModel 의존성 생성이 불가 → **상태와 UI를 분리한 stateless composable**에 샘플 데이터를 넘기는 방식 권장 ([출처](https://developer.android.com/develop/ui/compose/tooling/previews)).
- `@PreviewParameter`로 상태별(Loading/Empty/Error/긴 텍스트) 프리뷰, Multipreview(`@PreviewLightDark`, `@PreviewFontScales`, `@PreviewScreenSizes`)로 다크/폰트/화면 크기 일괄 확인 ([출처](https://developer.android.com/develop/ui/compose/tooling/previews)).
- Flutter View: 비즈니스 로직 금지, ViewModel 1:1. View는 "위젯 하나"가 아니라 위젯 모음 ([출처](https://docs.flutter.dev/app-architecture/guide)).
- React: 컴포넌트는 하나의 일을 해야 하며, props로 정적 렌더 먼저 ([출처](https://react.dev/learn/thinking-in-react)).
- Slot API(Compose의 `content: @Composable () -> Unit`, SwiftUI `@ViewBuilder`, Flutter `child`/`builder`, React `children`/render props): 공식 한 문서로 정리된 출처는 확인 못함 `[미확인]`. 개념은 "컴포넌트가 레이아웃 틀만 제공하고 내용은 호출자가 채움"으로, 디자인 시스템 컴포넌트의 확장 지점으로 쓴다(제안).
- Storybook(RN/Web) 및 SwiftUI Preview 공식 가이드는 이번 조사에서 직접 확인하지 못했다 `[미확인]`.

### 4.3 플랫폼별 매핑 표

| 개념 | Android | iOS | Flutter | RN | Web |
|---|---|---|---|---|---|
| Stateless 컴포넌트 | `PostCard(state, onLike)` | `PostCardView(post:, onLike:)` | `PostCard(post, onLike)` | `<PostCard post onLike/>` | `<PostCard post onLike/>` |
| Container (화면) | `PostFeedRoute(viewModel)` | `PostFeedScreen` (ViewModel 소유) | `PostFeedScreen(viewModel)` | `PostFeedScreen` + hook | `PostFeedPage` + hook |
| 로컬 UI 상태 | `remember`/`rememberSaveable` | `@State` | `StatefulWidget`/hook | `useState` | `useState` |
| Slot | `content: @Composable` | `@ViewBuilder` `[미확인]` | `child`/`builder` `[미확인]` | `children` | `children` |
| 프리뷰 | `@Preview` + ParameterProvider ([출처](https://developer.android.com/develop/ui/compose/tooling/previews)) | `#Preview` `[미확인]` | widgetbook 등 `[미확인]` | Storybook `[미확인]` | Storybook `[미확인]` |
| 이벤트 UI 로직 vs 비즈니스 | UI 로직은 composable, 비즈니스는 ViewModel ([출처](https://developer.android.com/topic/architecture/ui-layer/events)) | 동일 원칙 | View 로직 제한 ([출처](https://docs.flutter.dev/app-architecture/guide)) | 로직은 hook/순수 함수로 분리 ([출처](https://reactnative.dev/docs/testing-overview)) | 동일 |

### 4.4 자주 하는 실수
- 리스트 아이템에 ViewModel을 통째로 전달 ([출처](https://developer.android.com/topic/architecture/ui-layer/events)).
- 디자인 시스템 컴포넌트(Button/Card)가 API 호출·analytics 전송·navigation을 직접 수행.
- 프리뷰 불가능한 컴포넌트(ViewModel 필수) → 상태별 시각 검증 포기.
- 컴포넌트 내부에서 `if (isLiked && !isLoading && ...)` 등 도메인 규칙 판단 (예: 좋아요 가능 조건).
- RN: RNTL 테스트에서 props/state/testID에 의존 → 리팩터링에 취약 ([출처](https://reactnative.dev/docs/testing-overview)).

### 4.5 체크리스트
- [ ] Presentational 컴포넌트가 ViewModel/Repository/Navigator를 import하지 않는가
- [ ] 모든 컴포넌트가 Loading/Empty/Error/긴 텍스트/다크 모드 프리뷰를 갖는가
- [ ] 디자인 시스템 컴포넌트는 토큰(색/타이포/간격)만 사용하고 비즈니스 용어(Post, Sign)를 모르는가
- [ ] 접근성 라벨/식별자가 테스트용으로 부여되어 있는가 (§6)

---

## 5. UI 상태 모델링 (sealed, boolean soup 방지, 단발 이벤트, 불변성)

### 5.1 핵심 개념
- UI 상태는 "화면에 보이는 것의 충실한 표현". 단일 `uiState` 노출 ([출처](https://developer.android.com/topic/architecture/recommendations)).
- 상호 배타적 상태(Loading/Success/Error/Empty)는 sealed 계열로, 병행 가능한 상태는 data class 필드로 ([출처](https://developer.android.com/topic/architecture/recommendations)).
- **Boolean soup**: `isLoading`, `hasError`, `isEmpty`가 동시에 true가 될 수 있는 불가능 상태. 공식 예시(`LoginUiState`)는 boolean 필드를 쓰지만 ([출처](https://developer.android.com/topic/architecture/ui-layer/events)), 상호 배타 상태에는 sealed 사용이 권고된다 ([출처](https://developer.android.com/topic/architecture/recommendations)).

### 5.2 공식 가이드라인
- ViewModel → UI 이벤트는 **항상 UI 상태 갱신으로 표현**. "UI가 무슨 동작을 해야 하는가가 아니라, 그 동작이 UI 상태에 어떤 영향을 주는가를 생각하라" ([출처](https://developer.android.com/topic/architecture/ui-layer/events)).
- 일회성 메시지는 nullable 상태(`userMessage: String?`)로 두고 UI가 표시 후 `userMessageShown()`으로 소비 알림 ([출처](https://developer.android.com/topic/architecture/ui-layer/events)).
- Channel/SharedFlow 같은 이벤트 스트림은 생산자(ViewModel)가 소비자(UI)보다 오래 살면 전달 보장이 안 되므로 지양 ([출처](https://developer.android.com/topic/architecture/ui-layer/events)). "Don't send events from the ViewModel to the UI" — Strongly recommended ([출처](https://developer.android.com/topic/architecture/recommendations)).
- UI 요소 상태 변경은 UI가 직접, 비즈니스 로직이 필요한 이벤트는 ViewModel로 ([출처](https://developer.android.com/topic/architecture/ui-layer/events)).
- 불변성: 공식 문서에 독립 섹션은 없고, `data class` + `val` + `copy()` 로 새 인스턴스를 만들며, 외부에는 읽기 전용 `StateFlow`로 노출 ([출처](https://developer.android.com/topic/architecture/ui-layer/events), [출처](https://developer.android.com/topic/architecture/recommendations)).
- StateFlow 테스트는 가능하면 `value` 속성으로 단언 ([출처](https://developer.android.com/topic/architecture/recommendations)).
- 상태 수집: `collectAsStateWithLifecycle` (Strongly recommended) ([출처](https://developer.android.com/topic/architecture/recommendations)).
- Flutter ViewModel도 UI 상태를 유지하고 View가 재빌드되어도 데이터가 유지되도록 한다 ([출처](https://docs.flutter.dev/app-architecture/guide)).

### 5.3 찰캌 공통 UiState 어휘 (제안)

```text
FeedUiState  = Loading | Empty(topic) | Content(posts, isRefreshing) | Error(kind, retryable)
PostActionUi = Idle | InFlight | Failed(reason)          // 좋아요/사인
UploadUiState= Idle | Picking | Uploading(progress) | Reviewing(AI) | Done(postId) | Rejected(reason) | Failed
transient    = userMessage: String? (consume 후 null)      // 토스트/스낵바
```

### 5.4 플랫폼별 매핑 표

| 개념 | Android | iOS | Flutter | RN/Web (TS) |
|---|---|---|---|---|
| 배타 상태 | `sealed interface` | `enum` with associated values `[미확인]` | `sealed class` (Dart 3) `[미확인]` | discriminated union `{ status: 'loading' }` `[미확인]` |
| 불변 업데이트 | `copy()` | struct 값 복사 `[미확인]` | `copyWith` / freezed `[미확인]` | spread / immer `[미확인]` |
| 상태 노출 | `StateFlow` read-only | `private(set)` property | `ChangeNotifier` getter | hook 반환값 / readonly 타입 |
| 단발 이벤트 | nullable 상태 + `shown()` | 동일 패턴 | 동일 패턴 | 동일 패턴 |
| 네비게이션 | UI가 처리, 검증 결과는 상태로 ([출처](https://developer.android.com/topic/architecture/ui-layer/events)) | 동일 원칙 | 동일 원칙 | 동일 원칙 |

### 5.5 자주 하는 실수
- `isLoading` + `error` + `data` 필드 조합으로 불가능 상태 발생 (로딩 중 에러 표시 등).
- 토스트를 SharedFlow/Channel로 보내고 회전/백그라운드에서 유실 ([출처](https://developer.android.com/topic/architecture/ui-layer/events)).
- 네비게이션을 이벤트로 중복 발화 (뒤로 돌아왔을 때 재이동) ([출처](https://developer.android.com/topic/architecture/ui-layer/events)).
- 가변 컬렉션을 상태로 노출해 UI가 직접 수정.
- 5개 플랫폼의 상태 이름이 제각각 → 패리티 체크 불가. 공통 어휘(§5.3)를 사용.

### 5.6 체크리스트
- [ ] 화면별 UiState가 상호 배타 상태를 sealed/enum/union으로 표현하는가
- [ ] ViewModel→UI 단발 이벤트가 상태(+소비 콜백)로 모델링되었는가
- [ ] 상태 객체가 불변이며 외부에서 수정 불가한가
- [ ] 모든 UiState variant에 프리뷰/테스트가 있는가

---

## 6. 테스트 전략 (피라미드, 단위/UI/스크린샷/계약/E2E)

### 6.1 핵심 개념
- **Test pyramid**: 빠른 단위 테스트 다수, 더 거친 테스트 소수, E2E 매우 소수. "가능한 한 피라미드 아래로 테스트를 내려라". 위 단계 실패 시 아래 단계 테스트 부재를 의심, 가치 없는 상위 테스트는 삭제. 아이스크림 콘 안티패턴 주의 ([출처](https://martinfowler.com/articles/practical-test-pyramid.html)).
- Android 분류: small(unit) / medium(integration) / big(E2E), 그리고 local vs instrumented ([출처](https://developer.android.com/training/testing/fundamentals)).
- **Testable architecture**: 레이어/모듈 분리, thin entry point, 비즈니스 로직에서 프레임워크 의존 제거, 인터페이스+DI. 그렇지 않으면 테스트가 크고 느리고 flaky해진다 ([출처](https://developer.android.com/training/testing/fundamentals)).

### 6.2 플랫폼별 공식 가이드

| 플랫폼 | 단위 | 컴포넌트/UI | E2E/통합 | 근거 |
|---|---|---|---|---|
| Android | ViewModel을 Fake Repository로 JVM 로컬 테스트 | Compose test rule: `createComposeRule()`, finder → action → assertion, semantics 기반, 자동 동기화 | Hilt + `createAndroidComposeRule<HiltTestActivity>()` | [fundamentals](https://developer.android.com/training/testing/fundamentals), [Compose testing](https://developer.android.com/develop/ui/compose/testing), [Hilt testing](https://developer.android.com/training/dependency-injection/hilt-testing) |
| iOS | XCTest 단위 (ViewModel + Fake) `[미확인]` | XCUITest: `XCUIApplication`, accessibility identifier, `waitForExistence` `[미확인: 일반 지식]` | launch argument로 상태 초기화 `[미확인]` | [XCTest UI tests (본문 미확인)](https://developer.apple.com/documentation/xctest/user_interface_tests) |
| Flutter | unit (ViewModel/Repository + Fake) | widget test (단일 위젯, 빠름) | `integration_test` (실기기/에뮬레이터, 느림, 네이티브 UI 제한 → `patrol` 권장) | [Flutter testing overview](https://docs.flutter.dev/testing/overview), [case study testing](https://docs.flutter.dev/app-architecture/case-study/testing) |
| React Native | Jest (unit/integration) | RNTL: 사용자 관점(화면 텍스트/상호작용), props/state/testID 단언 지양 | Detox / Maestro / Appium, 핵심 플로우만 | [RN testing overview](https://reactnative.dev/docs/testing-overview) |
| Web | Jest/Vitest `[미확인]` | React Testing Library `[미확인]` | Playwright: user-facing locator, 테스트 격리, 외부 의존성은 `page.route`로 mock | [Playwright best practices](https://playwright.dev/docs/best-practices) |

Flutter 트레이드오프: unit(신뢰도 낮음/유지비 낮음/빠름) < widget < integration(신뢰도 최고/유지비 최고/느림). 권장은 unit+widget 다수 + 핵심 시나리오 integration ([출처](https://docs.flutter.dev/testing/overview)).
RN: 정적 분석(ESLint, TypeScript)이 첫 단계. 컴포넌트 테스트는 Node.js에서만 돌아 네이티브 버그는 못 잡는다 ([출처](https://reactnative.dev/docs/testing-overview)).

### 6.3 스크린샷 / Golden 테스트
- Flutter: `matchesGoldenFile` 로 위젯 렌더 결과를 기준 이미지와 비교 ([출처](https://api.flutter.dev/flutter/flutter_test/matchesGoldenFile.html)). 공식 testing overview에는 golden 언급이 없다 ([출처](https://docs.flutter.dev/testing/overview)).
- Fowler: 레이아웃 시각 검증은 스크린샷 비교 도구가 도울 수 있으나 사용성은 수동/탐색 테스트 필요 ([출처](https://martinfowler.com/articles/practical-test-pyramid.html)).
- Playwright 시각 비교는 OS·브라우저 버전이 같아야 안정적 ([출처](https://playwright.dev/docs/best-practices)). `toHaveScreenshot` 공식 문서는 직접 확인 못함 `[미확인]`.
- RN snapshot 주의: 큰 스냅샷은 리뷰 불가, 새 스냅샷은 무조건 정답 취급, `--updateSnapshot` 남용은 버그를 숨긴다 ([출처](https://reactnative.dev/docs/testing-overview)).
- Android(Paparazzi/Roborazzi/Compose Preview Screenshot Testing), iOS(swift-snapshot-testing) 도구는 공식 문서를 이번에 확인하지 못했다 `[미확인]`.
- 제안: 스크린샷은 **디자인 시스템 컴포넌트 + UiState variant**에만 적용하고, 5개 플랫폼 간 픽셀 동일성은 요구하지 않는다(폰트 렌더링 차이). 대신 variant 목록의 존재 여부를 패리티로 검증한다.

### 6.4 계약(Contract) 테스트 — 백엔드 API
- Fowler: 외부 HTTP 스텁은 현실과 drift할 수 있으므로 contract test로 보완. Consumer-driven contract(CDC)는 소비자가 기대를 게시하고 제공자가 지속 검증, Pact가 대표 도구 ([출처](https://martinfowler.com/articles/practical-test-pyramid.html)).
- Prism: API 설명 문서로부터 엔드포인트와 검증 규칙을 가진 mock 서버를 제공해 백엔드 준비 전 클라이언트 개발 가능 ([출처](https://docs.stoplight.io/docs/prism/83dbbd75532cf-http-mocking)). 페이지에서 OpenAPI/validation proxy 언급은 확인하지 못했다.
- MSW: 네트워크 레벨에서 요청을 가로채는 별도 mocking 레이어. 개발·통합·E2E에서 같은 mock 재사용 가능. 브라우저(Service Worker)와 Node.js 지원. React Native 지원은 해당 페이지에서 확인 불가 ([출처](https://mswjs.io/docs/)).
- 제안 구조: `openapi.yaml`(백엔드 SSOT) → (a) Prism mock 서버를 모든 클라이언트가 공유, (b) 각 클라이언트 DTO 코드 생성 또는 스키마 검증 테스트, (c) 백엔드 CI에서 응답이 스펙에 맞는지 검증. Schemathesis 등 도구는 공식 문서 미확인 `[미확인]`.

### 6.5 Cross-platform E2E: Maestro 지원 범위 (확인 결과)

| 플랫폼 | 지원 | 근거 |
|---|---|---|
| Android | 에뮬레이터 + 실기기 완전 지원 | [Supported platforms](https://docs.maestro.dev/get-started/supported-platform.md) |
| iOS | **시뮬레이터** 완전 지원 (실기기는 이 페이지에서 확인 불가) | 동일 |
| Flutter / React Native | 프레임워크 전용 계측 없이 **UI 레이어 자동화**로 지원 | 동일 |
| Web | **Beta**, **Chromium만**, 기본 로케일 `en-US`, 뷰포트 변경 제한 | [Web (Desktop Browser)](https://docs.maestro.dev/platform-support/web-desktop-browser) |

- 의미: 같은 YAML flow를 Android/iOS/Flutter/RN에서 공유하기 쉽다. 단 **요소 식별자(접근성 라벨/ID)를 5개 플랫폼에서 동일 규칙**으로 부여해야 한다(제안).
- Web은 Beta이므로 Web E2E는 Playwright를 주력으로, Maestro web은 선택(제안).
- RN 공식 문서도 E2E 도구로 Detox/Maestro/Appium을 나열한다 ([출처](https://reactnative.dev/docs/testing-overview)).
- Flutter `integration_test`는 권한 다이얼로그·알림 등 네이티브 UI를 못 다루므로 `patrol` 권장 ([출처](https://docs.flutter.dev/testing/overview)) — 푸시 알림 권한 플로우는 Maestro/patrol 중 택일 검토.

### 6.6 자주 하는 실수
- 로직 검증을 전부 E2E로 → 아이스크림 콘 ([출처](https://martinfowler.com/articles/practical-test-pyramid.html)).
- 프로덕션 시스템/실서버에 대해 E2E 실행 ([출처](https://martinfowler.com/articles/practical-test-pyramid.html)).
- 구현 상세(내부 호출 순서, testID, state)에 결합한 테스트 ([출처](https://reactnative.dev/docs/testing-overview)).
- `sleep` 고정 대기 (XCUITest는 `waitForExistence`, Compose는 자동 동기화 활용) `[미확인: 일반 지식]`, ([출처](https://developer.android.com/develop/ui/compose/testing)).
- 사소한 getter/setter 테스트, private 메서드를 억지로 테스트 ([출처](https://martinfowler.com/articles/practical-test-pyramid.html)).
- 테스트 간 데이터 공유로 순서 의존 ([출처](https://playwright.dev/docs/best-practices)).

### 6.7 체크리스트
- [ ] 피라미드 비율: 단위(ViewModel/Repository) > 컴포넌트 > 소수 E2E
- [ ] 모든 ViewModel의 UiState 전이가 Fake Repository로 단위 테스트됨
- [ ] 외부 의존성(네트워크/푸시/이미지 업로드/AI 리뷰)은 Fake 또는 mock 서버로 격리됨
- [ ] E2E는 핵심 사용자 여정(로그인 → 토픽 확인 → 사진 업로드 → AI 리뷰 → 좋아요/사인)만
- [ ] 스크린샷 테스트 대상이 디자인 시스템 + 상태 variant로 한정됨
- [ ] 테스트마다 독립 데이터/상태 (격리)

---

## 7. 다중 클라이언트 기능 패리티 검증

> 이 절은 공식 문서가 "패리티"를 직접 다루지 않으므로, 아래 출처가 뒷받침하는 개별 기법을 조합한 **제안**이다.

### 7.1 핵심 개념
"같은 행동"을 정의하는 SSOT를 **구현체가 아닌 명세**에 둔다: (1) 인수 기준(Gherkin), (2) API 스펙(OpenAPI), (3) fixture, (4) analytics 이벤트 명세, (5) 패리티 체크리스트.

### 7.2 공유 인수 기준: Gherkin
- Gherkin 구조: `Feature` → `Scenario`(권장 3~5 step) → `Given`(초기 상태, 사용자 상호작용 지양) / `When`(행동) / `Then`(관찰 가능한 결과, 내부 DB 등 지양), `Background`(공통 사전조건, 짧게), `Scenario Outline` + `Examples` 표 ([출처](https://cucumber.io/docs/gherkin/reference/)).
- 사용법(제안): 시나리오를 `acceptance/*.feature`에 두고, 각 플랫폼은 (a) Gherkin 러너에 바인딩하거나, (b) Maestro flow / Playwright 테스트 / 단위 테스트 이름에 **시나리오 ID**를 달아 매핑 표로 추적한다. 러너를 강제하지 않고 "ID로 추적"만 해도 패리티 가시화에 충분하다.

예시:
```gherkin
Feature: 오늘의 토픽 피드 좋아요
  Scenario: PAR-LIKE-001 좋아요 토글
    Given 오늘의 토픽에 사진 게시물이 1개 있고 내가 좋아요하지 않았다
    When 게시물의 좋아요 버튼을 누른다
    Then 좋아요 수가 1 증가하고 버튼이 선택 상태로 보인다
    And 서버에 좋아요 요청이 1회 전송된다
```
> 도메인 규칙(좋아요 가능 조건 등)은 `docs/business-rules/**`의 현재 규칙에 맞춰야 하며, 규칙이 불명확하면 임의 확정하지 말고 백엔드 확인 대상으로 올린다 (프로젝트 CLAUDE.md 지침).

### 7.3 패리티 매트릭스 (단일 체크리스트)

| Scenario ID | 설명 | 규칙 문서 | iOS | Android | Flutter | RN | Web | 검증 방식 |
|---|---|---|---|---|---|---|---|---|
| PAR-LIKE-001 | 좋아요 토글 | (해당 doc) | ☐ | ☐ | ☐ | ☐ | ☐ | 단위 + E2E |
| PAR-FEED-001 | 피드 Loading/Empty/Error | | ☐ | ☐ | ☐ | ☐ | ☐ | 컴포넌트 + 스크린샷 variant |
| PAR-UPLOAD-001 | 업로드 → AI 리뷰 결과 | | ☐ | ☐ | ☐ | ☐ | ☐ | mock 서버 + E2E |
| PAR-PUSH-001 | 푸시 수신 → 딥링크 | | ☐ | ☐ | ☐ | ☐ | ☐ | 수동/디바이스 E2E |

### 7.4 공유 API mock 서버와 fixture
- **Prism**: OpenAPI 등 API 설명 문서로 mock 서버 구동 ([출처](https://docs.stoplight.io/docs/prism/83dbbd75532cf-http-mocking)).
- **MSW**: Web/Node 테스트·개발·E2E에서 동일 핸들러 재사용 ([출처](https://mswjs.io/docs/)). 모바일 네이티브에는 직접 적용 불가 → 모바일은 Prism/WireMock 류 HTTP mock 서버를 가리키게 구성(제안). WireMock은 Fowler 글이 외부 HTTP 스텁 예시로 언급 ([출처](https://martinfowler.com/articles/practical-test-pyramid.html)).
- **Fixture 규칙(제안)**: `fixtures/` 디렉터리에 JSON 하나를 모든 클라이언트가 공유 (예: `feed_empty.json`, `feed_with_3_posts.json`, `upload_ai_rejected.json`, `error_500.json`). 시나리오 ID와 fixture 이름을 1:1로 맞춘다.
- Playwright는 외부 서비스를 `page.route`로 mock ([출처](https://playwright.dev/docs/best-practices)).
- Fixture/mock이 현실과 drift하지 않도록 백엔드 CI에서 계약 검증 ([출처](https://martinfowler.com/articles/practical-test-pyramid.html)).

### 7.5 Analytics 이벤트 패리티
- 공식 가이드는 없다 `[미확인]`. 제안:
  - `analytics-events.yaml`(이벤트명, 속성, 타입, 트리거 시나리오 ID)을 SSOT로 둔다.
  - 각 클라이언트에서 `AnalyticsClient` 인터페이스 뒤로 숨기고, 테스트에서는 **FakeAnalytics**가 전송 이벤트를 기록해 명세와 비교한다. (호출 자체가 요구사항이므로 spy/mock 성격이 정당한 드문 경우: [Fowler](https://martinfowler.com/articles/mocksArentStubs.html)).
  - Hilt에서는 `FakeAnalyticsModule`을 `@TestInstallIn`으로 교체하는 예가 공식 문서에 있다 ([출처](https://developer.android.com/training/dependency-injection/hilt-testing)).
  - 시간/ID가 들어가는 이벤트는 Clock/UUID 주입으로 결정적으로 만든다 ([출처](https://github.com/pointfreeco/swift-dependencies)).

### 7.6 자주 하는 실수
- 플랫폼별로 각자 시나리오를 새로 작성 → 서로 다른 기대 동작.
- Fixture를 플랫폼마다 복제하고 따로 수정.
- UI 픽셀 동일성을 패리티로 오해 (플랫폼 관용 UI는 허용, 행동·상태·텍스트·이벤트가 동일해야 함 — 팀 합의 필요).
- analytics 이벤트명/속성 철자 불일치.

### 7.7 체크리스트
- [ ] 모든 기능에 Scenario ID가 있고 5개 플랫폼 테스트와 매핑됨
- [ ] 공유 OpenAPI/Prism mock과 fixture를 모든 클라이언트가 사용
- [ ] analytics 이벤트 명세 + Fake 기반 검증 테스트가 있음
- [ ] 릴리스 전 패리티 매트릭스가 모두 체크됨
- [ ] 비즈니스 규칙 변경 시 시나리오/fixture/매트릭스 동시 갱신

---

## 8. 코드 품질 도구 (Lint / Format / CI)

### 8.1 플랫폼별 매핑 표

| 플랫폼 | Lint | Format | 비고/근거 |
|---|---|---|---|
| iOS | SwiftLint (`.swiftlint.yml`, 200+ rules, Build Tool Plugin 권장, CI는 Docker/pre-commit/fastlane) | swift-format `[미확인]` | [SwiftLint](https://github.com/realm/SwiftLint). CI에서 plugin 검증 skip 플래그는 모든 plugin/macro를 암묵 신뢰하는 보안 영향이 있음 (동일 출처) |
| Android | detekt (Kotlin 정적 분석, Gradle/Maven/Bazel, 커스텀 룰) | ktlint `[미확인]` | [detekt](https://detekt.dev/). ktlint 공식 사이트는 가져오기 실패, 미러/README만 검색됨 ([검색 결과 중 README](https://github.com/pinterest/ktlint/blob/c5d19e5d0a88f923ddfd4e9b11c55cbc671327e0/README.md)). Android Lint는 별도 `[미확인]` |
| Flutter | `dart analyze` + `flutter_lints` (`lints`의 superset, Flutter 팀이 권장) | `dart format` `[미확인]` | [Dart linter rules](https://dart.dev/tools/linter-rules); `dart fix`로 일부 자동 수정 (동일 출처) |
| React Native | ESLint + TypeScript (RN 프로젝트에 기본 구성) | Prettier `[미확인]` | [RN testing overview](https://reactnative.dev/docs/testing-overview) |
| Web | ESLint + typescript-eslint (`strict`, `stylistic`, typed linting) | Prettier `[미확인]` | [typescript-eslint](https://typescript-eslint.io/getting-started/) |

### 8.2 CI 구성 제안
1. PR마다: lint/format 검사 → 단위 테스트 → 컴포넌트 테스트(빠른 것) 순.
2. 야간/머지 후: 스크린샷 테스트, E2E(Maestro/Playwright), 계약 검증.
3. 모든 클라이언트에서 Prism/fixture 버전(OpenAPI 해시)을 CI에서 고정해 drift 방지.
4. 패리티 매트릭스 체크: 시나리오 ID 누락 시 CI 실패(스크립트로 테스트 이름에서 ID grep, 제안).
5. TypeScript는 `strict` 모드 + `tseslint.configs.strict` 권장 ([출처](https://typescript-eslint.io/getting-started/)).
- Flutter CI 서비스(Codemagic, Bitrise, Appcircle)가 공식 문서에 예시로 언급됨 ([출처](https://docs.flutter.dev/testing/overview)).

### 8.3 자주 하는 실수
- 플랫폼별 lint 규칙 강도가 달라 한 곳만 경고 무시.
- 포맷 자동화 없이 리뷰에서 스타일 논쟁.
- CI에서 신뢰 검증을 무조건 skip (SwiftLint 문서의 경고) ([출처](https://github.com/realm/SwiftLint)).
- 아키텍처 규칙(UI가 data 계층 import 금지)을 lint로 강제하지 않음 — 커스텀 규칙(detekt 커스텀 룰, SwiftLint custom_rules, ESLint no-restricted-imports 등)으로 보강 가능(제안; [detekt](https://detekt.dev/), [SwiftLint](https://github.com/realm/SwiftLint)).

### 8.4 체크리스트
- [ ] 5개 플랫폼 모두 lint+format이 pre-commit 또는 CI에서 실행
- [ ] TypeScript strict, Dart flutter_lints, SwiftLint, detekt 설정 파일이 커밋됨
- [ ] 레이어 import 규칙이 lint로 강제됨

---

## 9. 찰캌 적용 포인트

1. **공통 어휘 확정**: View / ViewModel(UiState) / Repository(interface, SSOT) / Service(DataSource) / UseCase(선택). 5 플랫폼 코드 리뷰와 문서에서 §1.3 표의 용어만 사용.
2. **UseCase는 기본 생략**: "토픽 + 내 사인 + 좋아요 병합"처럼 여러 Repository 병합이 생길 때만 도입 ([출처](https://developer.android.com/topic/architecture/domain-layer), [출처](https://docs.flutter.dev/app-architecture/guide)).
3. **화면별 UiState 사전**: Feed, PostDetail, Upload(AI Review 포함), Notifications, Signs. 모든 variant 이름을 5 플랫폼이 동일하게 사용 (§5.3). 단발 이벤트(토스트, 이동)는 상태+소비 패턴.
4. **Fake 우선 DI**: `FakePostRepository`, `FakeLikeRepository`, `FakeUploadRepository`, `FakeAiReviewRepository`, `FakePushRegistrar`, `FakeAnalytics`, `FakeClock`. 플랫폼별 DI: Hilt / 이니셜라이저(+swift-dependencies 선택) / provider / Context.
5. **컴포넌트 규율**: 디자인 시스템은 비즈니스 무지(無知). 화면 Container만 ViewModel을 안다. 모든 컴포넌트에 상태별 프리뷰.
6. **테스트 분배**: ViewModel/Repository 단위(최다) → 컴포넌트 → 핵심 여정 E2E 소수. E2E 후보: 업로드→AI 리뷰→게시, 좋아요/사인, 푸시 딥링크.
7. **E2E 도구**: Maestro로 Android/iOS(시뮬레이터)/Flutter/RN 공통 flow, Web은 Playwright 주력(Maestro Web은 Beta·Chromium 한정). 푸시 권한 등 네이티브 다이얼로그는 별도 검토(Flutter는 patrol).
8. **패리티 SSOT**: `acceptance/*.feature`(Scenario ID) + `openapi.yaml` + `fixtures/*.json` + `analytics-events.yaml` + 패리티 매트릭스. 비즈니스 규칙은 `docs/business-rules/**`를 읽기 전용으로 참조하고, 불명확하면 백엔드 확인 대상으로 보고 (CLAUDE.md).
9. **접근성 식별자 규칙**: Maestro/XCUITest/Compose/RNTL/Playwright 모두 사용자 관점 locator를 쓰되, 동일 화면 요소는 5 플랫폼에서 같은 라벨/ID 규칙 사용.
10. **미확인 후속 조사 후보**: Storybook(RN/Web), Compose screenshot testing, swift-snapshot-testing, SwiftUI Preview 공식 가이드, swift-format/ktlint 공식 설정, React Context DI 패턴, Schemathesis/Pact의 모바일 적용.

---

## 10. 출처 목록

**Android**
- https://developer.android.com/topic/architecture — 레이어, SSOT, UDF
- https://developer.android.com/topic/architecture/recommendations — 권장 등급, UiState, StateFlow
- https://developer.android.com/topic/architecture/domain-layer — 선택적 domain layer
- https://developer.android.com/topic/architecture/ui-layer/events — UI events, 단발 이벤트
- https://developer.android.com/develop/ui/compose/state-hoisting — state hoisting
- https://developer.android.com/develop/ui/compose/tooling/previews — Preview, ParameterProvider, Multipreview
- https://developer.android.com/develop/ui/compose/testing — Compose testing
- https://developer.android.com/training/testing/fundamentals — 테스트 범위, 테스트 가능한 아키텍처
- https://developer.android.com/training/testing/fundamentals/test-doubles — Fake 선호
- https://developer.android.com/training/dependency-injection/hilt-testing — Hilt 테스트

**Flutter**
- https://docs.flutter.dev/app-architecture/guide — 아키텍처 가이드
- https://docs.flutter.dev/app-architecture/case-study/testing — fake 기반 테스트
- https://docs.flutter.dev/app-architecture/case-study/dependency-injection — provider DI
- https://docs.flutter.dev/testing/overview — unit/widget/integration
- https://api.flutter.dev/flutter/flutter_test/matchesGoldenFile.html — golden (검색 결과 URL, 본문 미열람)
- https://dart.dev/tools/linter-rules — lints, flutter_lints

**Apple / iOS**
- https://developer.apple.com/documentation/swiftui/managing-model-data-in-your-app — 본문 미확인 (메타만)
- https://developer.apple.com/documentation/xctest/user_interface_tests — 본문 미확인 (메타만)
- https://github.com/pointfreeco/swift-dependencies — swift-dependencies
- https://github.com/realm/SwiftLint — SwiftLint

**React / RN / Web**
- https://react.dev/learn/thinking-in-react — Thinking in React
- https://reactnative.dev/docs/testing-overview — RN 테스트
- https://playwright.dev/docs/best-practices — Playwright
- https://mswjs.io/docs/ — MSW
- https://typescript-eslint.io/getting-started/ — typescript-eslint

**E2E / 계약 / 명세**
- https://docs.maestro.dev/get-started/supported-platform.md — Maestro 지원 플랫폼
- https://docs.maestro.dev/platform-support/web-desktop-browser — Maestro Web (Beta)
- https://docs.stoplight.io/docs/prism/83dbbd75532cf-http-mocking — Prism
- https://cucumber.io/docs/gherkin/reference/ — Gherkin

**일반 이론**
- https://martinfowler.com/articles/practical-test-pyramid.html — 테스트 피라미드, 계약 테스트
- https://martinfowler.com/articles/mocksArentStubs.html — Test double 분류

**기타 도구**
- https://detekt.dev/ — detekt
- https://github.com/pinterest/ktlint/blob/c5d19e5d0a88f923ddfd4e9b11c55cbc671327e0/README.md — ktlint README (검색 결과 URL, 본문 미열람)
