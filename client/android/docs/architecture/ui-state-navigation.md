# UI 상태와 내비게이션 설계

## 요약

> 사용자 입력은 Screen의 구체적인 람다를 통해 ViewModel로 전달한다. ViewModel은 처리 결과를 재현 가능한
> `UiState`로 제공한다. Route는 상태를 수집해 의미 기반 콜백을 호출하고, `ChalkakNavHost`가 콜백을
> 실제 목적지에 연결한다.

```text
사용자 클릭
  → Screen.onLoginClick
  → ViewModel.login()
  → LoginUiState.status = Authenticated
  → Route.onLoginSuccess()
  → ChalkakNavHost가 목적지 결정
  → NavController가 이동 수행
```

## 의사결정 배경

로그인 성공 후 Main으로 이동할 때 ViewModel의 일회성 이벤트를 다음 두 가지로 정의할 수 있다.

```kotlin
LoginUiEvent.LoginSucceeded
LoginUiEvent.NavigateToMain
```

`NavigateToMain`은 UI가 실행할 동작이 명확하지만 ViewModel이 로그인 이후의 앱 흐름을 알게 된다.
목적지가 Onboarding으로 변경되면 ViewModel 이벤트와 Route 처리까지 함께 바뀌는 결합이 생긴다.

`LoginSucceeded`는 피처 결과만 표현하므로 목적지 결합은 줄어든다. 그러나 로그인 완료는 놓치면 안 되는
현재 상태이므로 일회성 이벤트보다 `UiState`로 모델링한다.

## 책임 분리

| 계층 | 책임 |
| --- | --- |
| `XxxScreen` | UI 표시, 사용자 입력 콜백 호출 |
| `XxxViewModel` | 비즈니스 로직, 현재 `UiState` 생성 |
| `XxxRoute` | ViewModel 연결, 상태 수집, 피처 결과 콜백 호출 |
| `ChalkakNavHost` | 피처 결과와 목적지 매핑, 백스택 정책 |
| `NavController` | 실제 내비게이션 수행 |

## 사용자 입력

단순한 Compose + ViewModel 구조에서는 Screen의 람다를 ViewModel 함수에 바로 연결한다.

```kotlin
LoginScreen(
    uiState = uiState,
    onEmailChange = viewModel::updateEmail,
    onPasswordChange = viewModel::updatePassword,
    onLoginClick = viewModel::login,
)
```

Screen은 ViewModel이나 내비게이션 구현을 알지 않는다.

```kotlin
@Composable
fun LoginScreen(
    uiState: LoginUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onLoginClick: () -> Unit,
    modifier: Modifier = Modifier,
)
```

`UiAction.LoginClicked`와 통합 `onAction()`은 다음과 같은 요구가 있을 때만 도입한다.

- MVI/Reducer를 프로젝트 표준으로 사용한다.
- 모든 액션에 공통 middleware나 interceptor가 필요하다.
- 액션 기록, 재생 또는 공통 로깅이 필요하다.
- 복잡한 상태 머신의 입력을 하나의 reducer로 관리해야 한다.

그 외에는 개별 람다가 더 작고 읽기 쉽다.

## ViewModel 출력

로그인 결과는 상태로 표현한다. 서로 모순된 Boolean 조합을 피하기 위해 명시적인 상태 타입을 우선한다.

```kotlin
data class LoginUiState(
    val status: LoginStatus = LoginStatus.Idle,
)

sealed interface LoginStatus {
    data object Idle : LoginStatus
    data object Loading : LoginStatus
    data object Authenticated : LoginStatus
    data class Failed(val message: String) : LoginStatus
}
```

ViewModel은 목적지를 지시하지 않고 현재 피처 상태만 갱신한다.

```kotlin
fun login() {
    viewModelScope.launch {
        updateState { copy(status = LoginStatus.Loading) }

        try {
            loginUseCase()
            updateState { copy(status = LoginStatus.Authenticated) }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            updateState {
                copy(status = LoginStatus.Failed(error.toUserMessage()))
            }
        }
    }
}
```

### 일회성 effect 예외

모든 순간적 UI 표현을 기계적으로 금지하는 규칙은 아니다. 다만 ViewModel이 UI보다 오래 살아남는 구조에서
`Channel`/`SharedFlow` 일회성 전달은 유실·중복·소비 시점 문제를 만들 수 있다.

일회성 effect를 사용하기 전에 다음을 확인한다.

