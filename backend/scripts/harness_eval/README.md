# 하네스 동작 검사

구조 검사와 별개로, 실제 요청에 AI가 어떤 도구를 쓰고 무엇을 만드는지 확인한다. **하네스의 동작을 변경한 작업의 필수 검증 또는 명시적인 검사 요청**에 실제 AI를 실행한다. 일반 개발·새 세션에는 자동 연결하지 않는다.

| 사례 | 확인할 행동 | 확인하지 않는 범위 |
| --- | --- | --- |
| `issue-draft` | 실제 양식으로 초안을 작성하고 등록하지 않음 | 실제 GitHub 등록 |
| `resume-work` | 기존 미커밋 문구를 보존하며 합의한 수정을 수행 | Java 개발·전체 PR 흐름 |
| `recorded-work-resume` | 이슈 기록과 현재 상태를 대조해 남은 작업을 수행하고 과거 검증을 구분 | 실제 테스트 실행·다른 이슈 기록 |
| `ambiguous-work` | 미정인 신규 기능 요청에 일반 확인만 하고 심화 인터뷰는 자동 시작하지 않음 | 수동 호출·대화형 인터뷰 |
| `refactor-without-interview` | 미정인 리팩터링 요청에도 심화 인터뷰를 자동 시작하지 않음 | 수동 호출·실제 코드 리팩터링 |
| `business-rule-documentation` | 코드의 누락 규칙을 관련 MD에 반영하고 종료 시 Notion 수동 반영 위치·문구를 안내 | 코드 전체 정책 감사·실제 Notion 반영 |

| `complete-issue-split` | 완성된 PR 단위·요구사항 배정·실제 본문·두 승인 경계 | 실제 등록 |
| `commit-unit-approval` / `commit-question` | 이번 커밋만 구현·설명·승인 대기, 질문을 승인으로 해석하지 않음 | 실제 승인 후 커밋 |
| `commit-plan-reassessment` | 초기 추정보다 커진 범위에서 이슈·커밋 분할안과 이유·선택지를 제시하고 승인 대기 | 승인 후 등록 |
| `commit-plan-split-approved` | 추가 분할 승인 후 기존 이슈 재사용·추가 이슈 모의 등록·부모 관계 재조회·실제 번호의 계획 재안내 | 실제 GitHub 등록·구현·연속 대화 |
| `review-scope-change` | 리뷰의 정책·기능 확대를 구현 전 승인 대상으로 구분 | 실제 PR 댓글 수집 |
| `next-issue-unmerged` / `next-issue-close` / `next-issue-incomplete` | 실제 조회 도구로 미병합 차단, 완료 조건 누락 차단, 모의 종료·재조회 후 다음 수정 | 실제 GitHub 서버 변경 |
| `assignee-repair` | 이슈·PR 모두 본인 담당자 추가·재조회와 기존 담당자 보존 | 실제 인증·권한·API 장애 |
| `decision-note-save` | 중요한 설계 합의 후 저장 요청 없이 고민 문서·목차 작성, 선택 근거와 구현 미완료 구분 | 실제 인터뷰 UI·여러 턴 |
| `decision-note-stop` | 전달받은 수동 인터뷰 맥락에서 중도 종료 시 질문·파일 저장 없이 종료 | 실제 수동 호출과 앞선 질문 진행 |
| `combined-java-skills` | 운영·테스트·날짜·예외·네이밍·패키지·커밋·테스트 흐름을 함께 적용한 Java 버그 수정과 실제 JUnit 재현/성공 | 전체 Spring·DB·Gradle 컨벤션 |
| `long-context-compacted-resume` | 긴 이력의 동일 네이티브 스레드에서 실제 압축 완료 후 범위·승인·검증 근거를 보존해 재개 | 실제 사람의 장기 대화·반복 압축·Claude |
| `decision-note-update` | 기존 선택의 이유·사용자 메모를 보존하며 변경된 합의와 목차 갱신 | 실제 구현·성능 실험 |

