# 하네스 동작 검사

구조 검사와 별개로, 실제 요청에 AI가 어떤 도구를 쓰고 무엇을 만드는지 확인한다. **사용자가 동작 검사를 요청한 경우에만** 새 AI 세션을 실행한다. CI·일반 개발에는 연결하지 않는다.

| 사례 | 확인할 행동 | 확인하지 않는 범위 |
| --- | --- | --- |
| `issue-draft` | 실제 양식으로 초안을 작성하고 등록하지 않음 | 실제 GitHub 등록 |
| `resume-work` | 기존 미커밋 문구를 보존하며 합의한 수정을 수행 | Java 개발·전체 PR 흐름 |
| `recorded-work-resume` | 이슈 기록과 현재 상태를 대조해 남은 작업을 수행하고 과거 검증을 구분 | 실제 테스트 실행·다른 이슈 기록 |
| `ambiguous-work` | 자료를 읽고 중요한 정책 질문 하나를 함 | 전체 인터뷰·최종 모호함 점수 |
| `business-rule-documentation` | 코드의 누락 규칙을 관련 MD에 반영하고 종료 시 Notion 수동 반영 위치·문구를 안내 | 코드 전체 정책 감사·실제 Notion 반영 |

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

전체 선택은 현재 5개 사례 × 2개 도구 = 10개 세션이다. 한 세션에서 여러 모델 요청이 발생할 수 있다. 세션당 제한은 기본 180초이며 `--timeout`으로 최대 300초까지 설정한다. 정확한 비용·구독 한도는 예측하지 않고 제공된 사용량과 실행 시간을 보관한다.

현재 실행 지원 범위:

- **Codex:** macOS에서 네이티브 `exec`와 별도 권한 프로필을 사용하고 작업·캐시를 임시 저장소에 둔다. 개인 설정을 제외하되 기존 모델·추론 수준 선택은 유지한다. 모델 호출 전에 실제 샌드박스에서 외부 읽기·쓰기·명령의 네트워크 접근 차단과 MCP 비활성 상태를 확인한다. 중첩 샌드박스·관리자 제한·지원하지 않는 옵션으로 확인할 수 없으면 `BLOCKED`로 남긴다. 권한을 완화하여 재시도하지 않는다.
- **Claude:** 기존 로그인으로 네이티브 `-p`를 실행한다. `Skill`과 임시 저장소에 제한된 문서 MCP만 제공하며, 일반 Bash·파일·웹 도구는 제외한다. MCP는 문서 읽기·수정, 고정 Git 조회와 해당 저장소의 `work_state.py`를 통한 기록 조회·일반 저장만 제공하고 외부 자료·Git 내부·하네스 수정을 차단한다. 정적 스킬과 현재 문서·작업 기록 사례를 지원하며, 동적 명령·추가 에이전트·개인 동명 스킬 등 지원하지 않는 조건은 사유와 함께 `BLOCKED`로 남긴다.
- 두 도구의 실행 환경은 같지 않다. 특히 Claude 결과를 네이티브 Bash·파일 도구나 Java 경로 규칙의 검증으로 확대하지 않는다. 전역 지침과 관리자 정책은 유지되므로 앱의 대화형 실행이나 순수한 팀 하네스만의 실험과도 구분한다. 한 도구의 결과로 다른 도구의 통과를 대신하지 않는다.

Claude 연결 코드는 모델 없이 제한 도구·MCP 통신·모의 CLI와 이벤트 처리까지 자체 검사했다. 실제 Claude 동작은 팀원이 로그인한 환경에서 `run --platform claude`를 실행하고 기록을 판정해 확인한다.

현재 도구는 인증을 설정하거나 복사하지 않는다. GitHub 대신 `.invalid` 주소를 가진 임시 저장소에서 작업하고, 사례에 필요한 하네스·양식·최소 자료만 복사한다. `criteria.json`, 실행기와 기대 판정은 AI 작업 폴더 밖에 둔다. 준비·실행 후 임시 폴더는 정리하고 결과는 Git에서 제외된 `build/harness-eval/`에 남긴다. `prepare` 성공은 동작 검사 통과가 아니다.

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

## 참고

- [사례 평가 원칙](https://agentskills.io/skill-creation/evaluating-skills)
- [Codex 권한](https://learn.chatgpt.com/docs/permissions), [Codex 설정](https://learn.chatgpt.com/docs/config-file/config-reference)
- [Claude 프로그램 실행](https://code.claude.com/docs/en/headless), [도구 권한](https://code.claude.com/docs/en/permissions), [MCP 연결](https://code.claude.com/docs/en/mcp)
