# 테스트하기 쉬운 구조와 기능 동등성 검증

근거: [raw/architecture-testing.md §3, 6~8](../../raw/architecture-testing.md), 각 플랫폼 raw의 테스트 절. 구조 원칙은 [architecture-state.md](architecture-state.md).

## 1. 테스트하기 쉬운 구조

- 의존성은 생성자로 받는다. 네트워크, 저장소, 시간(`Clock`), UUID, 푸시 토큰, 이미지 피커, 분석 전송을 모두 인터페이스 뒤에 둔다.
- **Fake 우선**: Repository·Service의 가벼운 실제 구현(메모리 맵 등)을 테스트 소스에 둔다. mock 라이브러리로 호출 순서를 검증하면 리팩터링마다 테스트가 깨진다. 호출 자체가 요구사항일 때(분석 이벤트 전송)만 기록형 Fake/spy를 쓴다. [공식](https://developer.android.com/training/testing/fundamentals)
- ViewModel 테스트는 "Fake Repository 상태 + 사용자 이벤트 → UiState 전이"를 검증한다. 화면 테스트 없이 동작의 대부분을 잡는다.
- 코루틴·async 테스트는 시간을 제어한다: Kotlin `runTest` + `StandardTestDispatcher`(메인 디스패처 교체 규칙, 기존 앱 `MainDispatcherRule`), Swift는 주입한 Clock, Dart `fakeAsync`, JS fake timers. AI 리뷰 3초 폴링·5분 창은 실제로 기다리지 않고 테스트한다.

## 2. 플랫폼별 테스트 도구

| 층 | iOS | Android | Flutter | RN | Web |
| --- | --- | --- | --- | --- | --- |
| 단위 | Swift Testing(`@Test`, `#expect`) / XCTest | JUnit + `kotlinx-coroutines-test` | `test` | Jest | Vitest |
| 컴포넌트 | SwiftUI 프리뷰, ViewInspector 등 [일반지식] | Compose `createComposeRule()` [공식](https://developer.android.com/develop/ui/compose/testing) | widget test | React Native Testing Library [공식](https://reactnative.dev/docs/testing-overview) | Testing Library |
| 스크린샷 | swift-snapshot-testing [일반지식] | Roborazzi / Compose Preview Screenshot Testing [일반지식] | golden(`matchesGoldenFile`) | — | Playwright `toHaveScreenshot` |
| E2E | Maestro(시뮬레이터), XCUITest | Maestro, Compose UI test | Maestro, `patrol`(권한 다이얼로그 처리) | Maestro, Detox | **Playwright** |
| 아키텍처 규칙 | SwiftLint custom rule | Konsist(기존 앱), detekt | `custom_lint` 등 | ESLint `no-restricted-imports` | 같음 |
| 린트·포맷 | SwiftLint, swift-format | ktlint(기존 앱), Android Lint | `flutter_lints`, `dart format` | ESLint + TS strict, Prettier | 같음 |

- Maestro는 Android(에뮬레이터·실기기), iOS 시뮬레이터, Flutter·RN을 같은 YAML로 다룬다. **웹은 Beta, Chromium 한정**이라 웹 E2E는 Playwright가 주력이다. [공식](https://docs.maestro.dev/get-started/supported-platform.md)
- 같은 Maestro flow를 쓰려면 **요소 식별자를 다섯 플랫폼이 같게** 부여한다(iOS `accessibilityIdentifier`, Compose `testTag` + `testTagsAsResourceId`, Flutter `Semantics(identifier:)`, RN `testID`, 웹 `data-testid`). 식별자 표는 시나리오 문서와 함께 둔다. [제안]
- 피라미드: 단위(ViewModel, Repository) 다수 > 컴포넌트 > 핵심 여정 E2E 소수. 실서버 대상 E2E는 하지 않는다. [공식](https://martinfowler.com/articles/practical-test-pyramid.html)

## 3. 기능 동등성 검증 체계

"같은 동작"의 기준을 구현이 아니라 **명세**에 둔다. 다섯 가지를 레포에 하나씩만 둔다. [제안]

| 명세 | 위치(제안) | 쓰임 |
| --- | --- | --- |
| 시나리오(인수 기준) | `devwiki/parity/scenarios/*.md` | 시나리오 ID(`PAR-LIKE-001`)와 Given/When/Then. 테스트 이름에 ID를 넣어 추적 |
| API 계약 | 서버 springdoc OpenAPI 산출물 | DTO 생성 또는 스키마 검증, mock 서버 |
| fixture | `devwiki/parity/fixtures/*.json` | 시나리오별 서버 응답. 다섯 클라이언트가 같은 파일을 쓴다 |
| 문구 | 공통 문구 표(오류·빈 상태·접근성 라벨) | 키 기준으로 다섯 플랫폼에 반영 |
| 분석 이벤트 | 이벤트 사전 | §4 |

### 3.1 시나리오 작성 규칙

```gherkin
Scenario: PAR-LIKE-002 좋아요 실패 시 되돌리기
  Given 승인된 게시물 하나를 좋아요하지 않은 상태로 보고 있다   # fixture: feed_one_post.json
  And 좋아요 등록 요청이 서버 오류로 실패한다                     # fixture: error_500.json
  When 좋아요 버튼을 누른다
  Then 버튼이 즉시 선택 상태가 되었다가 선택 해제로 돌아온다
  And "좋아요를 반영하지 못했어요" 메시지가 보인다
```

- Given은 fixture로 재현 가능한 서버 상태, Then은 사용자가 관찰할 수 있는 결과만 쓴다. [공식](https://cucumber.io/docs/gherkin/reference/)
- 규칙 문서(`docs/business-rules`)의 ID를 함께 적는다. 규칙이 없거나 서버와 다르면 시나리오를 확정하지 않고 [open-questions](../open-questions.md#백엔드-확인-대상)에 올린다.
- Gherkin 러너를 강제하지 않는다. 테스트 이름이나 Maestro flow 파일명에 ID를 넣는 것으로 충분하다.

### 3.2 mock 서버

- 모바일 네 개(iOS, Android, Flutter, RN)는 개발·E2E 빌드에서 base URL을 로컬 mock 서버로 바꿀 수 있어야 한다. OpenAPI로 Prism mock을 띄우고, 시나리오별 응답은 fixture로 고정한다(Prism 동적 응답만으로는 실패·경계 상황을 못 만든다). [공식](https://docs.stoplight.io/docs/prism/83dbbd75532cf-http-mocking)
- 웹은 같은 fixture를 MSW 핸들러나 Playwright `page.route`로 쓴다. [공식](https://mswjs.io/docs/)
- fixture와 실제 서버가 어긋나지 않게, OpenAPI 스키마로 fixture를 검증하는 스크립트를 CI에 둔다.
- 스토리지 presigned PUT도 mock 서버가 받아야 업로드 시나리오가 끝까지 돈다.

### 3.3 패리티 매트릭스

| 시나리오 ID | 규칙 | iOS | Android | Flutter | RN | Web | 검증 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| PAR-AUTH-001 로그인 → 사인 등록 → 홈 | USER-001, SIGN-001 | | | | | | E2E |
| PAR-AUTH-002 동시 401 → 갱신 1회 | — | | | | | | 단위 |
| PAR-FEED-001 피드 로딩·빈·오류·오프라인 | POST-007 | | | | | | 단위 + 스크린샷 |
| PAR-LIKE-001~003 좋아요, 실패 롤백, 연타 | LIKE-003, 004 | | | | | | 단위 + E2E |
| PAR-UPLOAD-001~004 업로드, URL 만료, 응답 유실, 하루 1장 | POST-003, 004, 011 | | | | | | 단위 + E2E |
| PAR-AIREVIEW-001~004 상태 분기, 폴링 창 초과, 다시 요청 | AIREVIEW-* | | | | | | 단위 |
| PAR-PUSH-001~007 `type`별 도착 화면 | NOTIFICATION-* | | | | | | 단위 + 수동 |
| PAR-TOPIC-001 기기 시간대와 무관한 오늘 주제 | TOPIC-002, 005 | | | | | | 단위 |
| PAR-ACCOUNT-001 탈퇴 | USER-003~006 | | | | | | E2E |

매트릭스는 플랫폼 작업 PR마다 갱신한다. 칸 값은 `✓`(테스트 있음), `수동`, `차이 허용(링크)`, 빈칸(미구현).

## 4. 분석 이벤트 동등성

- 기존 iOS·Android 앱은 Firebase Analytics에 `screen_view`(화면 이름·클래스)와 `bottom_navigation_selected`(`destination`)를 보낸다. 새 플랫폼도 같은 이름과 속성으로 시작한다. [찰캌규칙]
- 이벤트 사전(이름, 속성, 타입, 발생 시나리오 ID)을 한 파일로 두고 다섯 플랫폼이 따른다. 이름은 snake_case, 화면 이름 값도 사전에 고정한다. [제안]
- 각 플랫폼은 `AnalyticsTracker` 인터페이스 뒤에 숨기고, 테스트에서는 기록형 Fake로 "시나리오 X를 돌리면 이벤트 Y가 이 속성으로 한 번 나간다"를 검증한다.
- 웹은 Firebase Analytics JS SDK 또는 GA4를 쓰며, 같은 이벤트 이름을 쓴다.

## 5. 허용된 차이 기록

동작을 맞출 수 없는 곳은 시나리오에 "차이 허용"으로 표시하고 대체 UX를 함께 적는다. 현재 후보:

- 웹: 백그라운드 업로드 없음, iOS Safari는 설치형 PWA에서만 푸시, 햅틱 없음, 카메라는 브라우저 파일 입력
- Android·Web에서 Apple 로그인 제공 여부
- 플랫폼 관례 UI(Snackbar vs 인라인 배너, 시트 모양)

결정은 [open-questions](../open-questions.md#팀-결정-대상)에서 한다.

## 체크리스트

- [ ] 새 ViewModel마다 Fake Repository 기반 UiState 전이 테스트
- [ ] 시간 의존 로직(폴링, 재시도, 날짜)은 가짜 시계로 테스트
- [ ] 요소 식별자가 식별자 표와 같다
- [ ] 기능의 시나리오 ID가 테스트 이름에 있고 매트릭스가 갱신됐다
- [ ] fixture는 공용 폴더의 것을 쓰고 복제하지 않았다
- [ ] 분석 이벤트가 사전과 같다
