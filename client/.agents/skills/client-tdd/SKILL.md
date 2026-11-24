---
name: client-tdd
description: 승인된 계획의 TC를 테스트 클래스 묶음으로 Red → Green → Refactor 구현하고 push 전 전체 검사까지 통과시킨다. 플랫폼별 TDD는 서브에이전트가 동시에 한다. 화면 코드를 바꾸면 Android는 화면 자동 확인(Preview 스냅샷, 바로 열기 스크립트)도 같은 서브에이전트가 하고, 사람은 PR 리뷰에서 결과를 본다. 하네스 작업이면 TDD 대신 문서 검사와 시험·측정을 한다. 사람이 /client-tdd로 부를 때만 쓴다.
argument-hint: "#이슈번호"
disable-model-invocation: true
---

# client-tdd

먼저 [공통 약속](../client-flow/references/conventions.md)을 읽는다. [TDD 규칙](references/tdd-rules.md)은 플랫폼 서브에이전트가 읽는다.
계획은 `$EVID/plan.md` 또는 이슈의 `<!-- client-plan -->` 댓글에서 읽는다. 승인된 계획이 없으면 `client-plan`부터 하라고 알리고 멈춘다.
하네스 작업(공통 약속의 경로 표)이면 아래 "하네스 작업일 때"만 따른다.

## 누가 무엇을 하나

| 누가 | 하는 일 |
|---|---|
| 메인(이 대화) | 화면 확인 대상 판단(PR 📸 칸용), 플랫폼 서브에이전트 띄우기와 결과 받기, 사람에게 묻기 |
| 플랫폼 서브에이전트(바꾸는 플랫폼마다 하나) | TDD 규칙대로 묶음 Red → Green → Refactor, Android면 화면 자동 확인, push 전 전체 검사, `$EVID/evidence-<플랫폼>.md` 기록 |