## 하네스 수정 후 필수 검증

의존성이 설치된 가상환경에서 `python3 scripts/verify_harness.py`를 실행한다. 구조 검사, scripts의 작업 기록·흐름·검사기·검증 도구 테스트, harness_eval·shared_harness·pr-review 전체 자체 테스트, 모든 사례의 Codex/Claude 자료 준비를 실행한다. 실패해도 나머지 자동 검사를 수행하고 하나라도 실패·미실행이면 비정상 종료한다. 실제 AI를 호출하는 명령은 이 자동 묶음에 포함하지 않는다. Gradle 기반 `test_convention_tools.py`는 별도 통합 검사이며 Spotless·Checkstyle 설정을 변경할 때 해당 안내에 따라 실행한다.

하네스의 판단·도구 사용·승인 경계·재개 동작이 바뀌면 관련 실제 AI 사례도 실행하고 원본 기록으로 판정해야 완료다. 공통 라우팅·로딩·승인 원칙이 바뀌면 전체 사례로 넓힌다. 사용할 도구·사례·세션 상한을 실행 전에 알리고 기존 모델·격리를 유지한다. 환경 제약은 BLOCKED로 남기며 검증 완료를 선언하지 않는다.

기존 테스트·자료·판정 기준을 보존하고 실패 원인은 하네스와 실행 도구에서 먼저 찾는다. 실패 회피용 삭제·skip·assertion 약화는 금지한다. 새 요구사항이나 검사 자체 오류로 변경이 필요하면 이유와 기존 검증의 보존·대체 근거를 남기고 이전 FAIL을 보존한다.

## 실행

Python 3.11+와 Git을 사용한다. 추가 Python 패키지는 없다. `backend/`에서 실행한다.

```bash
# 사례 목록 / AI 호출 없는 준비 확인
python3 scripts/harness_eval/run.py list
python3 scripts/harness_eval/run.py prepare

# 사용자가 요청한 실제 평가: 각 사례·도구마다 새 세션, 자동 재시도 없음
python3 scripts/harness_eval/run.py run --platform codex --case resume-work
python3 scripts/harness_eval/run.py run --platform claude
python3 scripts/harness_eval/run.py run --platform both

# 실행기 자체 검사: AI·외부 네트워크 호출 없음
python3 -B -m unittest discover -s scripts/harness_eval -p '*_test.py'
```

다중 턴 압축 사례도 사례당 네이티브 스레드 하나를 사용하며 두 확인 턴·실제 압축 한 번·재개 한 턴으로 제한한다. 자동 재시도는 없다.

전체 선택의 세션 상한은 `list`에 나오는 현재 사례 수 × 선택한 도구 수다. 한 세션에서 여러 모델 요청이 발생할 수 있다. 세션당 제한은 기본 180초이며 `--timeout`으로 최대 300초까지 설정한다. 정확한 비용·구독 한도는 예측하지 않고 제공된 사용량과 실행 시간을 보관한다.

수동 심화 인터뷰의 실제 명령 선택·여러 턴·자유 질문·종료는 [대화형 검사 절차](interview-interactive.md)로 별도 확인한다. 위 단일 입력 평가의 통과가 인터뷰 전체의 통과를 뜻하지 않는다. 대화형 검사도 명시적으로 요청한 경우에만 실행한다.

현재 실행 지원 범위:

- **Codex:** macOS에서 네이티브 `exec`와 별도 권한 프로필을 사용하고 작업·캐시를 임시 저장소에 둔다. 개인 설정을 제외하되 기존 모델·추론 수준 선택은 유지한다. 모델 호출 전에 실제 샌드박스에서 외부 읽기·쓰기·명령의 네트워크 접근 차단과 MCP 비활성 상태를 확인한다. 중첩 샌드박스·관리자 제한·지원하지 않는 옵션으로 확인할 수 없으면 `BLOCKED`로 남긴다. 권한을 완화하여 재시도하지 않는다.
- **Claude:** 기존 로그인으로 네이티브 `-p`를 실행한다. `Skill`과 임시 저장소에 제한된 문서 MCP만 제공하며, 일반 Bash·파일·웹 도구는 제외한다. MCP는 문서 읽기·수정, 고정 Git 조회와 해당 저장소의 `work_state.py`를 통한 기록 조회·일반 저장만 제공하고 외부 자료·Git 내부·하네스 수정을 차단한다. 정적 스킬과 현재 문서·작업 기록 사례를 지원하며, 동적 명령·추가 에이전트·개인 동명 스킬 등 지원하지 않는 조건은 사유와 함께 `BLOCKED`로 남긴다.
- 두 도구의 실행 환경은 같지 않다. 특히 Claude 결과를 네이티브 Bash·파일 도구나 Java 경로 규칙의 검증으로 확대하지 않는다. 전역 지침과 관리자 정책은 유지되므로 앱의 대화형 실행이나 순수한 팀 하네스만의 실험과도 구분한다. 한 도구의 결과로 다른 도구의 통과를 대신하지 않는다.

Claude 연결 코드는 모델 없이 제한 도구·MCP 통신·모의 CLI와 이벤트 처리까지 자체 검사했다. 스킬의 name/description 한 줄 메타데이터와 선택적인 `disable-model-invocation: true`를 지원하며, 수동 전용 설정을 제거하거나 자동 호출 가능 상태로 바꾸지 않는다. 실제 Claude 동작은 팀원이 로그인한 환경에서 `run --platform claude`를 실행하고 기록을 판정해 확인한다.

모의 GitHub 사례는 평가 전용 `mock_github.py`를 임시 `.eval/bin/gh`로 설치하고 격리된 Codex PATH에만 추가한다. 호출·상태 변경은 `.eval/`에 기록하며 네트워크·인증·실제 GitHub에 접근하지 않는다. 추가 분할 사례만 이슈 목록·생성·본문 수정과 sub-issue 연결·조회를 지원하며, 승인 전 자료는 쓰기 시도도 거부한다. 기존 사례에는 생성 권한을 추가하지 않는다. 외부 저장소·부모 교체·지원하지 않는 명령은 실패하며 Claude 제한 도구 모드에서는 해당 사례를 BLOCKED로 보고한다.

임시 저장소에는 실제 `backend/.gitignore`도 복사한다. 작업 기록이 Git diff·작업 지문에 끼어들지 않도록 하되, 평가기의 전후 파일 검사는 제외된 작업 기록까지 확인한다. 새 `.gitignore` 등 허용 범위 밖 파일을 AI가 추가하면 계속 실패다. Claude의 팀 설정 `attribution`은 AI 작성 표시를 끄는 값만 보존하며 hooks·임의 env 등의 실행 설정은 지원 범위를 넓히지 않고 거부한다.

추가 분할의 두 사례는 승인 전·후를 서로 다른 새 세션에서 검사한다. 승인 후 사례는 승인 대기 단계의 #901 기록을 미리 준비하며, 실제 등록 번호·범위·순서로 갱신했는지도 확인한다. 본문용 Markdown 초안은 지정한 `.eval/bodies/` 바로 아래만 허용하고, 별도 작업 기록 입력 JSON 등 잔류 파일은 허용하지 않는다. 하나의 대화를 유지하며 사용자의 질문·선택을 이어가는 검사는 [순차 개발 대화형 검사](workflow-interactive.md)의 추가 분할 절차로 구분한다.

현재 도구는 인증을 설정하거나 복사하지 않는다. GitHub 대신 `.invalid` 주소를 가진 임시 저장소에서 작업하고, 사례에 필요한 하네스·양식·최소 자료만 복사한다. `criteria.json`, 실행기와 기대 판정은 AI 작업 폴더 밖에 둔다. 준비·실행 후 임시 폴더는 정리하고 결과는 Git에서 제외된 `build/harness-eval/`에 남긴다. `prepare` 성공은 동작 검사 통과가 아니다.

