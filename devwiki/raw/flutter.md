---
title: Flutter 앱 개발 (Flutter/Dart 3) 리서치 노트
topic: 찰캌(Chalkak) Flutter(iOS/Android) 재구현을 위한 Flutter 개발 가이드
collected: 2026-10-27
sources:
  - https://docs.flutter.dev/app-architecture/guide
  - https://docs.flutter.dev/app-architecture/design-patterns/result
  - https://docs.flutter.dev/app-architecture/design-patterns/command
  - https://docs.flutter.dev/app-architecture/design-patterns/optimistic-state
  - https://docs.flutter.dev/app-architecture/design-patterns/offline-first
  - https://docs.flutter.dev/data-and-backend/state-mgmt/options
  - https://bloclibrary.dev/why-bloc/
  - https://docs.flutter.dev/perf/best-practices
  - https://docs.flutter.dev/perf/impeller
  - https://dart.dev/tools/linter-rules/use_build_context_synchronously
  - https://docs.flutter.dev/cookbook/networking/background-parsing
  - https://docs.flutter.dev/platform-integration/platform-channels
  - https://docs.flutter.dev/packages-and-plugins/developing-packages
  - https://pub.dev/help/scoring
  - https://docs.flutter.dev/ui/adaptive-responsive/capabilities
  - https://docs.flutter.dev/ui/navigation/deep-linking
  - https://pub.dev/packages/go_router
  - https://pub.dev/packages/image_picker
  - https://pub.dev/packages/cached_network_image
  - https://firebase.google.com/docs/cloud-messaging/flutter/get-started
  - https://firebase.google.com/docs/cloud-messaging/flutter/receive-messages
  - https://pub.dev/packages/permission_handler
  - https://docs.flutter.dev/deployment/obfuscate
  - https://docs.flutter.dev/deployment/flavors
  - https://docs.flutter.dev/testing/overview
  - https://pub.dev/packages/mocktail
  - https://docs.flutter.dev/ui/accessibility-and-localization/accessibility
---

# Flutter 앱 개발 리서치 노트 (찰캌 Flutter 재구현용)

> 표기 규칙: `([출처](URL))`가 붙은 항목은 해당 공식 페이지에서 직접 확인한 내용이다. **[일반 지식]** 표시가 붙은 항목은 페이지에서 직접 확인하지 못했거나(수집 중 해당 페이지 fetch 실패/미기재) 널리 알려진 관행이므로 구현 전에 공식 문서로 재확인이 필요하다. 일부 페이지(riverpod.dev, docs.flutter.dev/flavors-ios 등)는 수집 중 접근하지 못했다.

---

## 1. 앱 아키텍처 (Flutter "Guide to app architecture")