TDD의 테스트 출력과 코드 읽기가 메인 대화에 쌓이면 그 뒤 호출마다 다시 실려 토큰이 커진다(#938은 맥락이 525k까지). 그래서 TDD는 새 맥락의 서브에이전트가 하고 메인에는 결과만 돌아온다.
플랫폼 서브에이전트의 모델은 지정하지 않는다. 사람이 고른 모델을 그대로 쓴다(코드를 고치는 일이라서).

## 순서

1. **화면 확인 대상인지 보기**: 화면 코드(Compose `*Screen`·컴포넌트, SwiftUI View)를 바꾸면 대상이다.
   - 대상이면 계획의 테스트 밖 확인 항목마다 `확인:`(`Preview`·`기기`·`사람`)이 있는지 본다. 빠졌으면 [화면 자동 확인](references/screen-auto-check.md)의 표로 정해 `$EVID/plan.md`에 적는다. `사람` 항목은 `가는 길:`·`데이터:`도 채운다(PR 📸 칸으로 옮긴다).
   - Android의 `Preview`·`기기` 항목은 플랫폼 서브에이전트가 [화면 자동 확인](references/screen-auto-check.md)대로 수정 전 실패 → 수정 후 통과까지 본다. iOS는 아직 모든 항목을 사람이 PR 리뷰에서 본다.
   - 메인은 기기를 조작하지 않는다. 화면 확인 스킬(`android-screen-check`, `ios-screen-check`)도 부르지 않는다(사람이 캡처·녹화를 요청할 때만).
     자동 확인 결과도 사람이 PR에서 본다. 화면 기준(레퍼런스)이 틀리면 스크립트는 틀린 화면도 통과시키기 때문이다.
   - 대상이 아니면 PR의 📸 칸에는 `없음`을 적는다.
2. **플랫폼 서브에이전트 띄우기**: 바꾸는 플랫폼마다 하나씩, 두 플랫폼이면 **동시에** 띄운다. Gradle과 xcodebuild는 서로 다른 도구라 같이 돌아도 된다.
   - Claude: `Agent` 도구(`general-purpose`, 모델 지정 없음, 백그라운드). Codex: `spawn_agent`(`agent_type`·모델 지정 없음).
   - `$EVID/evidence-<플랫폼>.md`에 전체 검사 종료 코드 0이 이미 있는 플랫폼은 띄우지 않는다(이어 가기).
   - 아래 "서브에이전트에 넘길 글"을 채워 넘긴다.
3. **결과 받기**: 서브에이전트는 아래 "돌려받는 결과" 형식만 돌려준다.
   - `tc_wrong`이 있으면 다른 플랫폼 서브에이전트도 멈춘다(Claude `TaskStop`, Codex `close_agent`). 바꿀 TC와 이유를 사람에게 보여 주고 `client-plan`의 "계획이 바뀌어 다시 승인받을 때"를 따른다. 승인받으면 이미 끝낸 플랫폼도 바뀐 TC로 2번부터 다시 한다.
   - `blocked`가 있으면 공통 약속의 멈춤 규칙대로 사람에게 알리고 멈춘다. 다른 플랫폼 서브에이전트는 끝날 때까지 둔다.
   - `refactor` 칸이 비었거나 `evidence-<플랫폼>.md`에 `## Refactor`가 없으면 그 서브에이전트에 Refactor와 기록을 마저 하게 한다(#939 시험에서 Android가 빠뜨림).
   - 바꾼 플랫폼 모두 `full_check`가 종료 코드 0이면 끝이다(`client-record`로 간다).

같은 검사가 4번 연속 실패하면 공통 약속의 멈춤 규칙대로 멈춘다. 플랫폼 서브에이전트 안의 검사는 서브에이전트가 세고 `blocked`로 돌려준다.

## 서브에이전트에 넘길 글

```text
너는 찰칵 <Android|iOS> TDD를 맡은 서브에이전트다. 사람에게 묻지 않는다. 막히면 멈추고 결과의 blocked에 적는다.

먼저 읽을 것:
- client/.agents/skills/client-flow/references/conventions.md (명령어, 멈춤 규칙)
- client/.agents/skills/client-tdd/references/tdd-rules.md (묶음 Red → Green → Refactor)
- 플랫폼 AGENTS.md (client/android/AGENTS.md 또는 client/iOS/AGENTS.md)

이슈: #<번호>
계획: <EVID>/plan.md  — 이 플랫폼에 해당하는 TC만 한다(`(Android만)` 같은 표시를 본다)
기록: <EVID>/evidence-<android|ios>.md  — Green이 이미 있는 TC는 TDD 규칙의 "다시 불렸을 때"대로 건너뛴다. `## 계획 수정` 줄이 있으면 그 아래 기록만 유효하다
iOS면 시뮬레이터: <UDID와 기종·iOS 버전>

할 일:
1. TDD 규칙대로 TC를 테스트 클래스 묶음으로 돌린다. TC가 0개면 바로 고친다.
2. 모든 TC가 끝나면 공통 약속의 push 전 전체 검사를 이 플랫폼만 돌려 종료 코드 0까지 만든다. 명령과 종료 코드를 기록 파일에 남긴다.
3. 이 플랫폼 폴더(client/android/ 또는 client/iOS/)만 고친다. 커밋·push하지 않는다.
4. Android이고 계획의 테스트 밖 확인 항목에 `확인: Preview` 또는 `확인: 기기`가 있으면 client/.agents/skills/client-tdd/references/screen-auto-check.md대로 한다.
   - 코드를 고치기 전에: Preview 수정 전 찍기, 기기 항목의 바로 열기·확인 스크립트를 쓰고 수정 전 코드로 돌려 실패 확인
   - 전체 검사 뒤에: Preview 수정 후 찍고 비교, 기기 항목 1번 돌려 통과 확인(실패하면 1번 더)
   - 그다음 같은 문서의 "PR 그림"대로 sheet-android.json을 쓰고 android-sheet-<k>.jpg를 만든다. 완성 그림은 한 번만 열어 글이 넘치거나 겹치지 않는지 본다
   - 스크립트는 첫 수정 전 실행 뒤 최대 2번까지만 고치고, 수정 전 실패를 확인한 뒤로는 고치지 않는다. 안 맞으면 `판정 불안정`으로 캡처만 남긴다.
   - 캡처는 <EVID>/media/, 스크립트는 <EVID>/checks/에 두고, 결과를 기록 파일의 `## 화면 확인`에 남긴다.
   - 모델이 캡처를 보고 누를 곳을 정하는 조작은 하지 않는다. 앱 삭제·데이터 지우기·connectedAndroidTest 금지.
   iOS는 화면 확인을 하지 않는다(사람이 PR 리뷰에서 한다).

돌려줄 결과: 아래 형식만. 테스트 출력이나 과정은 넣지 않는다.
```

## 돌려받는 결과

```text
platform: Android
tcs:
  TC 1 실패 → 통과
  TC 2 처음부터 통과
  추가: 소개 앞뒤 공백은 지운다 실패 → 통과
refactor: 공통 스위치 컴포넌트로 합침, 다시 실행 통과   ← 바꾼 것이 없으면 `없음`
full_check: 종료 코드 0                  ← 못 돌렸으면 이유
tc_wrong: 없음                           ← TC가 틀렸으면 번호와 이유, 원하는 TC
blocked: 없음                            ← 멈춤 규칙에 걸렸으면 실패한 검사, 마지막 메시지 한 줄, 4번 동안 바꾼 것, 남은 가설
changed: client/android/feature/feed/... (바꾼 파일)
screen:                                  ← Android 화면 자동 확인. 대상 아니면 `없음`
  1 Preview: 바뀜 1(의도한 변화), 그대로 15, 빈 그림 0
  2 기기: 수정 전 실패 → 수정 후 통과
  4 기기: 판정 불안정 — <무엇이 엇갈리는지> (캡처만, 사람이 PR에서 봄)
  3 사람: 실제 서버 응답
  잡음: Preview 4장                       ← 스크립트가 뺀 장 수. 없으면 0
  의도하지 않은 변경: 없음               ← 있으면 그림 이름과 무엇이 보이는지
  sheet: media/android-sheet-1.jpg (1장), originals 4개
```

플랫폼 칸의 말은 PR 본문 형식과 같다: `실패 → 통과`, `처음부터 통과`, `크래시 → 통과`.

## 하네스 작업일 때

`client/.agents/`(하네스)만 바꾸는 작업이다. 돌릴 TC가 없으므로 TDD 대신 이렇게 한다. 계획의 TC는 시험에서 확인할 하네스 동작이다.

1. **고치기**: 승인된 계획대로 스킬을 고친다.
2. **문서 검사**: 종료 코드 0까지 고친다. 명령과 종료 코드를 `$EVID/evidence-harness.md`에 남긴다. 4번 연속 실패하면 멈춤 규칙대로 멈춘다.
   ```bash
   python3 client/.agents/skills/client-flow/scripts/check-harness.py
   ```
   상대 링크, `client/.claude/skills` 링크, 스킬 이름, 스크립트 문법, `client/.codex/agents` 정의를 본다.
3. **시험 준비**: 시험 이슈가 바뀐 하네스로 돌도록 한다.
   - 고친 스킬을 커밋하고 push한다(`chore: <내용> (#번호)`). 시험 브랜치가 이 커밋을 받아야 하기 때문이다. 문서 커밋은 `client-pr`에서 따로 한다.
   - `$EVID/trial.md`에 시험 이슈 번호, 시험 브랜치, 하네스 브랜치, 하네스 커밋, 확인할 TC 번호를 적는다.
4. **시험 안내하고 멈추기**: 사람에게 아래를 알리고 멈춘다. 시험은 측정을 세션 단위로 하므로 이 대화가 아니라 새 세션에서 한다.
   - 시험 브랜치를 하네스 브랜치 위로 올리는 명령. 시험 이슈에 연결 브랜치가 있고 커밋이 없으면 그 워크트리에서 fast-forward로 올린다. 연결 브랜치가 없으면 하네스 브랜치를 기준으로 만든다.
     ```bash
     # 연결 브랜치가 있을 때 (시험 브랜치의 워크트리에서)
     git fetch origin
     git merge --ff-only origin/<하네스 브랜치>                      # 시험 브랜치에 커밋이 없을 때만 된다
     git config branch.<시험 브랜치>.gh-merge-base <하네스 브랜치>   # 시험 PR의 기준 브랜치
     # 연결 브랜치가 없을 때
     gh issue develop <시험 이슈> --base <하네스 브랜치> --name <시험 브랜치> --checkout
     ```
   - 그 워크트리에서 `/client-flow #<시험 이슈>`를 부른다는 것, 끝나면 이 워크트리에서 `/client-flow #<하네스 이슈>`로 돌아오면 측정부터 이어 간다는 것.
5. **측정**(이어 가기로 돌아왔을 때): 시험 이슈에 드래프트 PR이 있으면 계획의 "측정할 값"을 잰다.
   - 측정 도구가 있으면 그 방법대로 재고(만든 사람의 로컬 `~/.config/chalkak/metrics/README.md`), 없으면 `측정 못 함: <이유>`로 적는다.
   - 결과를 `$EVID/metrics.md`에 `값 | v0(<기준 이슈>) | 이번` 표로 남기고, TC마다 시험에서 확인됨/안 됨과 근거(세션 기록, 시험 PR, 기록 파일)를 적는다.
   - 확인 안 된 TC가 있으면 원인을 고치고 2번부터 다시 한다. 고칠 수 없으면 PR에 적는다.

## 끝나면 알릴 것

- 플랫폼마다 TC 결과(실패 → 통과 등)와 추가 TC가 있었는지
- 전체 검사 종료 코드
- 화면 확인: 자동 확인 결과(항목마다 수정 전 → 수정 후, Preview 바뀜·잡음과 `의도하지 않은 변경`), PR 그림 장수, 사람이 PR에서 볼 항목 수, 또는 `없음`
- 하네스 작업이면: 문서 검사 결과, 시험 이슈와 확인된 TC, 측정 표