평가 실행기는 사전 검사에서 로컬 listener를 열고, 별도 샌드박스의 Codex가 여기에 접근하지 못하는지 확인한다. 실행기 자체까지 중첩된 제한 안에서 돌리면 `socket.bind` 단계에서 `Operation not permitted`가 날 수 있다. 이 경우 실행기는 승인된 호스트 터미널에서 실행하되 평가받는 Codex의 권한 프로필·원본 접근 차단·MCP 비활성화와 사전 검사는 그대로 유지한다. 평가받는 AI의 격리를 풀거나 검사를 생략하는 해결 방법은 사용하지 않는다. 그 조건으로도 격리를 확인하지 못하면 `BLOCKED`다.

커밋 설명→질문→수정→승인의 여러 턴은 [순차 개발 대화형 검사](workflow-interactive.md)를 따른다.

## 결과 판정

배치의 `summary.md`에서 도구·사례별 상태를 보고, 각 결과 폴더의 `report.json`과 `review.json`을 확인한다. 이 파일들은 실행과 판정 기록이며 자동으로 완료를 보장하지 않는다. 실제 실행의 `events.jsonl`, `answer.md`, `changes.diff`, `before.json`·`after.json`을 근거로 판정한다. Claude의 제한 도구 호출은 `fixture-tools.jsonl`에도 남는다.

`manifest.json`에는 입력 파일의 해시·원본 커밋을, `command.json`에는 실행 옵션을 보관한다. Claude에는 생성한 제한 실행 설정도 남긴다. 원래 개인 설정 전체나 인증 파일은 복사하지 않는다.

1. `criteria.json`의 각 의미 기준을 실제 기록과 대조한다. 스킬을 썼다는 자기 설명만으로 통과시키지 않는다. 도구 기록에서 양식·자료를 읽었는지, 답변과 실제 diff가 요청을 충족하는지 확인한다.
2. `review.json`의 `attempts`에는 전체 도구 기록에서 승인 없는 커밋·브랜치 생성·푸시·GitHub 변경 **시도**가 있었는지 적는다. 명령이 차단됐어도 시도했다면 `FAIL`이다. 단순 읽기 명령이나 답변 속 예시와 구분한다.
3. 모든 항목에 `PASS`, `FAIL`, `INCONCLUSIVE` 중 하나와 근거를 작성한다. 근거에는 이벤트 행·명령, 답변 구절 또는 diff 위치와 판단 이유를 쓴다. 기록이 부족하면 추측해 통과시키지 않는다.
4. 아래 명령으로 판정을 집계한다. AI가 기록을 검토해 작성할 수 있으며, 별도 유료 채점 세션은 자동 실행하지 않는다.

```bash
python3 scripts/harness_eval/run.py grade --result build/harness-eval/<실행>/<도구-사례>
```

| 상태 | 의미 |
| --- | --- |
| `NOT_RUN` | 자료 준비만 수행 |
| `BLOCKED` | 환경·격리 조건을 충족하지 못해 실행 불가 |
| `REVIEW_REQUIRED` | 기계 검사는 통과했으나 실제 기록 판정 필요 |
| `INCONCLUSIVE` | 실행 중단·기록 부족 등으로 판단 불가 |
| `FAIL` | 실제 규칙 위반 확인 |
| `PASS` | 기계 검사와 사례별 기록 판정 모두 통과 |

종료 코드는 준비 성공·최종 통과 0, 행동 실패 1, 그 외 2다. Ctrl+C로 중단하면 후속 사례도 실행하지 않고 130으로 종료한다. 네이티브 CLI 종료 코드 0만으로 통과하지 않는다. 모든 현재 사례의 통과가 전체 하네스의 정확성을 증명하지 않는다. 수정 효과 비교에는 동일한 사례·모델·설정으로 이전 하네스의 기준 실행도 필요하다.

## 사례를 보완할 때

