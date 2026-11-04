---
name: client-pr
description: 검증과 기록이 끝난 찰칵 클라이언트 작업을 코드·문서 커밋으로 나누고 PR 본문을 쓴 뒤, 묻지 않고 push·캡처 업로드·드래프트 PR까지 연다. 드래프트 해제는 사람이 한다. 리뷰 반영 뒤 추가 커밋에도 쓴다. 사람이 /client-pr로 부를 때만 쓴다.
argument-hint: "#이슈번호"
disable-model-invocation: true
---

# client-pr

먼저 [공통 약속](../client-flow/references/conventions.md)과 [PR 본문 형식](references/pr-body.md)을 읽는다.
화면 확인을 한 작업일 때만 [화면 확인 자료](references/screen-evidence.md)도 읽는다(캡처 업로드와 📸 칸 쓰기).
문서는 별도 PR 없이 같은 PR에 넣는다.

사람은 터미널이 아니라 드래프트 PR에서 코드, 본문, 기록, 캡처를 본다. 그래서 push부터 드래프트 PR까지 묻지 않고 한다.
리뷰할 만한지는 사람이 PR을 보고 정하고, 드래프트도 사람이 푼다.

## 순서

1. **전제 확인**: 마지막 push 전 전체 검사가 통과한 뒤로 코드가 바뀌지 않았는지 본다. 바뀌었으면 전체 검사부터 다시 한다.
2. **커밋**:
   - 코드 커밋: `feat: 작가 프로필 소개 표시 (#123)`. 코드 옆 함정 주석은 코드 커밋에 같이 넣는다.
   - 문서 커밋: ADR은 따로 `docs: 프로필 소개 말줄임 기준 기록 (#123)`.
   - 남길 기록이 없으면 문서 커밋도 없다.
3. **PR 본문 쓰기**: [PR 본문 형식](references/pr-body.md)대로 `$EVID/pr.md`에 쓴다.
4. **push**: `git push -u origin HEAD`
5. **캡처 업로드**(화면 확인을 했을 때):
   ```bash
   client/.agents/skills/client-pr/scripts/upload-media.sh <이슈번호> "$EVID"/media/*-compare-*.png ...
   ```
   - 출력된 마크다운을 `$EVID/pr.md`의 `## 📸 화면 확인` 칸에 넣는다.
   - 올리지 않은 파일은 워크트리를 지울 때 같이 사라진다. 남겨야 할 녹화 원본(`android-repro.mp4` 등)이 있으면 함께 올린다.
   - 원격에 `client/pr-assets`가 없다고 나오면(종료 코드 3) 만들어도 되는지 묻고, 승인받으면 `--create-branch`를 붙여 다시 실행한다.
6. **드래프트 PR**:
   ```bash
   gh pr create --draft --base client/develop --title "<type>: <내용>" --body-file "$EVID/pr.md" \
     --label "<종류>" --label "<플랫폼>" --assignee @me   # 두 플랫폼이면 --label Android --label iOS
   ```
   - PR 제목 끝에는 `(#이슈번호)`를 붙이지 않는다. 커밋 제목과 달리 레포의 PR 제목에는 번호가 없고, 이슈는 본문 `close #번호`로 이어진다.
     예: 커밋 `fix: 조정 눈금자 눈금이 튀는 문제 수정 (#934)` → PR `fix: 조정 눈금자 눈금이 튀는 문제 수정`
   - 드래프트를 풀지 않는다(`gh pr ready` 금지).

## 리뷰에서 지적받았을 때

같은 PR에 추가 커밋으로 반영한다. 묻지 않고 push한다.

1. 고친다. 동작이 바뀌면 TC를 추가해 Red → Green으로, TC 자체가 틀렸으면 `client-plan`에서 다시 승인받는다.
2. push 전 전체 검사 → 수정 후 화면 확인(화면이 바뀌었으면) → `client-record` 다시 보기
3. 추가 커밋(`fix: 리뷰 반영 - <무엇> (#123)`), 본문 갱신
4. `git push`, 바뀐 캡처는 다시 업로드, `gh pr edit <PR번호> --body-file "$EVID/pr.md"`

## 알릴 것

- PR 주소와 "드래프트예요. PR을 보고 리뷰할 만하면 드래프트를 풀어 주세요."
- 올린 캡처 수, 리뷰어가 먼저 볼 곳(📸 칸의 `설명 못 함` 번호)
- 계획과 달라진 점(있으면). PR 본문의 어디에 적었는지
- 백엔드 전달 문구(있으면). 사람이 백엔드에 전달한다.