### 핵심 개념
- 관심사 분리(separation of concerns)로 앱을 **UI layer**와 **Data layer** 두 층으로 나눈다. 구성요소는 View, ViewModel, Repository, Service이며 feature 하나는 보통 View 1 + ViewModel 1 + Repository 1개 이상 + Service 0개 이상이다. ([출처](https://docs.flutter.dev/app-architecture/guide))
- MVVM 대응: View + ViewModel = UI layer, Repository + Service = model(data) layer. ([출처](https://docs.flutter.dev/app-architecture/guide))
- **View**: 위젯 조합. 비즈니스 로직을 넣지 않고 ViewModel에서 받은 데이터만 렌더링. 허용되는 로직은 ViewModel 플래그/nullable 기반의 단순 if, 애니메이션, 화면 크기/방향 기반 레이아웃, 단순 라우팅뿐. ([출처](https://docs.flutter.dev/app-architecture/guide))
- **ViewModel**: 앱 로직의 대부분. Repository 데이터를 UI 상태로 변환(필터/정렬/결합), UI 상태 유지, View가 호출하는 **command**(함수) 노출. Flutter 위젯 없이 테스트 가능. View:ViewModel = 1:1. ([출처](https://docs.flutter.dev/app-architecture/guide))
- **Repository**: 모델 데이터의 **single source of truth(SSOT)**. Service의 raw 데이터를 domain model로 변환하고 캐싱, 에러 처리, 재시도, 갱신/폴링을 담당. **Repository끼리는 서로를 알면 안 된다**(두 Repository 데이터가 필요하면 ViewModel 또는 domain layer에서 결합). 로그인 세션 같은 앱 전역 상태의 위치로도 적합. ([출처](https://docs.flutter.dev/app-architecture/guide))
- **Service**: 가장 낮은 층. API 엔드포인트/플랫폼 API/로컬 파일을 감싸 `Future`/`Stream`을 노출. **상태를 가지지 않음**. ([출처](https://docs.flutter.dev/app-architecture/guide))
- **Domain layer(use-case/interactor)** 는 선택. 여러 Repository 결합, 매우 복잡한 로직, 여러 ViewModel에서 재사용될 때만 추가. 모든 데이터 접근마다 만들지 말 것. ([출처](https://docs.flutter.dev/app-architecture/guide))
- 가이드는 "규칙이 아니라 가이드라인"이며 앱 요구에 맞게 조정하라고 명시한다. ([출처](https://docs.flutter.dev/app-architecture/guide))

### 공식 가이드라인(권장 방식)
**Result 타입** — Dart 예외는 unchecked라 호출자가 처리를 잊기 쉽다. `sealed class Result<T>` + `Ok<T>`/`Error<T>`를 반환하고 ViewModel에서 `switch`로 exhaustive하게 처리한다. Service는 try-catch로 감싸 `Result.ok/Result.error`를 반환하고 Repository 같은 중간 계층은 그대로 전달한다. 폴백 로직(API 실패 → 로컬)도 중첩 try-catch 대신 `Result` 분기로 단순화된다. pub.dev에 `result_dart`, `result_type`, `multiple_result` 구현이 있다. ([출처](https://docs.flutter.dev/app-architecture/design-patterns/result))

```dart
sealed class Result<T> { const Result(); }
final class Ok<T> extends Result<T> { const Ok._(this.value); final T value; }
final class Error<T> extends Result<T> { const Error._(this.error); final Exception error; }
// ViewModel
switch (result) { case Ok<UserProfile>(): ...; case Error<UserProfile>(): ...; }
```

**Command 패턴** — ViewModel 액션을 감싸 `running`/`error`/`completed`/`result` 상태를 노출하는 `ChangeNotifier` 클래스(`Command0<T>`, `Command1<T, A>`). 규칙: ① `running`이면 재실행 무시(중복 탭 방지) ② 시작 시 `running=true`, 결과 초기화 ③ `finally`에서 `running=false` ④ UI 부수효과(SnackBar 등) 처리 후 `clearResult()` 호출 안 하면 notify마다 반복됨 ⑤ 액션은 `Result<T>` 반환. View는 ViewModel이 아니라 **command를 `ListenableBuilder`로 listen**한다. ([출처](https://docs.flutter.dev/app-architecture/design-patterns/command))

**Optimistic state 레시피** — 요청 완료 전에 성공 상태를 즉시 UI에 반영하고, 실패 시 롤백 + 오류 안내. ViewModel 패턴: `if (already) return; flag = true; notifyListeners(); try { await repo.call(); } catch { flag = false; error = true; } finally { notifyListeners(); }`. 위젯은 `initState`에서 listener 등록, `dispose`에서 해제, error 플래그를 소비 후 리셋. 확장: pending(전송 중) 3번째 상태를 두거나 Command의 running 상태 활용. ([출처](https://docs.flutter.dev/app-architecture/design-patterns/optimistic-state))

**Offline-first 레시피** — Repository가 로컬(DB Service)과 원격(API Service)을 결합하는 SSOT. 읽기 전략: (a) 로컬 폴백 (b) **Stream으로 로컬 먼저 emit 후 원격 갱신값 emit (권장)** (c) 로컬 전용 + 별도 `sync()`. 쓰기 전략: online-only(성공 시 DB 저장) vs offline-first(DB 먼저 저장 후 API; 불일치 가능 → 동기화 필요). 동기화: `Timer.periodic`, `synchronized` 플래그, `workmanager` 백그라운드 작업(배터리 주의, `connectivity_plus`/`battery_plus`로 조건 확인), FCM 서버 푸시와 결합. ([출처](https://docs.flutter.dev/app-architecture/design-patterns/offline-first))

### 자주 하는 실수·놓치기 쉬운 점
- View/위젯에 API 호출·분기 로직을 넣음 → 테스트/재사용 불가. ([출처](https://docs.flutter.dev/app-architecture/guide))
- Repository가 다른 Repository에 의존. ([출처](https://docs.flutter.dev/app-architecture/guide))
- Service에 상태를 넣음. ([출처](https://docs.flutter.dev/app-architecture/guide))
- Command 결과를 `clearResult()` 하지 않아 SnackBar/네비게이션이 반복 실행. ([출처](https://docs.flutter.dev/app-architecture/design-patterns/command))
- Optimistic 롤백 시 `error` 플래그를 리셋하지 않아 메시지 중복. ([출처](https://docs.flutter.dev/app-architecture/design-patterns/optimistic-state))
- 쓰기를 offline-first로 할 때 동기화 전략 없이 로컬/원격이 어긋남. ([출처](https://docs.flutter.dev/app-architecture/design-patterns/offline-first))

### 체크리스트
- [ ] feature별 View/ViewModel/Repository/Service 파일 구조 정의
- [ ] Service는 `Result<T>` 반환, ViewModel에서 exhaustive `switch`
- [ ] 좋아요/사인 등 즉시 반응 액션은 optimistic + 롤백 + 중복탭 방지
- [ ] 피드 읽기는 Stream(캐시 → 원격) 고려
- [ ] 세션/현재 사용자는 Repository(Stream)로 노출

---

## 2. 상태 관리 옵션 (Provider/ChangeNotifier, Riverpod, Bloc)

### 핵심 개념
- 공식 문서의 옵션 페이지는 내장 방식(`setState`, `ValueNotifier`/`InheritedNotifier`, `InheritedWidget`/`InheritedModel`)과 "커뮤니티 패키지는 보일러플레이트 감소, 디버깅 도구, 일관된 아키텍처에 도움이 될 수 있으며 최선의 선택은 앱 복잡도와 팀 선호에 달렸다"고만 말하고 특정 패키지를 추천하지 않는다. `package:provider`는 `InheritedWidget` 위에 구축되었다고만 언급. ([출처](https://docs.flutter.dev/data-and-backend/state-mgmt/options))
- 공식 아키텍처 레시피의 ViewModel은 `ChangeNotifier` + `ListenableBuilder`를 사용한다(별도 패키지 불필요). ([출처](https://docs.flutter.dev/app-architecture/design-patterns/optimistic-state))
- **Bloc**: 프레젠테이션과 비즈니스 로직 분리, 상태 변경 경로를 하나로 강제해 예측 가능하고 모든 케이스를 테스트 가능. 한 코드베이스에서 여러 개발자가 같은 패턴으로 협업하기 좋음. 다만 "완벽한 상태 관리는 없고 팀/프로젝트에 따라 선택"이라고 문서가 명시. ([출처](https://bloclibrary.dev/why-bloc/))

### 비교(트레이드오프) — **[일반 지식]** (Riverpod 공식 페이지는 수집 실패)
| 옵션 | 장점 | 단점 |
|---|---|---|
| Provider + ChangeNotifier | 공식 아키텍처 예제와 동일, 학습 곡선 낮음, 보일러플레이트 적음 | 위젯 트리(BuildContext) 의존, 세밀한 rebuild 제어는 수동(`Selector`, 분리된 notifier) |
| Riverpod | 컴파일 타임 안전, BuildContext 비의존, 캐싱/비동기(`AsyncValue`) 내장, 테스트 시 override 용이 | 개념(Provider 종류, 코드 생성 옵션) 학습 비용 |
| Bloc/Cubit | 이벤트→상태 흐름 명시, 대규모 팀 일관성, 로깅/테스트 | 보일러플레이트 많음, 단순 화면엔 과함 |

### 자주 하는 실수
- 전역 가변 싱글톤으로 상태를 두고 ChangeNotifier를 `notifyListeners()` 없이 변경. **[일반 지식]**
- 한 개의 거대한 notifier에 모든 상태를 넣어 불필요한 rebuild 유발(→ command/feature 단위 분리). ([출처](https://docs.flutter.dev/app-architecture/design-patterns/command) 참고)
- `dispose()`에서 listener 해제를 잊음. ([출처](https://docs.flutter.dev/app-architecture/design-patterns/optimistic-state))

### 체크리스트
- [ ] 팀 규모/숙련도로 한 가지를 선택하고 프로젝트 전반에 통일 (공식 가이드 호환성은 Provider+ChangeNotifier가 가장 직접적)
- [ ] 선택한 패키지가 Repository/Service 계층과 무관하게(View 의존 없이) 동작하도록 구성

---

## 3. 위젯 rebuild 성능

### 핵심 개념
- `build()`는 조상이 rebuild되면 자주 호출되므로 **반복적·비싼 작업을 넣지 않는다**. ([출처](https://docs.flutter.dev/perf/best-practices))

### 공식 가이드라인
- 큰 위젯을 캡슐화/변경 빈도 기준으로 작은 위젯으로 분리. `setState()`는 실제로 바뀌는 가장 작은 서브트리에서 호출. ([출처](https://docs.flutter.dev/perf/best-practices))
- 재사용 UI는 helper 함수보다 `StatelessWidget` 선호. ([출처](https://docs.flutter.dev/perf/best-practices))
- 변하지 않는 자식 인스턴스를 재사용(예: `AnimatedBuilder`의 `child` 파라미터)해 rebuild 전파를 막는다. ([출처](https://docs.flutter.dev/perf/best-practices))
- 목록/그리드는 `ListView.builder` 등 lazy builder. 구체적 `List` children을 받는 생성자는 대부분이 화면 밖일 때 피한다. ([출처](https://docs.flutter.dev/perf/best-practices))
- `saveLayer()` 유발 요소(`ShaderMask`, `ColorFilter`, overflowShader가 있는 `Text` 등), `Opacity`, clipping을 남용하지 말 것. 둥근 사각형은 clip 대신 `borderRadius`, 애니메이션 투명도는 `AnimatedOpacity`/`FadeInImage`. ([출처](https://docs.flutter.dev/perf/best-practices))
- intrinsic layout pass(셀 크기 동적 통일)는 비쌈. 프레임 예산: 60Hz 기준 build/render 각 약 16ms, 120Hz 기기는 합계 8ms 이내. ([출처](https://docs.flutter.dev/perf/best-practices))
- 위젯에 `operator ==`를 오버라이드하지 말 것(O(N²) 위험). ([출처](https://docs.flutter.dev/perf/best-practices))
- `const` 생성자, `Key` 사용 요령: `const` 위젯은 rebuild 시 동일 인스턴스로 건너뛰어짐, 리스트 아이템 재정렬/삭제에는 `ValueKey(id)` 사용. **[일반 지식]**

### 자주 하는 실수
- `build()` 안에서 `Future`를 생성(`FutureBuilder(future: api())`) → rebuild마다 재호출. **[일반 지식]**
- `build()`에서 정렬/파싱/포맷팅 등 계산, 부작용(네비게이션, setState). **[일반 지식]**
- 리스트 아이템 `key` 누락으로 상태가 엉뚱한 아이템에 붙음. **[일반 지식]**
- 이미지 오버레이/그라디언트 위에 `Opacity` 중첩.  ([출처](https://docs.flutter.dev/perf/best-practices))

### 체크리스트
- [ ] 정적 서브트리에 `const`, `prefer_const_constructors` lint 활성화
- [ ] 피드는 `ListView.builder`/`SliverList`, 아이템에 `ValueKey`
- [ ] DevTools로 rebuild/Track layouts/saveLayer 확인(프로파일 모드, 실기기)

---

## 4. BuildContext와 async gap (mounted 체크)

### 핵심 개념
- `BuildContext`를 async gap(`await` 이후) 너머로 쓰면 위젯이 이미 dispose된 상태일 수 있어 진단 어려운 크래시가 난다. Dart linter 규칙 `use_build_context_synchronously`가 경고한다. ([출처](https://dart.dev/tools/linter-rules/use_build_context_synchronously))

### 공식 가이드라인
- `State`의 `context`라면 **State의 `mounted`**, 지역 변수/인자로 받은 context라면 **`context.mounted`**를 `await` 이후 확인하고 early return. ([출처](https://dart.dev/tools/linter-rules/use_build_context_synchronously))
- 규칙은 `analysis_options.yaml`의 `linter > rules`에 추가. ([출처](https://dart.dev/tools/linter-rules/use_build_context_synchronously))
- 더 나은 구조: 비동기 결과를 ViewModel/Command가 상태로 노출하고 View는 listener에서 SnackBar/네비게이션 처리(§1 Command 참조). ([출처](https://docs.flutter.dev/app-architecture/design-patterns/command))

```dart
await repo.save();
if (!context.mounted) return;   // State 안에서는 mounted
Navigator.of(context).pop();
```

### 자주 하는 실수
- 이미지 선택/권한 요청/업로드 `await` 뒤 `ScaffoldMessenger.of(context)`, `Navigator.pop` 호출 시 mounted 미확인.
- 컨텍스트를 필드에 저장해 나중에 사용. ([출처](https://dart.dev/tools/linter-rules/use_build_context_synchronously))

### 체크리스트
- [ ] `use_build_context_synchronously` lint를 error로 설정
- [ ] 업로드/권한 플로우 await 뒤에 항상 mounted 확인

---

## 5. Isolates / compute

### 핵심 개념
- Dart는 기본 단일 스레드. 약 16ms를 넘는 무거운 작업(대용량 JSON 파싱 등)은 jank를 유발하므로 백그라운드 isolate로 옮긴다. ([출처](https://docs.flutter.dev/cookbook/networking/background-parsing))

### 공식 가이드라인
- `compute(topLevelFn, message)`는 top-level 함수를 백그라운드 isolate에서 실행. 메시지는 원시값이나 단순 객체여야 하며 `Future`/`http.Response` 같은 복잡한 객체는 전달 불가(문서 예시는 `response.body` String만 전달). ([출처](https://docs.flutter.dev/cookbook/networking/background-parsing))
- 플러그인/플랫폼 채널은 root isolate 또는 등록된 background isolate에서 동작. 백그라운드 isolate에서 플러그인을 쓰려면 `RootIsolateToken`을 넘겨 `BackgroundIsolateBinaryMessenger.ensureInitialized`를 호출. ([출처](https://docs.flutter.dev/platform-integration/platform-channels))
- `Isolate.run`은 최신 Dart의 간편 API **[일반 지식 — 수집한 cookbook 페이지는 compute만 설명]**.

### 자주 하는 실수
- 클로저가 큰 객체/위젯 참조를 캡처한 채로 isolate에 전달. **[일반 지식]**
- UI 객체·소켓 등 전송 불가 타입 전달. ([출처](https://docs.flutter.dev/cookbook/networking/background-parsing))
- 이미지 리사이즈/압축을 메인 isolate에서 수행(플러그인이 이미 네이티브 스레드에서 처리하는지 확인).

### 체크리스트
- [ ] 큰 JSON, 이미지 디코딩/가공은 isolate 사용 여부 프로파일링 후 결정
- [ ] 백그라운드 isolate에서 플러그인 사용 시 RootIsolateToken 초기화

---

## 6. Platform channel & 플러그인 선택

### 핵심 개념
- `MethodChannel`은 Dart↔네이티브 비동기 메서드 호출. 채널 이름은 앱 내 고유(도메인 prefix). 타입 안전하지 않으므로 호스트/클라이언트가 메서드명·인자 타입을 맞춰야 하며, 안전한 대안으로 **Pigeon**이 타입 안전 코드를 생성. ([출처](https://docs.flutter.dev/platform-integration/platform-channels))
- 스레딩: 네이티브→Flutter 호출은 플랫폼 메인 스레드에서, 핸들러를 메인 외 스레드에서 돌리려면 Task Queue API 사용. ([출처](https://docs.flutter.dev/platform-integration/platform-channels))
- iOS: `UISceneDelegate` 라이프사이클(Flutter 3.41+ 기본)에서는 채널을 `didInitializeImplicitFlutterEngine(_:)`에서 만든다(`didFinishLaunching`에서는 `window`가 nil). ([출처](https://docs.flutter.dev/platform-integration/platform-channels))
- **Federated plugin** = app-facing interface + platform interface + platform implementation(s). endorsed 구현은 `default_package`로 자동 포함, non-endorsed는 `pubspec.yaml`에 직접 추가(endorsed 오버라이드도 가능). `dart:ffi` 기반 FFI 패키지는 Flutter 3.38부터 네이티브 코드 번들링에 권장. ([출처](https://docs.flutter.dev/packages-and-plugins/developing-packages))

### 플러그인 선택 기준 (pub.dev)
- pub points(6개 카테고리: 파일 규약, 문서, 플랫폼 지원, 최신 툴체인(iOS/macOS 플러그인의 Swift Package Manager 지원 등), 정적 분석, 최신 의존성), likes(개발자 선호), downloads(인기 지표일 뿐 사용자 수 아님)를 **함께** 본다. 대상 플랫폼 지원 여부, 예제/문서, 최신 SDK 호환성도 확인. ([출처](https://pub.dev/help/scoring))
- 추가 확인 **[일반 지식]**: publisher 인증(verified publisher), 마지막 릴리스 날짜, 이슈 활성도, 유지보수 주체(flutter.dev / firebase.google.com 퍼블리셔 우선).

### 자주 하는 실수
- 플랫폼 채널 반환 타입을 잘못 가정(예: int 32bit 초과는 Kotlin `Long`). ([출처](https://docs.flutter.dev/platform-integration/platform-channels))
- 유지보수가 끊긴 플러그인에 핵심 기능(푸시, 업로드) 의존. **[일반 지식]**

### 체크리스트
- [ ] 의존 플러그인별 pub points / 플랫폼 지원 / 최신 릴리스 확인
- [ ] 커스텀 네이티브 연동은 Pigeon 우선 검토

---

## 7. Impeller

### 핵심 개념
- **iOS**: Impeller가 유일한 지원 렌더러(Skia로 되돌릴 수 없음). **Android**: API 29+ 기기에서 기본 활성; 낮은 API 레벨이나 Vulkan 미지원 기기는 레거시 OpenGL 렌더러로 자동 폴백. 데스크톱은 Flutter 3.47 기준 기본 활성, 웹은 Skia. ([출처](https://docs.flutter.dev/perf/impeller))
- 셰이더/파이프라인 상태를 엔진 빌드 시점에 미리 컴파일하는 것이 설계 의도(런타임 셰이더 컴파일 jank 방지). 페이지가 jank 완전 제거를 보장한다고 명시하지는 않음. ([출처](https://docs.flutter.dev/perf/impeller))

### 가이드라인
- Android 디버깅 시 비활성화: `flutter run --no-enable-impeller`. 배포 시: `AndroidManifest.xml`의 `<application>` 아래 `<meta-data android:name="io.flutter.embedding.android.EnableImpeller" android:value="false"/>`. ([출처](https://docs.flutter.dev/perf/impeller))
- 렌더링 이슈는 `[Impeller]` 제목 접두어로 GitHub에 기기/칩 정보, 스크린샷, 성능 trace와 함께 보고. ([출처](https://docs.flutter.dev/perf/impeller))

### 체크리스트
- [ ] 구형 Android(API<29) 및 저사양 기기에서 렌더링 QA
- [ ] 이슈 재현 시 Impeller on/off 비교

---

## 8. 플랫폼 적응형 UI (Material vs Cupertino, SafeArea)

### 핵심 개념/가이드
- 공식 adaptive 문서는 **capability(기기가 할 수 있는 것)** 와 **policy(해야 하는 것: 스토어 정책, 디자인 선호)** 를 구분하고, 레이아웃/기능 로직에서 `Platform.isIOS`/`kIsWeb` 직접 분기를 피하라고 권한다. 판단을 이름 있는 메서드(`Policy` 클래스 등)로 감싸고 위젯 테스트에서 mock 가능하게 한다. ([출처](https://docs.flutter.dev/ui/adaptive-responsive/capabilities))
- 이 페이지에는 Material vs Cupertino, `.adaptive` 위젯, SafeArea 상세가 없다(별도 페이지 존재, 미수집). 아래는 **[일반 지식]**:
  - `Switch.adaptive`, `Slider.adaptive`, `showAdaptiveDialog`, `CircularProgressIndicator.adaptive` 등 `.adaptive` 생성자가 플랫폼별 외형 제공. iOS 뒤로가기 스와이프 제스처는 기본 `PageRoute`가 처리.
  - `SafeArea`는 노치/Dynamic Island/홈 인디케이터/상태바 침범 방지; edge-to-edge가 기본인 최신 Android에서도 필요. `Scaffold`의 `body`는 이미 일부 처리하나 커스텀 오버레이/바텀 시트는 직접 `SafeArea`/`MediaQuery.viewPadding`.
  - 키보드 `viewInsets` 처리, 텍스트 스케일 대응.

### 자주 하는 실수
- `Platform.isIOS` 분기가 코드 곳곳에 흩어져 테스트/정책 변경 곤란. ([출처](https://docs.flutter.dev/ui/adaptive-responsive/capabilities))
- iOS App Store 정책(결제, 계정 삭제 등) 대응을 UI 분기로만 처리. ([출처](https://docs.flutter.dev/ui/adaptive-responsive/capabilities))

### 체크리스트
- [ ] 디자인 시스템 차원에서 Material 단일 vs 플랫폼별 외형 결정
- [ ] 플랫폼 분기는 `Policy`/`Capability` 클래스로 집중

---

## 9. 네비게이션 (go_router, 딥링크)

### 핵심 개념
- 딥링크 처리는 `Router` 위젯 방식이 권장이고 named routes는 대부분의 앱에 더 이상 권장되지 않는다. iOS/Android/Web 지원. ([출처](https://docs.flutter.dev/ui/navigation/deep-linking))
- `go_router`: 선언형 `Router` 기반, path/query 파라미터, 하위 라우트, `redirect`(예: 미로그인 시 로그인으로), `ShellRoute`(바텀 내비 유지 등), 딥링크 지원. **현재 maintenance 모드("feature-complete")이며 버그 수정·안정성 중심, 신규 기능 개발 계획 없음**(수집 시점 v18.1.1). ([출처](https://pub.dev/packages/go_router))
- 설정: Android는 App Links, iOS는 Universal Links 쿡북 참고. 기본 핸들러를 끄려면 `Info.plist`의 `FlutterDeepLinkingEnabled=false` / `AndroidManifest.xml`의 `flutter_deeplinking_enabled=false`. ([출처](https://docs.flutter.dev/ui/navigation/deep-linking))
- 앱 미실행(cold)/실행 중(warm) 시 라우팅 동작이 다르므로 둘 다 테스트. ([출처](https://docs.flutter.dev/ui/navigation/deep-linking))
- 푸시 알림 탭 → 화면 이동: `getInitialMessage()`(종료 상태에서 열림) + `onMessageOpenedApp`(백그라운드에서 열림). ([출처](https://firebase.google.com/docs/cloud-messaging/flutter/receive-messages))
- 커스텀 스킴 vs https 링크 **[일반 지식 / 해당 deep-linking 페이지의 일반 설명]**: 커스텀 스킴은 소유권 검증이 없어 다른 앱이 가로챌 수 있고, App/Universal Links는 도메인 검증 파일로 소유를 증명.

### 자주 하는 실수
- 로그인 redirect 로직에서 무한 redirect 루프. **[일반 지식]**
- 딥링크 파라미터 미검증(존재하지 않는 ID, 삭제된 게시물) 처리 누락.
- go_router를 영구적으로 발전하는 패키지로 가정(maintenance 모드임을 인지). ([출처](https://pub.dev/packages/go_router))

### 체크리스트
- [ ] 라우트 표(`/photo/:id`, `/topic/:date` 등) 정의 및 인증 redirect
- [ ] Universal Links(`apple-app-site-association`) / App Links(`assetlinks.json`) 도메인 검증 **[일반 지식]**
- [ ] 푸시 탭 → 라우팅의 cold/warm 테스트

---

## 10. 이미지 처리 (image_picker, 캐싱, 메모리)

### image_picker 핵심 ([출처](https://pub.dev/packages/image_picker))
- iOS `Info.plist`: `NSPhotoLibraryUsageDescription`(항상 `requestFullMetadata=false`여도 App Store 정책상 필요), `NSCameraUsageDescription`, `NSMicrophoneUsageDescription`(비디오 녹화).
- Android: 별도 설정 불필요(scoped storage). Android 13+는 Android Photo Picker 자동 사용. 메모리 부족 시 OS가 앱을 죽일 수 있으므로 시작 시 `retrieveLostData()`로 복구 처리. 카메라 촬영물은 앱 캐시에 저장되어 임시일 수 있으므로 영구 보관할 파일은 이동. `launchMode: singleInstance`는 항상 `RESULT_CANCELED` → `singleTask` 사용.
- iOS 14+ 시뮬레이터에서 HEIC 선택 불가(Apple 이슈) → 실기기 테스트. 지원: Android SDK 24+, iOS 13+.
- `maxWidth`/`imageQuality` 옵션 상세는 수집한 페이지에서 미확인 **[일반 지식: pickImage 파라미터로 리사이즈/압축 가능]**.

### 캐싱 ([출처](https://pub.dev/packages/cached_network_image))
- `cached_network_image`는 `flutter_cache_manager`로 캐시 디렉터리에 저장; `placeholder`/`progressIndicatorBuilder`/`errorWidget`/`imageBuilder` 제공. 웹에선 캐싱 미지원. 로드 실패가 디버거 중단/콘솔 에러/크래시 리포트에 기록될 수 있으나 앱 크래시는 아님.
- 메모리: 썸네일 목록에 원본 해상도를 디코딩하지 않도록 표시 크기에 맞춰 디코딩(`Image`의 `cacheWidth/cacheHeight`, `memCacheWidth` 등) **[일반 지식]**. `ImageCache` 기본 한도(1000장/100MB) **[일반 지식]**.

### 자주 하는 실수
- 원본 12MP 사진을 그대로 업로드/디코딩 → OOM·느린 업로드. **[일반 지식]**
- 카메라 임시 파일 경로를 DB에 저장했다가 사라짐. ([출처](https://pub.dev/packages/image_picker))
- Info.plist 키 누락 → 앱 리젝트/크래시. ([출처](https://pub.dev/packages/image_picker))
- Android lost data 미처리로 촬영 후 화면 상태 소실. ([출처](https://pub.dev/packages/image_picker))

### 체크리스트
- [ ] 업로드 전 리사이즈/압축(EXIF 방향/위치정보 처리 정책 포함)
- [ ] 목록은 썸네일 URL + 표시 크기 디코딩
- [ ] `retrieveLostData()` 앱 시작 시 처리

---

## 11. firebase_messaging (iOS/Android 차이)

### 핵심 개념
- 기기 상태: Foreground / Background / Terminated. 앱을 최소 1회 실행해야 FCM 등록. iOS에서 사용자가 앱 스위처로 스와이프 종료하면, Android에서 설정 > 강제 종료하면 재실행 전까지 백그라운드 메시지 수신 불가. ([출처](https://firebase.google.com/docs/cloud-messaging/flutter/receive-messages))
- 권한: iOS/macOS/웹/**Android 13+** 는 `requestPermission` 필요. Android 12 이하는 OS에서 끄지 않은 한 `authorized`. ([출처](https://firebase.google.com/docs/cloud-messaging/flutter/receive-messages)) (Android 13 `POST_NOTIFICATIONS` 매니페스트 선언 자체는 get-started 페이지에 없음 **[일반 지식]**)

### iOS 설정 ([출처](https://firebase.google.com/docs/cloud-messaging/flutter/get-started))
- Firebase 콘솔 > 프로젝트 설정 > Cloud Messaging에 **APNs 인증 키(.p8)**(Key ID, Team ID) 업로드.
- `ios/Runner.xcworkspace`에서 Push Notifications capability + Background Modes(Background fetch, Remote notifications).
- Apple 기기에서 method swizzling 필요. iOS SDK 10.4.0+ 에서는 다른 FCM 호출 전에 APNs 토큰 가용 확인(`getAPNSToken()`).
- `requestPermission(provisional: true)`로 임시(조용한) 권한 요청 가능.
- 이미지 알림은 Notification Service Extension 필요, **iOS 시뮬레이터는 푸시 이미지 미표시 → 실기기 필수**. ([출처](https://firebase.google.com/docs/cloud-messaging/flutter/receive-messages))
- 주의: 수집 시점 get-started 페이지는 토큰을 Firebase Installation ID 기반 `register()`/`onRegistered`로 설명하고 `getToken()`/`onTokenRefresh`를 deprecated로 표기하며 둘을 혼용하지 말라고 한다. 사용 중인 `firebase_messaging` 버전의 API를 확인할 것. ([출처](https://firebase.google.com/docs/cloud-messaging/flutter/get-started))

### 수신 처리 ([출처](https://firebase.google.com/docs/cloud-messaging/flutter/receive-messages))
- Foreground: `onMessage` 스트림. notification 메시지는 기본적으로 포그라운드에서 시스템 알림을 표시하지 않음 — Android는 High Priority 채널 필요, iOS는 presentation options 설정.
- Background/Terminated: `FirebaseMessaging.onBackgroundMessage(handler)` — 핸들러는 **익명 함수 금지, top-level 함수, `@pragma('vm:entry-point')` 필수(Flutter 3.3.0+)**. Android에서는 별도 isolate에서 실행되어 UI 상태 갱신 불가(HTTP, 로컬 저장은 가능), 30초 초과 작업은 프로세스 종료될 수 있음.
- 알림 탭: `getInitialMessage()`, `onMessageOpenedApp`.
- Auto-init 비활성화: iOS `FirebaseMessagingAutoInitEnabled=NO`, Android 메타데이터; 런타임 `setAutoInitEnabled(true)`. ([출처](https://firebase.google.com/docs/cloud-messaging/flutter/get-started))

### 자주 하는 실수
- 백그라운드 핸들러를 클래스 메서드/클로저로 작성, `vm:entry-point` 누락 → 릴리스에서만 미동작.
- 핸들러 isolate에서 `Firebase.initializeApp()` 미호출 **[일반 지식]**.
- iOS 시뮬레이터로 푸시 테스트. ([출처](https://firebase.google.com/docs/cloud-messaging/flutter/receive-messages))
- APNs 키 미업로드/환경(dev/prod) 불일치.
- 사용자 로그아웃/기기 변경 시 토큰 서버 등록 해제 누락 **[일반 지식]**.

### 체크리스트
- [ ] APNs .p8, Capabilities, Background Modes 설정
- [ ] Android 13+ 권한 요청 타이밍(첫 실행이 아닌 맥락 있는 시점)
- [ ] foreground 알림 표시 정책(로컬 알림 플러그인 또는 presentation options)
- [ ] 알림 탭 딥링크 라우팅(cold/warm)

---

## 12. 권한 (permission_handler)

### 핵심 ([출처](https://pub.dev/packages/permission_handler))
- **Android**: 메인 `AndroidManifest.xml`에 권한 선언, `android.useAndroidX=true`, 문서 기준 `compileSdkVersion` 35 (FAQ: `READ_MEDIA_*`는 compileSdk 33 필요). `Permission.x.shouldShowRequestRationale`로 설명 UI 노출 판단. `permanentlyDenied`는 **`request()` 결과로만** 반환되고 `status`는 `denied`로 나옴; 영구 거부 상태 request는 다이얼로그 없음 → `openAppSettings()`로 설정 이동.
- **iOS (Swift Package Manager, Flutter 3.24+/Xcode 15+)**: `Info.plist`의 usage-description 키로 권한 자동 활성화. Info.plist가 없으면 모든 권한이 컴파일 제외되어 `denied` 보고. 플레이버별 `permission_handler.yaml` 가능.
- **iOS (CocoaPods)**: `Info.plist` 키 + Podfile `post_install`의 `GCC_PREPROCESSOR_DEFINITIONS`에 권한별 `PERMISSION_CAMERA=1` 등 매크로(미사용은 0). **Info.plist 항목 누락 시 iOS가 앱을 종료**. 변경 후 clean & rebuild.

### 자주 하는 실수
- Podfile 매크로를 켜지 않고 `denied`만 반환되는 현상을 코드 버그로 오인.
- 사용하지 않는 권한 키를 남겨 App Store 심사 질의 (문서도 미사용 키 제거 권장).
- 영구 거부 시 같은 request를 반복. ([출처](https://pub.dev/packages/permission_handler))
- 사진 라이브러리 접근에 `image_picker`로 충분한데 별도 광범위 권한 요청 **[일반 지식: 시스템 피커 사용 시 권한 불필요한 경우 많음]**.

### 체크리스트
- [ ] 사용 권한만 Info.plist/매니페스트/Podfile 매크로에 선언
- [ ] 거부/영구거부/제한(limited) 상태별 UX 설계

---

## 13. --obfuscate, --split-debug-info, symbolication

### 핵심/가이드 ([출처](https://docs.flutter.dev/deployment/obfuscate))
- **릴리스 빌드에서만** 동작.
```bash
flutter build <target> --obfuscate --split-debug-info=/<symbols-dir>
# 난독화 맵 추가
#   --extra-gen-snapshot-options=--save-obfuscation-map=/<map-file>
flutter symbolize -i <stack-trace-file> -d <symbols-file>
```
- Android 타깃: `apk`/`appbundle`/`aar`, 심볼 파일 예 `app.android-arm64.symbols`(플랫폼·아키텍처에 맞는 파일 매칭 필수). iOS 타깃: `ios`/`ipa`/`ios-framework`.
- 한계: **보안 수단이 아님**(리소스 암호화 안 함 → 시크릿을 앱에 넣지 말 것), 웹은 난독화 대신 minify, `runtimeType.toString()` 등 이름 의존 코드 깨짐, 현재 enum 이름은 난독화되지 않음.
- **심볼 디렉터리는 반드시 빌드 버전별로 백업/보관**(Crashlytics 등 업로드 포함 — Crashlytics 연동은 **[일반 지식]**).

### 자주 하는 실수
- 릴리스 후 심볼 파일을 버려 스택트레이스 복원 불가.
- 다른 아키텍처/빌드의 심볼 파일로 symbolize. ([출처](https://docs.flutter.dev/deployment/obfuscate))
- 클래스명을 문자열 키/로그 분기에 사용.

### 체크리스트
- [ ] CI 릴리스 빌드에 두 플래그 적용, 심볼 파일을 아티팩트로 보존
- [ ] 크래시 리포팅 도구에 심볼 업로드 단계 추가

---

## 14. Flavors / 환경 설정

### 핵심 ([출처](https://docs.flutter.dev/deployment/flavors))
- Android flavor = **product flavor**. `android/app/build.gradle.kts`의 `flavorDimensions` + `productFlavors`(`applicationIdSuffix` 예: `.staging`). 실행/빌드: `flutter run --flavor staging`, `flutter build apk --flavor staging`; 값은 gradle의 flavor명과 일치해야 한다.
- Dart에서는 `appFlavor`(`package:flutter/services.dart`)로 플레이버명 조회(flavor 미지정 시 `null`).
- 앱 이름은 `src/<flavor>/res/values/strings.xml`의 `app_name` + 매니페스트 `@string/app_name`, 아이콘은 `src/<flavor>/res/mipmap-*`, 플레이버별 에셋은 `pubspec.yaml`의 `assets: flavors`, 기본 플레이버는 `default-flavor`. AGP 9.0+는 `resValue()`로 `app_name` 설정 시 기본 실패.
- iOS 플레이버(스킴/빌드 구성)는 별도 가이드(`flavors-ios`)이며 이번에 수집하지 못함. `--dart-define`/`--dart-define-from-file`은 해당 페이지에 없음 **[일반 지식: 컴파일 타임 상수 주입에 사용; 비밀 값은 앱에 포함 시 추출 가능]**.

### 자주 하는 실수
- dev/prod가 같은 bundle id/applicationId라 한 기기에 동시 설치 불가.
- 플레이버별 Firebase 설정 파일(`google-services.json`, `GoogleService-Info.plist`) 혼용 **[일반 지식]**.
- API 키/시크릿을 `--dart-define`에 넣어도 바이너리에서 추출 가능. ([출처](https://docs.flutter.dev/deployment/obfuscate) — 난독화는 보안 수단 아님)

### 체크리스트
- [ ] dev/staging/prod 별 applicationId/bundle id, 앱 이름, 아이콘, API base URL, Firebase 프로젝트
- [ ] CI에서 `--flavor` 지정 빌드

---

## 15. 테스트

### 핵심 ([출처](https://docs.flutter.dev/testing/overview))
- **Unit**(함수/클래스, 의존성 mock, 디스크·화면·외부 입력 없음), **Widget**(단일 위젯 UI/상호작용/레이아웃), **Integration**(앱 전체/대부분, 실기기·에뮬레이터, 성능 측정 가능).
- 트레이드오프: 신뢰도·유지비·의존성은 unit < widget < integration, 속도는 unit/widget 빠름·integration 느림. 권고: unit·widget을 많이, integration은 핵심 시나리오만 + 코드 커버리지 추적.
- SDK 내장 `integration_test`는 **권한 다이얼로그·알림·플랫폼 뷰 같은 네이티브 UI와 상호작용 불가** → 필요하면 **Patrol** 패키지 권장.
- Golden test(`matchesGoldenFile`로 렌더 결과를 기준 이미지와 비교)는 수집한 개요 페이지에 없음 **[일반 지식: 플랫폼/폰트에 따라 픽셀 차이 → CI 환경 고정 필요]**.

### mocktail ([출처](https://pub.dev/packages/mocktail))
- 코드 생성/수동 mock 불필요, null safety 지원. `class MockRepo extends Mock implements Repo {}`; `when(() => repo.x()).thenReturn(...)`; `verify(() => ...).called(1)`; `verifyNever`; 커스텀 타입에 `any()` 사용 시 `registerFallbackValue(Fake...)`를 `setUpAll`에서 등록; `reset(mock)`.
- 아키텍처 이점: ViewModel은 위젯 없이 unit test 가능, Repository/Service는 mock으로 교체. ([출처](https://docs.flutter.dev/app-architecture/guide))

### 자주 하는 실수
- `registerFallbackValue` 누락으로 `any()` 런타임 에러. ([출처](https://pub.dev/packages/mocktail))
- 위젯 테스트에서 `pump`/`pumpAndSettle` 혼동(무한 애니메이션에서 timeout) **[일반 지식]**.
- integration_test로 권한 팝업을 자동화하려다 막힘. ([출처](https://docs.flutter.dev/testing/overview))

### 체크리스트
- [ ] ViewModel/Repository unit test (mocktail), 핵심 위젯 widget test
- [ ] 로그인→업로드→좋아요 등 소수 integration 시나리오 (네이티브 UI 필요 시 Patrol)
- [ ] Golden test 사용 시 CI 폰트/플랫폼 고정

---

## 16. 접근성 (Semantics)

### 핵심 ([출처](https://docs.flutter.dev/ui/accessibility-and-localization/accessibility))
- 스크린 리더가 모든 컨트롤을 이해 가능하게 설명해야 하며 **TalkBack(Android)/VoiceOver(iOS)** 로 테스트. 동작 없는 컨트롤(빈 `onPressed`)은 피드백 제공.
- 터치 타깃 최소 **48x48**, 텍스트/컨트롤 대비 **4.5:1 이상**(비활성 제외), 큰 글꼴/디스플레이 스케일에서도 사용 가능, 색각 이상·그레이스케일 고려, 입력 중 자동 컨텍스트 전환 금지, 중요한 동작은 되돌릴 수 있게.
- 커스텀 위젯은 `Semantics(label:, button: true, child: ...)`로 의미 부여 (페이지는 일반 참고 예시로 제시). ([출처](https://docs.flutter.dev/ui/accessibility-and-localization/accessibility))
- 규정 참고: WCAG 2, EN 301 549(EAA 기술 기반), VPAT. ([출처](https://docs.flutter.dev/ui/accessibility-and-localization/accessibility))
- 위젯 테스트의 `meetsGuideline(...)` 가이드라인 체크 **[일반 지식]**.

### 자주 하는 실수
- 이미지(사진 카드)에 대체 텍스트 없음, 아이콘 버튼에 `tooltip`/label 없음 **[일반 지식]**.
- `GestureDetector`만으로 버튼 구현해 접근성 트리에서 누락.
- 고정 높이 컨테이너로 큰 글꼴에서 텍스트 잘림.

### 체크리스트
- [ ] 좋아요/사인/신고 버튼 label, 사진 카드 요약 label
- [ ] 텍스트 스케일 2.0 수동 확인, VoiceOver/TalkBack 점검

---

## 17. Null safety 함정 / 흔한 실수 (종합)

- **[일반 지식]** `!`(bang) 남용 → 런타임 크래시. JSON 파싱 시 서버 필드 누락 대비해 nullable/기본값/검증 필요(Dart 3 pattern matching·`sealed class` 활용).
- **[일반 지식]** `late` 초기화 전 접근 `LateInitializationError`; 프로미션(promotion)은 지역변수에만 적용, 필드는 지역 변수로 복사 후 체크.
- `dispose()` 누락(Controller, StreamSubscription, listener). ([출처](https://docs.flutter.dev/app-architecture/design-patterns/optimistic-state))
- `build` 안의 부작용·Future 생성, `const` 누락, 키 누락 (§3).
- async gap의 BuildContext 사용 (§4).
- 릴리스 모드에서만 발생: `vm:entry-point` 누락 (§11), 난독화로 인한 이름 의존 코드 (§13), iOS Info.plist/Podfile 권한 매크로 (§12), Android release의 INTERNET 권한 누락 **[일반 지식 — debug 매니페스트에만 있는 경우]**.
- 프로파일링은 **debug가 아닌 profile 모드, 실기기**에서. **[일반 지식]**

---

## 18. iOS/Android 플랫폼 셋업 gotchas (종합)

| 영역 | 항목 |
|---|---|
| iOS Info.plist | `NSPhotoLibraryUsageDescription`, `NSCameraUsageDescription`, `NSMicrophoneUsageDescription` (image_picker) ([출처](https://pub.dev/packages/image_picker)); permission_handler 사용 키만 유지 ([출처](https://pub.dev/packages/permission_handler)); `FlutterDeepLinkingEnabled`, `FirebaseMessagingAutoInitEnabled` ([출처](https://docs.flutter.dev/ui/navigation/deep-linking), [출처](https://firebase.google.com/docs/cloud-messaging/flutter/get-started)) |
| iOS Xcode | Push Notifications capability, Background Modes(Remote notifications, Background fetch), APNs .p8 업로드 ([출처](https://firebase.google.com/docs/cloud-messaging/flutter/get-started)); Associated Domains(Universal Links) ([출처](https://docs.flutter.dev/ui/navigation/deep-linking) 의 쿡북 링크) |
| iOS Podfile | `post_install`의 `PERMISSION_*=1` 매크로 (CocoaPods 사용 시) ([출처](https://pub.dev/packages/permission_handler)); 플랫폼 최소 버전은 플러그인 요구(image_picker iOS 13+) 이상으로 ([출처](https://pub.dev/packages/image_picker)) |
| Android Manifest | 권한 선언, `flutter_deeplinking_enabled`, 앱 이름 `@string/app_name`, Impeller 메타데이터 ([출처](https://docs.flutter.dev/perf/impeller)); 알림 권한(Android 13+ `POST_NOTIFICATIONS`) **[일반 지식]** |
| Android Gradle | `compileSdk` 35(permission_handler 문서 기준), AndroidX, image_picker는 minSdk 24+ 요구, flavors(`build.gradle.kts`) ([출처](https://pub.dev/packages/permission_handler), [출처](https://pub.dev/packages/image_picker), [출처](https://docs.flutter.dev/deployment/flavors)) |
| MainActivity | image_picker는 `launchMode: singleTask` 사용(`singleInstance` 금지) ([출처](https://pub.dev/packages/image_picker)) |
| 테스트 | iOS 시뮬레이터는 푸시 이미지 불가, HEIC 선택 불가 → 실기기 ([출처](https://firebase.google.com/docs/cloud-messaging/flutter/receive-messages), [출처](https://pub.dev/packages/image_picker)) |

---

## 찰캌 적용 포인트

1. **아키텍처**: 기능(오늘의 주제 피드, 사진 업로드, 좋아요, 사인, 알림, AI 리뷰)별 View/ViewModel/Repository/Service. 서버 응답은 Service에서 `Result<T>`로 감싸 ViewModel에서 exhaustive 처리. ([출처](https://docs.flutter.dev/app-architecture/design-patterns/result))
2. **좋아요/사인**: optimistic state + 롤백 + Command의 중복 실행 방지. 서버 의미(멱등성, 에러코드)는 백엔드 비즈니스 규칙 문서(`docs/business-rules`)에 맞추고 클라이언트에서 임의 정책 생성 금지(프로젝트 CLAUDE.md 규칙). ([출처](https://docs.flutter.dev/app-architecture/design-patterns/optimistic-state))
3. **피드**: `ListView.builder` + 썸네일 캐싱(`cached_network_image`) + 표시 크기 디코딩. 피드 읽기는 Stream(캐시→원격) 패턴 검토. ([출처](https://docs.flutter.dev/perf/best-practices), [출처](https://docs.flutter.dev/app-architecture/design-patterns/offline-first))
4. **이미지 업로드**: `image_picker`(Info.plist 키, Android lost data 복구) → 리사이즈/압축(필요 시 isolate) → 업로드. 업로드 `await` 후 `mounted` 확인. 업로드 재시도/네트워크 개념은 타 문서 참조, Flutter 쪽은 `Command` 상태로 진행/실패 표시. ([출처](https://pub.dev/packages/image_picker), [출처](https://dart.dev/tools/linter-rules/use_build_context_synchronously))
5. **푸시**: `firebase_messaging` — APNs .p8, Background Modes, top-level `@pragma('vm:entry-point')` 핸들러, Android 13+ 런타임 권한, `getInitialMessage`/`onMessageOpenedApp`로 딥링크 라우팅(go_router). iOS 시뮬레이터 한계로 실기기 테스트. ([출처](https://firebase.google.com/docs/cloud-messaging/flutter/receive-messages))
6. **권한**: 카메라/사진/알림 권한 요청 UX(rationale → request → permanentlyDenied 시 `openAppSettings`), Podfile 매크로/Info.plist 일치. ([출처](https://pub.dev/packages/permission_handler))
7. **네비게이션**: go_router(maintenance 모드임을 인지하되 공식 팀 패키지이며 안정적) + 인증 redirect + Universal/App Links. ([출처](https://pub.dev/packages/go_router))
8. **빌드/배포**: dev/staging/prod flavor, `--obfuscate --split-debug-info` 적용 및 심볼 보관, 시크릿은 클라이언트에 넣지 않음. ([출처](https://docs.flutter.dev/deployment/obfuscate), [출처](https://docs.flutter.dev/deployment/flavors))
9. **Impeller**: iOS는 Impeller 고정, Android API 29+ 기본, 이하 OpenGL 폴백 → 구형 기기 QA. ([출처](https://docs.flutter.dev/perf/impeller))
10. **테스트/접근성**: ViewModel unit test(mocktail), 핵심 위젯 test, 소수 integration(+Patrol), 사진 카드/버튼 Semantics, 48dp 타깃. ([출처](https://docs.flutter.dev/testing/overview), [출처](https://docs.flutter.dev/ui/accessibility-and-localization/accessibility))
11. **상태관리 선택(결정 필요)**: 공식 예제와 가장 가까운 Provider+ChangeNotifier vs Riverpod vs Bloc — 팀 숙련도 기준으로 팀이 결정. ([출처](https://docs.flutter.dev/data-and-backend/state-mgmt/options))
12. **미확인/후속 조사 필요**: Riverpod 공식 문서, iOS flavors 가이드, Impeller 최신 known issue, `--dart-define` 공식 설명, permission_handler 최신 버전 요구사항(수집 시 일부 페이지 접근 실패).

---

## 출처 목록

공식(Flutter/Dart/Firebase)
- Guide to app architecture: https://docs.flutter.dev/app-architecture/guide
- Result 레시피: https://docs.flutter.dev/app-architecture/design-patterns/result
- Command 레시피: https://docs.flutter.dev/app-architecture/design-patterns/command
- Optimistic state 레시피: https://docs.flutter.dev/app-architecture/design-patterns/optimistic-state
- Offline-first 레시피: https://docs.flutter.dev/app-architecture/design-patterns/offline-first
- State management options: https://docs.flutter.dev/data-and-backend/state-mgmt/options
- Performance best practices: https://docs.flutter.dev/perf/best-practices
- Impeller: https://docs.flutter.dev/perf/impeller
- use_build_context_synchronously: https://dart.dev/tools/linter-rules/use_build_context_synchronously
- Background parsing(compute): https://docs.flutter.dev/cookbook/networking/background-parsing
- Platform channels: https://docs.flutter.dev/platform-integration/platform-channels
- Developing packages & plugins(federated): https://docs.flutter.dev/packages-and-plugins/developing-packages
- Adaptive: capabilities & policies: https://docs.flutter.dev/ui/adaptive-responsive/capabilities
- Deep linking: https://docs.flutter.dev/ui/navigation/deep-linking
- Obfuscate Dart code: https://docs.flutter.dev/deployment/obfuscate
- Flavors (Android): https://docs.flutter.dev/deployment/flavors
- Testing overview: https://docs.flutter.dev/testing/overview
- Accessibility: https://docs.flutter.dev/ui/accessibility-and-localization/accessibility
- FCM Flutter get started: https://firebase.google.com/docs/cloud-messaging/flutter/get-started
- FCM Flutter receive messages: https://firebase.google.com/docs/cloud-messaging/flutter/receive-messages

pub.dev / 기타
- pub points & scoring: https://pub.dev/help/scoring
- go_router: https://pub.dev/packages/go_router
- image_picker: https://pub.dev/packages/image_picker
- cached_network_image: https://pub.dev/packages/cached_network_image
- permission_handler: https://pub.dev/packages/permission_handler
- mocktail: https://pub.dev/packages/mocktail
- Bloc "Why Bloc": https://bloclibrary.dev/why-bloc/
