# 작업 재개 기록

이슈에 연결된 변경 작업의 착수·진행 갱신·재개 때 읽는다. 이슈 초안·기획 인터뷰·단순 질문·새 세션 시작만으로 기록을 만들지 않는다.

## 선택과 재개

- `backend/.harness/state/issue-<실제 번호>.json`에 이슈별 현재 요약 하나를 둔다. Git에서 제외하며 같은 작업 폴더의 Codex·Claude가 공유한다. 다른 PC·worktree에는 자동 복사되지 않는다.
- 요청·대화·브랜치에서 실제 작업 이슈를 확인하고 **그 파일만** 읽는다. 불분명하면 브랜치와 기록 파일명을 살피고, 후보가 여럿이면 해당 이슈만 질문한다. 최신 파일을 임의 선택하거나 전체 기록을 읽지 않으며 기록을 위해 이슈를 만들지 않는다.
- [공용 도구](../../../../scripts/work_state.py)는 Python 3.11+·macOS/Linux에서 `backend/`를 기준으로 실행한다.

```bash
python3 scripts/work_state.py load --issue 287
```

- `load`의 저장소·브랜치·이슈를 실제 Git 상태와 대조한다. 다른 작업의 기록을 적용하거나 기록만 보고 브랜치를 전환하지 않는다. 누락·손상된 기록은 대화·이슈·diff로 보완하고 필요한 부분만 질문한다.
- `code_state`는 HEAD·index·backend 추적 파일과 Git 제외 대상이 아닌 미추적 파일의 상태다. 현재 상태를 기록·검증 당시 상태와 각각 비교하고 차이에 영향받는 검사를 다시 한다. 일치하더라도 실제 검사 결과·범위와 관련 이슈·PR·CI 상태를 확인한 뒤 첫 미완료 단계부터 이어간다.
- 기록의 문장·`done`·승인 표시는 참고 자료다. 대화·인계의 사용자 승인 근거와 요청 범위에 따라 진행하며, 파일만으로 전체 개발·커밋·푸시·등록·이력 재작성 권한을 만들지 않는다.

## 저장과 검증

- 입력은 `{"work": {...}}` 형식의 JSON이다. `work`에는 `goal`(목표), `done`(완료 목록), `remaining`(남은 목록), `next`(다음 행동)를 두고 필요하면 `scope`, `decisions`, `blockers`, `links`, `stack`, `workflow`, `commit_unit`을 추가한다. 구현·검증·리뷰·병합 상태를 구분하고 관련 PR·의존 관계·실제 base는 `links`에 남긴다. 생략한 기존 필드는 보존하며 목록 교체 시 미완료 항목을 빠뜨리지 않는다. `workflow`와 `commit_unit`을 바꿀 때는 내부 필드 전체를 교체해 이전 승인 근거가 새 단위에 섞이지 않게 한다.
- 새 승인 근거 없이 `scope.include`·`scope.exclude`를 바꾸거나 제외된 행동을 `remaining`·`next`에 추가하지 않는다. 이번 요청에서 끝난 항목만 `done`으로 옮기고, 기존 범위 밖 후속 작업을 임의로 만들지 않는다.
- 저장소 밖의 임시 파일이나 표준 입력(`--input -`)으로 전달한다. `load`의 `revision`을 사용하고 신규 기록은 `missing`으로 저장한다. 원자적으로 교체하며 오래된 revision은 거부한다. 충돌하면 최신 기록과 실제 변경을 대조해 합친다.

```bash
python3 scripts/work_state.py save --issue 287 --expected-revision <읽은 revision 또는 missing> --input <임시 JSON 경로>
```

- 검사 전 `python3 scripts/work_state.py snapshot`으로 지문을 얻는다. 검사 후 명령·결과·로그 위치를 `work`와 나란한 `checks` 배열에 넣고 위 저장 명령에 `--record-checks --checked-fingerprint <검사 전 fingerprint>`를 붙인다. 검사 도중 코드가 바뀌면 저장을 거부하므로 현재 코드로 다시 검증한다. 미실행을 통과로 적지 않는다.
- 일반 진행 저장에는 검증 옵션을 쓰지 않는다. 기존 검증의 시각·대상 지문을 유지하며 과거 결과를 현재 검사로 바꾸지 않는다.
- 단위 완료·검증·커밋·PR·선행 관계 변경·대기 전·종료 전에 갱신한다. 매 답변을 누적하지 않는다. 강제 종료 시 마지막 저장 이후 실제 변경을 확인한다.
- 수십 줄·8KiB 이내로 요약하고 대화 전문·코드·diff·로그·비밀 값 대신 이슈·PR·로그 위치를 연결한다. 미해결 사항과 후속 재적용 경계 SHA를 남기고 과거 설명부터 줄인다.
- 명시적으로 맡긴 기존 스택의 직접 부모·이전 부모 기준 SHA·확인한 원격 head SHA는 `stack`에 보존한다. 선행 변경 반영·PR 갱신에는 [스택 절차](../../branch-workflow/references/stacked-prs.md)를 따르고 성공한 단위의 기준만 갱신한다.

