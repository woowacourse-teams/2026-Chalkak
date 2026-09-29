# Chalkak Android — 프로젝트 메모리

> MUST: PR 차단 사유. DEFAULT: 기본값, 이유 있으면 벗어나도 됨. SMELL: 위반 아님, 분리 검토 신호.
> 포맷·개행·줄길이는 `.editorconfig` + ktlint가 강제한다. 아래 MUST 중 일부는 Konsist 테스트가 검사하며, 나머지는 구현·리뷰에서 판단한다.

## 자동 검사 — ktlint·Konsist
- Android 코드를 수정한 뒤 `client/android`에서 `./gradlew ktlintCheck testDebugUnitTest`를 실행한다. CI에서도 두 작업을 실행한다.
- Konsist는 `app/src/test/java/com/stonefive/chalkak/ArchitectureKonsistTest.kt`에서
  feature → data, domain → feature/data/core, core.designsystem → feature 의존과 domain의 Android API 사용을 검사한다.
- `app/src/test/java/com/stonefive/chalkak/ComposeKonsistTest.kt`는 `@Composable`인 `*Screen` 함수가
  ViewModel·NavController를 파라미터나 본문에서 직접 사용하지 않는지 검사한다.
- 테스트에 없는 Compose 동작·UI 상태·내비게이션 규칙까지 Konsist가 보장한다고 간주하지 않는다.

## MUST — 아키텍처
- 의존 방향: `feature → domain ← data`, `feature/data → core`
  - feature는 data를 직접 참조 금지. DTO를 UI로 끌어오지 않는다.
  - domain은 `android.*` import 금지.
  - `core.designsystem`은 어떤 feature도 참조 안 함.
- **공통 컴포넌트 재사용** — 피드 전용 이미지 등 중복 구현 금지.
  `ChalkakSignedImage` 등 `core.designsystem`·`Post` 모델을 재사용한다.

## DEFAULT — 피처 구조
- 피처 내부는 기본적으로 다음 구조를 따른다.
  - `XxxRoute`
  - `XxxScreen`
  - `XxxViewModel`
  - `XxxUiState`
  - `component/`

## MUST — Compose
- Route(stateful) / Screen(stateless) 2계층으로 분리한다.
  - ❌ ViewModel·NavController를 Route 아래로 전달하지 않는다.
  - ❌ Screen 내부에서 `hiltViewModel()`·`LocalContext`로 SideEffect를 실행하지 않는다.
- **레이아웃 책임 분리** — `statusBarsPadding`, 외부 여백, `fillMaxWidth`는 화면 부모가 관리한다.
  하위 컴포넌트는 내부 배치만 담당하고 자기 여백/폭을 스스로 정하지 않는다.
- **디자인 시스템 토큰만 사용** — 하드코딩 `Color(0x..)`, `16.dp` 리터럴, 인라인 `TextStyle` 금지.
  타이포/스페이싱/컬러는 designsystem 토큰에서 가져온다.
  - Figma 고정 dp는 의미 있는 상수로 둔다.
- **비즈니스 상태 = ViewModel** — 비즈니스/화면 상태는 ViewModel의 `UiState`로 관리하고,
  화면에는 `StateFlow`를 `collectAsStateWithLifecycle`로 전달한다.
- 모든 public UI Composable은 `modifier: Modifier = Modifier`를 선택 파라미터 중 첫 번째로 받고,
  가장 바깥 노드에 **최초로** 적용한다.
  - ✅ `Column(modifier = modifier.padding(16.dp))`
  - ❌ `Column(modifier = Modifier.padding(16.dp).then(modifier))`
- 리컴포지션 경로(본문)에서 상태 변경·로깅·네트워크 호출 금지.

## DEFAULT — Compose
- 파라미터 순서:
  `필수 데이터 → 필수 람다 → modifier → 나머지 선택 → trailing content`
- `LaunchedEffect`에는 실제 의존값을 key로 사용한다.
  - `LaunchedEffect(Unit)`은 리뷰에서 정당화가 필요하다.

## SMELL — Compose
다음 조건은 즉시 위반으로 보지는 않지만, 컴포넌트 분리를 검토해야 하는 신호다.

- `when(uiState)` 분기 3개 초과
- 중첩 깊이 4 초과
- 한 Composable이 약 100줄 초과

## MUST — 프리뷰
- **모든 stateless Screen + 재사용/디자인시스템 컴포넌트**는 `@Preview`를 갖는다.
  - private 헬퍼까지 강제하지는 않는다.
