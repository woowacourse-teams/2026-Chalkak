---
name: client-tdd
description: 승인된 계획의 TC를 하나씩 Red → Green → Refactor로 구현하고, 화면 작업이면 수정 전·후를 기기에서 확인한 뒤 push 전 전체 검사까지 통과시킨다. 사람이 /client-tdd로 부를 때만 쓴다.
argument-hint: "#이슈번호"
disable-model-invocation: true
---

# client-tdd

먼저 [공통 약속](../client-flow/references/conventions.md)과 [TDD 규칙](references/tdd-rules.md)을 읽는다.
계획은 `$EVID/plan.md` 또는 이슈의 `<!-- client-plan -->` 댓글에서 읽는다. 승인된 계획이 없으면 `client-plan`부터 하라고 알리고 멈춘다.

## 순서

1. **화면 확인 대상인지 보기**: 화면 코드(Compose `*Screen`·컴포넌트, SwiftUI View)를 바꾸면 대상이다.
   - 대상이면 **코드를 고치기 전에** 화면 확인 스킬을 `mode: before`로 부른다. Android는 `android-screen-check`, iOS는 `ios-screen-check`.
     서브에이전트로 돌고 결과만 돌아온다. 넘길 것: `items`(계획의 테스트 밖 확인 항목), `evid`(`$EVID`), `hint`(바뀐 화면의 Screen 이름, 아는 가는 길·재현 조건).
   - 결과의 `blocked`가 `없음`이 아니면 무엇이 막혔는지 사람에게 알리고 멈춘다. 사람이 "기기 확인 없이 진행"이라고 하면 이어 가고, PR에 못 한 이유를 적는다.
   - 결과의 `notes`에 사람에게 물을 것이 있으면 묻는다.
   - 대상이 아니면 기기 확인을 하지 않는다. PR의 📸 칸에는 `없음`을 적는다.
2. **TDD**: [TDD 규칙](references/tdd-rules.md)대로 TC를 하나씩 돌린다. TC가 0개면 바로 고친다.
3. **push 전 전체 검사**: 모든 TC가 끝나면 공통 약속의 전체 검사 명령을 돌린다.
   - TDD에서는 클래스 하나씩만 돌렸기 때문에 다른 테스트·포맷·린트가 깨졌는지는 여기서 처음 본다.
   - push 확인 뒤에 CI에서 떨어지면 사람이 한 번 더 봐야 하므로 여기서 통과시킨다.
   - 실패하면 출력을 읽고 고친다. 포맷은 ktlint 규칙대로, 깨진 테스트는 테스트가 아니라 구현을 고친다.
   - 결과(명령과 종료 코드)를 `$EVID/evidence.md`에 남긴다.
4. **수정 후 화면 확인**(1번에서 대상이었을 때): 같은 스킬을 `mode: after`로, 1번과 같은 `items`·`evid`로 부른다. 비교와 번호마다 판단까지 서브에이전트가 해서 돌려준다.
   - `설명 못 함`이 있으면 그 `compare` 이미지를 직접 열어 본다. 원인을 찾으면 고치고 3번부터 다시 한다. 동작이 바뀌면 TC를 추가해 Red → Green으로, 모양만이면 바로 고친다.
   - 고칠 수 없거나 원인을 모르면 그대로 두고, push 확인에서 사람에게 먼저 보여 준다.
   - 결과의 `route`는 `client-record`에 넘긴다.

같은 검사가 4번 연속 실패하면 공통 약속의 멈춤 규칙대로 멈춘다.

## 끝나면 알릴 것

- TC마다 Red 한 줄과 Green 통과, 추가 TC가 있었는지
- 전체 검사 명령과 종료 코드
- 화면 확인: 비교 이미지 경로(`file://`)와 번호마다 판단, 또는 `없음`이나 못 한 이유