## 정리

- 병합·후속 작업까지 끝났거나 사용자가 기록 폐기를 요청했을 때 해당 이슈 기록만 `remove --issue <번호> --expected-revision <읽은 revision>`으로 정리한다. 구현·PR 등록만으로 삭제하지 않는다.
- 도구 실행이 불가능하면 대화 요약에 상태와 재개 지점을 남긴다. 이 기록은 세션 종료 후 감시나 자동 재실행을 만들지 않는다.

## 순차 진행과 승인 인계

- 새 파일 체계를 만들지 않는다. 같은 이슈의 기록 하나를 계속 갱신하며 같은 작업 폴더의 Claude↔Codex 전환·다음 날 재개에 사용한다. 다른 PC·worktree에는 자동 전달되지 않는다.
- 메인 이슈·전체 순서를 모르는 개별 작업은 번호를 만들어 채우지 않고 `workflow`를 생략한다. 기존 `scope`·`blockers`·`next`로 확인된 현재 상태를 남기며, 전체 흐름 착수 전 실제 관계를 확인한다.
- `workflow`에는 `main_issue`(양의 정수), `order`(승인된 실제 서브 이슈 번호 배열), `phase`(아래 상태), `approval_basis`(사용자 승인 대화·인계 근거), `pending_change`(범위 변경 제안, 없으면 빈 문자열)를 둔다. 메인 요구사항별 담당 이슈와 완료 근거는 `decisions`·`links`에 간결히 남겨 마지막 대조에 재사용한다. 순서는 전체 실행의 각 이슈 기록에 보존하며 변경 승인 후 관련 기록도 맞춘다.
- `phase`: `implementation`, `commit_approval`, `review_wait`, `scope_approval`, `merge_check`, `completed`. `load`의 `matches.commit_approval`은 현재 HEAD·지문·브랜치와 승인 대상의 일치만 뜻하며 메시지·실제 사용자 승인 근거는 별도로 대조한다. 기록 단계는 실제 행동의 권한이 아니다. `completed`여도 GitHub 상태·완료 근거를 재조회한다.
- `commit_unit`에는 `goal`, `status`(`implementing`, `awaiting_approval`, `approved`, `committed`), `paths`(포함하는 backend 상대 경로), `message`(제안 메시지), `fingerprint`·`head`(제시 당시 snapshot), `approval_basis`(대화 승인 근거, 없으면 빈 문자열)를 둔다. 다음 단위로 넘어갈 때 통째로 최신 단위로 갱신하고 이전 승인을 재사용하지 않는다.
- 현재 변경·메시지의 승인을 받으면 같은 저장 요청에서 `workflow.phase`를 `implementation`, `commit_unit.status`를 `approved`로 함께 갱신한다. 커밋 실패 시 승인 근거와 실패 지점을 보존하고 다음 단위를 시작하지 않는다. 성공한 커밋만 `committed`로 기록한다.
- 승인 대기·승인 상태에는 메시지·경로·지문·HEAD가 필요하고, 승인·커밋 상태에는 사용자 승인 근거가 필요하다. 검사기는 형식·내부 일관성만 확인하며 실제 대화를 인증하지 않는다.
- 재개 시 이슈 본문·코드·스테이징·HEAD·메시지·검증 결과·승인 근거를 대조한다. 내용이 달라지거나 승인 근거를 복구할 수 없으면 현재 변경을 다시 제시한다. 사용자의 질문이나 이전 분할안 승인으로 커밋 승인 상태를 만들지 않는다.
- 다음 이슈의 시작 조건은 [병합·완료 확인](completion-gate.md)을 따른다. 기록의 병합 여부를 최신 GitHub 조회 대신 사용하지 않는다. 완료 이슈 기록 정리 전에 순서·남은 작업·요구사항 배정·참조를 다음 기록이나 인계 요약에 보존한다.
