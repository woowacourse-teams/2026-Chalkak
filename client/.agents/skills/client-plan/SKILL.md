---
name: client-plan
description: 찰칵 클라이언트 이슈를 읽고 관련 결정·가정·TC·테스트 밖 확인 항목·범위를 담은 계획을 대화에서 승인받은 뒤 이슈 댓글로 올린다. 사람이 /client-plan으로 부를 때만 쓴다.
argument-hint: "#이슈번호"
disable-model-invocation: true
---

# client-plan

먼저 [공통 약속](../client-flow/references/conventions.md)과 [계획 형식](references/plan-format.md)을 읽는다.
계획에는 **무엇이 되어야 하는지**만 쓴다. 클래스 구조나 파일 배치 같은 구현 방법은 쓰지 않는다(Refactor와 PR 리뷰에서 다룬다).

## 순서

1. **이슈 읽기**: `gh issue view <번호> --comments`. `확인 필요`로 남은 칸을 본다.
2. **바꿀 코드 찾기**: 플랫폼 `AGENTS.md`를 따르며 바꿀 파일 후보를 찾는다.
   - 바꿀 파일이 공통 약속 경로 표의 하네스 경로뿐이면 하네스 작업이다. [계획 형식](references/plan-format.md)의 "하네스 작업의 계획"으로 쓴다.
   - 하네스 경로와 Android·iOS·Web 경로가 섞이면 계획을 쓰지 않는다. `하네스와 <플랫폼> 코드는 이슈를 나눠 주세요`와 나눌 경계(파일 목록)를 알리고 멈춘다.
3. **관련 ADR 고르기**: 바꿀 파일 후보를 스크립트에 넣는다. 모든 ADR을 읽지 않는다.
   ```bash
   python3 client/.agents/skills/client-plan/scripts/related-adr.py <바꿀 파일>...
   ```
   - 출력된 ADR만 읽고 계획의 `관련 결정` 줄에 이름을 적는다. 없으면 `관련 결정: 없음`.
   - `관련 경로` 줄이 없다는 경고가 나오면 계획 끝에 알린다(그 ADR은 찾히지 않는다).
4. **서비스 정책 확인**: 정책이 걸린 작업이면 `business-rules`의 관련 규칙만 읽는다. 규칙이 없거나 서버와 다르면 가정에 `확인 필요`로 적고 백엔드 확인 대상으로 남긴다.
5. **계획 쓰기**: [계획 형식](references/plan-format.md)의 다섯 칸을 순서대로 쓴다.
6. **대화에 보여 주기**: 승인 전에는 이슈에 올리지 않는다. 승인, 수정 요청, 보류 중 하나를 받는다.
   - 수정 요청은 대화에서 바로 반영하고, 바뀐 줄만 표시해서 다시 보여 준다.
   - 보류면 멈춘다.
7. **승인되면 올리기**:
   ```bash
   EVID="$(git rev-parse --show-toplevel)/client/.work/<번호>"; mkdir -p "$EVID/media"
   # 승인된 계획을 "$EVID/plan.md"에 저장한 뒤
   gh issue comment <번호> --body-file "$EVID/plan.md"
   ```
   - 계획에서 이슈 내용이 바뀌었으면(`확인 필요`가 채워짐, 범위가 바뀜) `gh issue edit <번호> --body-file <새 본문>`으로 이슈 본문도 고친다.

## 계획이 바뀌어 다시 승인받을 때

`client-tdd`나 리뷰에서 TC, 테스트 밖 확인 항목, 화면 기준 중 하나가 틀렸다고 판단하면 여기로 돌아온다. 확인 항목과 화면 기준도 다시 승인받는 이유: PR 리뷰에서 사람이 확인하는 기준이라, 틀린 채 두면 고친 화면을 틀린 기준으로 확인하게 된다.

1. 바꿀 줄과 이유를 대화에 보여 주고 승인받는다. 바뀐 줄만 표시한다.
2. 이슈의 계획 댓글을 수정본으로 고친다. 댓글 맨 위의 `<!-- client-plan -->` 표시로 찾는다.
   ```bash
   id=$(gh api repos/{owner}/{repo}/issues/<번호>/comments --paginate \
     -q '[.[] | select(.body | startswith("<!-- client-plan -->"))] | last | .id')
   gh api -X PATCH repos/{owner}/{repo}/issues/comments/$id -F body=@"$EVID/plan.md"
   ```
3. 기록에 계획이 바뀐 것을 남긴다. TC가 바뀌었으면 바꾸는 플랫폼마다 `$EVID/evidence-<플랫폼>.md` 끝에 덧붙인다(덮어쓰지 않는다).
   ```text
   ## 계획 수정 (v2): TC 2 바뀜, TC 4 없앰
   ```
   이 줄 위의 바뀐 TC 기록은 무효다. 이어 가기가 옛 Green이나 옛 전체 검사 0을 보고 바뀐 TC를 건너뛰지 않게 하기 위해서다.
4. 이슈 본문의 `📎 참고 자료`에 화면 기준이 있었고 바뀌었으면 `gh issue edit`으로 고친다.
5. 돌아갈 곳:
   - TDD 도중이면 하던 묶음의 Red로 돌아간다. 처음부터 다시 하지 않는다.
   - 구현이 끝난 뒤(리뷰 반영 등)면 바뀐 TC와 새 TC만 Red부터 한다. 바꾼 테스트가 지금 구현에서 assert 실패로 떨어져야 Red다. 없앤 TC가 이번 작업에서 새로 쓴 테스트면 그 테스트와 구현을 걷어낸다(`client/develop`에 원래 있던 테스트는 지우지 않는다).
   - 확인 항목·화면 기준만 바뀌었으면 TDD를 다시 하지 않고 고친 뒤 전체 검사로 간다.
