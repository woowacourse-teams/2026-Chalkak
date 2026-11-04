# TDD 규칙

## 순서: 바깥에서 안쪽으로

| 순서 | Android | iOS |
|---|---|---|
| 1 | ViewModel TC. domain의 Repository 인터페이스에 Fake를 꽂는다. 필요한 Repository 메서드는 이때 정해진다. `MainDispatcherRule`과 `runTest`를 쓴다. | ViewModel TC. Repository 자리에 Fake를 꽂는다. |
| 2 | Repository·DataSource TC. `MockWebServer`로 실제 요청 본문과 응답 처리를 본다. | API 계층 TC. 테스트 안의 가짜 응답 핸들러(`makeClient { request in ... }`)로 요청과 응답 처리를 본다. |

ViewModel이 바뀌지 않는 작업은 2부터 한다.

- mock 라이브러리를 쓰지 않는다. 같은 테스트 파일에 있는 `private class Fake...`를 재사용하거나 새로 만든다.
- Compose·SwiftUI 화면은 TDD 대상이 아니다. 계획의 테스트 밖 확인 항목으로 기기에서 본다.

## TC 한 개 돌리기

TC는 항상 하나씩 돌린다. 다시 불렸을 때는 `$EVID/evidence.md`에 Green이 기록된 TC를 건너뛰고, 그 TC들의 테스트가 지금도 통과하는지 한 번 돌려 본 뒤 다음 TC부터 한다. 두 플랫폼 작업이면 플랫폼마다 따로 본다(Android가 다 Green이고 iOS TC 2까지 Green이면 iOS TC 3부터).

1. **Red**
   - 계획의 TC 제목을 그대로 테스트 이름으로 쓰고, Given / When / Then 세 줄을 본문 주석으로 둔다.
     ```kotlin
     @Test
     fun `삭제에 성공하면 피드 목록에서 빠진다`() = runTest {
         // Given 피드에 사진 1, 2, 3
         // When  2번 삭제가 성공한다
         // Then  목록이 1, 3
     }
     ```
   - 컴파일이 안 되면 필요한 시그니처만 빈 구현으로 만든다.
   - 그 테스트 클래스만 실행한다(공통 약속의 "테스트 클래스 하나" 명령).
   - **새 테스트가 assert 실패로 떨어져야 Red다.** 컴파일 에러나 예외는 Red가 아니다. 테스트나 빈 구현을 고쳐서 assert에서 떨어지게 한다.
2. **Green**
   - 통과할 만큼만 구현한다.
   - 같은 클래스를 다시 실행한다. 새 테스트가 통과하고, 앞서 통과한 테스트도 깨지지 않아야 한다.
   - 앞서 통과한 테스트가 깨지면 구현을 고친다.
3. **Refactor**
   - 중복을 정리하고 플랫폼 `AGENTS.md`의 MUST 규칙(UiState, Route/Screen 분리, 디자인 토큰 등)에 맞춘다.
   - 다시 실행해서 같은 결과인지 본다.
4. **기록**: `$EVID/evidence.md`에 덧붙인다. PR 본문 근거로 그대로 쓴다. 플랫폼을 꼭 적는다.
   ```text
   - TC 1 (Android) 삭제에 성공하면 피드 목록에서 빠진다
     Red: expected:<[1, 3]> but was:<[1, 2, 3]>
     Green: 통과
   ```

## 하지 않는 것

- 테스트를 고쳐서 통과시키기
- 기존 테스트를 지우거나 `@Ignore`(iOS `.disabled`) 처리하기
- TC를 여러 개 한꺼번에 쓰고 한꺼번에 구현하기

## TC가 틀렸다고 판단하면

멈춘다. 테스트를 고쳐서 맞추지 않는다. 바꿀 TC와 이유를 대화에 보여 주고 `client-plan`의 "TC가 바뀌어 다시 승인받을 때"를 따른다.

## 계획에 없던 TC가 필요하면

계획 범위 안에서 빠진 경우(예: 앞뒤 공백 처리)는 추가 TC로 같은 사이클을 돌린다. 기록과 PR 본문에 `(추가 TC)`로 표시한다.
범위를 넓히는 TC라면 추가하지 않고 TC 변경으로 다룬다.
