---
name: client-pr
description: 검증과 기록이 끝난 찰칵 클라이언트 작업을 코드·문서 커밋으로 나누고 PR 본문(사람이 할 화면 확인 체크리스트 포함)을 쓴 뒤, 묻지 않고 push·드래프트 PR까지 연다. 화면 확인과 드래프트 해제는 사람이 한다. 리뷰 반영 뒤 추가 커밋에도 쓴다. 사람이 /client-pr로 부를 때만 쓴다.
argument-hint: "#이슈번호"
disable-model-invocation: true
---

# client-pr

먼저 [공통 약속](../client-flow/references/conventions.md)과 [PR 본문 형식](references/pr-body.md)을 읽는다.
화면 코드를 바꾼 작업이면 [화면 확인 자료](references/screen-evidence.md)도 읽는다(📸 칸에 사람이 확인할 체크리스트 쓰기).
문서는 별도 PR 없이 같은 PR에 넣는다.

사람은 터미널이 아니라 드래프트 PR에서 코드, 본문, 기록을 보고, 📸 체크리스트대로 기기에서 화면을 확인한다. 그래서 push부터 드래프트 PR까지 묻지 않고 한다.
리뷰할 만한지는 사람이 PR을 보고 정하고, 드래프트도 사람이 푼다.

## 순서

1. **전제 확인**: 마지막 push 전 전체 검사가 통과한 뒤로 코드가 바뀌지 않았는지 본다. 바뀌었으면 전체 검사부터 다시 한다.
   - 하네스 작업이면 전체 검사 대신 문서 검사(`evidence-harness.md`)와 측정(`metrics.md`)을 본다. 고친 스킬은 시험 전에 이미 커밋·push했으니, 그 뒤 바뀐 것만 커밋한다.
2. **커밋**:
   - 코드 커밋: `feat: 작가 프로필 소개 표시 (#123)`. 코드 옆 함정 주석은 코드 커밋에 같이 넣는다.
   - 문서 커밋: ADR은 따로 `docs: 프로필 소개 말줄임 기준 기록 (#123)`.
   - 남길 기록이 없으면 문서 커밋도 없다.
3. **PR 본문 쓰기**: [PR 본문 형식](references/pr-body.md)대로 `$EVID/pr.md`에 쓴다.
4. **push**: `git push -u origin HEAD`
5. **📸 칸 확인**(화면 코드를 바꿨을 때): `$EVID/pr.md`의 `## 📸 화면 확인`이 [화면 확인 자료](references/screen-evidence.md)의 사람 확인 체크리스트인지 본다. 화면 기준, 항목 번호마다 가는 길·데이터와 바꾼 플랫폼마다 빈 체크박스, 개발 서버 빌드 명령이 있어야 한다.
   - Android 화면 자동 확인이 있으면 PR 그림(`$EVID/media/android-sheet-<k>.jpg`)과 그림에 쓴 원본(`$EVID/media/originals/`)을 **한 번의** `upload-media.sh` 호출로 올리고, 그림을 📸 칸 맨 위에, 원본 링크를 항목마다 넣는다([화면 확인 자료](references/screen-evidence.md)). 판정용 3칸 비교 그림(`preview-diff/*_compare.png`)은 올리지 않는다. 바로 열기 캡처와 Preview는 앱 리소스 이미지만 보여서 공개해도 된다. 로그인·계정 고르기 화면은 올리지 않는다.
     ```bash
     client/.agents/skills/client-pr/scripts/upload-media.sh <이슈번호> "$EVID"/media/android-sheet-*.jpg "$EVID"/media/originals/*
     ```
   - 사람이 요청해서 찍은 캡처·녹화(`mode: adhoc`)가 `$EVID/media/`에 있을 때도 올린다. 그때 원격에 `client/pr-assets`가 없다고 나오면(종료 코드 3) 만들어도 되는지 묻고, 승인받으면 `--create-branch`를 붙여 다시 실행한다.
       ```bash
       client/.agents/skills/client-pr/scripts/upload-media.sh <이슈번호> "$EVID"/media/<파일>...
       ```
6. **드래프트 PR**: 기준 브랜치는 보통 `client/develop`이고, 하네스 시험 브랜치면 그 하네스 브랜치다.
   ```bash
   BASE=$(git config "branch.$(git branch --show-current).gh-merge-base" || echo client/develop)
   gh pr create --draft --base "$BASE" --title "<type>: <내용>" --body-file "$EVID/pr.md" \
     --label "<종류>" --label "<플랫폼>" --assignee @me   # 두 플랫폼이면 --label Android --label iOS
   ```
   - PR 제목 끝에는 `(#이슈번호)`를 붙이지 않는다. 커밋 제목과 달리 레포의 PR 제목에는 번호가 없고, 이슈는 본문 `close #번호`로 이어진다.
     예: 커밋 `fix: 조정 눈금자 눈금이 튀는 문제 수정 (#934)` → PR `fix: 조정 눈금자 눈금이 튀는 문제 수정`
   - 드래프트를 풀지 않는다(`gh pr ready` 금지).
   - 기준이 하네스 브랜치면 본문 🛠️ Technical Concerns에 `하네스 시험 PR이에요. #<하네스 이슈> PR이 머지되면 기준을 client/develop으로 바꿔요.`를 적는다.

## 리뷰에서 지적받았을 때

같은 PR에 추가 커밋으로 반영한다. 묻지 않고 push한다.

리뷰 내용은 사람이 대화에 적어 준다(`/client-pr #123 리뷰 지적: <무엇>`). PR 댓글로만 남겼다고 하면 `gh pr view <PR번호> --comments`로 읽는다.

1. 계획이 바뀌는지 본다.
   - TC, 테스트 밖 확인 항목, 화면 기준 중 하나라도 바뀌면 `client-plan`의 "계획이 바뀌어 다시 승인받을 때"를 따른다. 잘못된 화면 기준(레퍼런스)으로 만든 경우가 여기다.
   - 계획은 그대로이고 구현만 고치면 바로 고친다. 동작이 바뀌면 추가 TC로 Red → Green, 모양만이면 바로 고친다.
2. 고친다. TC가 바뀌었으면 바뀐 TC만 Red부터 한다(`client-tdd`, 플랫폼 서브에이전트).
3. push 전 전체 검사 → `client-record` 다시 보기(바뀐 결정이 이미 남긴 기록과 어긋나면 그 기록을 고친다)
4. 추가 커밋(`fix: 리뷰 반영 - <무엇> (#123)`), 본문 갱신. 화면이 바뀐 항목의 📸 체크박스는 다시 빈칸으로 둔다. 계획이 바뀌었으면 검증 결과 표 아래에 `TC n은 계획이 바뀌었어요: <무엇을 무엇으로, 왜>`, 화면 기준이 바뀌었으면 📎 참고 자료에 바뀐 기준과 이유 한 줄.
5. `git push`, `gh pr edit <PR번호> --body-file "$EVID/pr.md"`

## 알릴 것

- PR 주소와 "드래프트예요. 📸 칸대로 기기에서 화면을 확인하고, 리뷰할 만하면 드래프트를 풀어 주세요. 고칠 곳은 `/client-pr #<번호> 리뷰 지적: <무엇>`으로 알려 주세요."
- 화면 확인 항목 수와 바꾼 플랫폼(화면 코드를 바꿨을 때)
- 계획과 달라진 점(있으면). PR 본문의 어디에 적었는지
- 백엔드 전달 문구(있으면). 사람이 백엔드에 전달한다.