- 이 값이 실제로 현재 UI 또는 앱 상태를 의미하지 않는가?
- 소비자가 없을 때 유실되어도 상태 불일치가 없는가?
- 화면 재생성, Lifecycle 정지, 복수 collector에서 안전한가?
- 소비 완료를 별도 상태로 반영해야 하는가?

로그인, 결제, 가입, 저장 완료처럼 놓치면 안 되는 결과는 effect에만 두지 않는다.

## Route와 내비게이션

Route는 상태를 수집하고 피처 결과를 상위 계층에 알린다.

```kotlin
@Composable
fun LoginRoute(
    onLoginSuccess: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LoginViewModel = viewModel(factory = LoginViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.status) {
        if (uiState.status == LoginStatus.Authenticated) {
            onLoginSuccess()
        }
    }

    LoginScreen(
        uiState = uiState,
        onLoginClick = viewModel::login,
        modifier = modifier,
    )
}
```

Route 콜백은 현재 목적지보다 피처의 의미를 표현한다.

| 권장 | 지양 |
| --- | --- |
| `onLoginSuccess` | `onNavigateToMain` |
| `onRegistrationCompleted` | `onNavigateToOnboarding` |
| `onTopicClick` | Screen에 `NavController` 전달 |

사용자가 Settings나 Help처럼 명시적인 목적지를 직접 선택하는 클릭은 `onSettingsClick`,
`onHelpClick`처럼 목적이 드러나는 이름을 사용할 수 있다. 핵심은 Screen이 실제 내비게이션 구현을
모르는 것이다.

NavHost는 피처 결과를 현재 앱 정책의 목적지에 연결한다.

```kotlin
composable<Login> {
    LoginRoute(
        onLoginSuccess = {
            navController.navigate(Today) {
                popUpTo<Login> { inclusive = true }
            }
        },
    )
}
```

로그인 후 Onboarding이 필요하도록 정책이 바뀌면 NavHost의 매핑을 바꾼다.

```kotlin
LoginRoute(
    onLoginSuccess = {
        navController.navigate(Terms)
    },
)
```

## 반복 내비게이션

`UiState` 기반 effect는 구성 변경이나 백스택 복귀 후 다시 관찰될 수 있다.

- 완료 후 이전 화면이 필요 없으면 백스택에서 제거한다.
- 이전 화면을 유지해야 하면 “이동할 사용자 의도가 아직 유효한가”를 UI/내비게이션 계층에서 관리한다.
- 반복 이동을 막기 위해 ViewModel에 구체적인 목적지를 추가하지 않는다.

## 기존 코드와의 공존

이 문서는 새 구현과 관련 코드를 수정할 때의 수렴 방향이다. 기존 `UiEvent`를 모두 한 번에 바꾸는
대규모 리팩터링을 자동으로 허가하지 않는다. 요청된 피처와 직접 연관된 범위에서만 이 구조로
수렴시키고 나머지는 별도 작업으로 다룬다.

## 참고 자료

- [Android UI events](https://developer.android.com/topic/architecture/ui-layer/events)
  - 로그인 결과를 UI state로 표현하는 예제와 일회성 이벤트 전달의 한계를 설명한다.
- [Android UI layer](https://developer.android.com/topic/architecture/ui-layer)
  - 사용자 이벤트는 위로, 상태는 아래로 흐르는 UDF와 SSOT 원칙을 설명한다.
- [Test Compose navigation](https://developer.android.com/guide/navigation/testing/compose)
  - Screen에 `NavController`를 전달하지 않고 내비게이션 콜백을 주입하도록 권장한다.
- [Compose UI Architecture](https://developer.android.com/develop/ui/compose/architecture)
  - Compose의 단방향 데이터 흐름과 상태 홀더 구조를 설명한다.
- [Now in Android 모듈화 문서](https://github.com/android/nowinandroid/blob/main/docs/ModularizationLearningJourney.md)
  - 앱 내비게이션, feature API/impl, 목적지 키의 책임을 설명한다.
- [Now in Android `ForYouEntryProvider`](https://github.com/android/nowinandroid/blob/7d45eae4f8720a0c77f507712ba2437ff974b6ed/feature/foryou/impl/src/main/kotlin/com/google/samples/apps/nowinandroid/feature/foryou/impl/navigation/ForYouEntryProvider.kt)
  - Screen에 `Navigator`를 넘기지 않고 `onTopicClick = navigator::navigateToTopic`으로 연결한다.