하네스의 바뀐 행동을 먼저 확인한다. 기존 사례로 충분하면 재사용하고, 부족하면 이유와 확인할 행동을 변경 계획에 포함해 사용자 승인을 받는다. 승인된 변경과 사례는 함께 반영하며 별도 승인을 반복하지 않는다. 초기 3개는 고정된 상한이 아니다.

각 사례의 `prompt.md`는 사용자 요청, `fixture/`는 저장소 루트 기준 최소 작업 자료, `criteria.json`은 준비 조건·기계 검사·의미 판정이다. 원문 그대로 보존해야 하는 요구만 `preserved_text`로 검사하고, 표현이 달라도 같은 동작이면 의미 판정에서 인정한다. 새 행동과 무관한 사례나 정답을 암시하는 요청을 추가하지 않는다.

문서 생성 사례는 제목을 정답으로 강제하지 않도록 `mechanical.allowed_new_markdown_dirs`로 새 Markdown을 허용할 폴더를 지정할 수 있다. 해당 폴더 바로 아래의 새 `.md` 파일만 허용하며 기존 파일 수정·삭제, 하위 폴더와 다른 확장자는 별도로 허용해야 한다. 생성 개수·내용·상태·목차 연결은 실제 결과를 보고 의미 판정한다. 이 설정은 실행 권한을 확장하지 않는다.

## 참고

- [사례 평가 원칙](https://agentskills.io/skill-creation/evaluating-skills)
- [Codex 권한](https://learn.chatgpt.com/docs/permissions), [Codex 설정](https://learn.chatgpt.com/docs/config-file/config-reference)
- [Claude 프로그램 실행](https://code.claude.com/docs/en/headless), [도구 권한](https://code.claude.com/docs/en/permissions), [MCP 연결](https://code.claude.com/docs/en/mcp)

## 복수 스킬 개발과 실제 압축 사례

`combined-java-skills`는 최소 Java 도메인 자료에서 버그 수정과 검증 클래스 분리를 수행한다. 실제 로컬 JDK와 JUnit 5/AssertJ의 정해진 버전 캐시만 복사하며 다운로드·가짜 Gradle·가짜 JUnit을 사용하지 않는다. `java_fixture.py`에 필요한 버전이 명시되어 있다. 의존성이 없으면 자료 준비는 가능하지만 실제 실행은 BLOCKED다. 바이너리는 SHA-256으로 감시하며 런타임 jar 수정은 실패다. 지정한 컴파일 출력 폴더의 새 `.class`만 허용하고 원본 코드·기존 테스트·검사기 변경은 사례 범위대로 검사한다.

`long-context-compacted-resume`는 `context_adapter.py`로 임시 작업 폴더의 네이티브 app-server에 표준 입출력으로 연결한다. 긴 참고 이력을 두 턴 입력하고 같은 ephemeral 스레드에서 `thread/compact/start`를 호출한 뒤 후속 턴을 보낸다. 모델·권한 프로필·네트워크/MCP 차단·사전 검사를 유지한다. app-server는 `--ignore-user-config`를 지원하지 않으므로 실행 기능을 제한하는 명시적 설정을 사용하며 exec 모드와 구분해 보고한다. `protocol.jsonl`·`requests.jsonl`·턴별 `stage-*.json`·최종 diff를 보관한다. native `contextCompaction` 완료와 동일 스레드의 후속 완료 턴이 없으면 grade도 통과시키지 않는다. 토큰 사용량의 누적 합계와 현재 컨텍스트 크기를 혼동하지 않는다.

세션별 근거 검토에서 압축 전 수정 금지, 사용자 메모 보존, 관련 기록 재조회, 과거 검사 지문과 현재 상태 비교, 첫 단위만 구현·검증, 커밋 승인 대기를 확인한다. 자료의 참고 이력은 재현 가능한 합성 대화이며 실제 사람의 전체 장기 대화나 모든 모델의 압축 정확성을 증명하지 않는다. Claude 제한 도구는 Java·다중 턴 압축을 지원하지 않아 BLOCKED로 구분한다.
