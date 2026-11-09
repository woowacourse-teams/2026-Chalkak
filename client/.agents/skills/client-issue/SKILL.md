---
name: client-issue
description: 한 줄 설명, 스크린샷, 로그를 받아 찰칵 클라이언트 GitHub 이슈를 레포 템플릿대로 만들고 연결 브랜치까지 만든다. 사람이 /client-issue로 부를 때만 쓴다.
argument-hint: "<한 줄 설명>"
disable-model-invocation: true
---

# client-issue

먼저 [공통 약속](../client-flow/references/conventions.md)을 읽는다.
사람 확인 없이 만든다. 고칠 게 있으면 다음 단계인 계획에서 반영한다.

## 순서

1. **무엇을 받았나 보기**: 한 줄 설명, 스크린샷, 로그, 대화에서 나온 조사 결과를 쓴다.
   - 인자가 없으면 지금까지 대화를 이슈로 정리한다.
   - 무엇을 하는지조차 모르겠으면 선택지 질문 1~3개만 하고 만든다.
2. **템플릿 고르기**: `.github/ISSUE_TEMPLATE/`에서 종류에 맞는 파일을 읽는다.
   - 버그 `bug_report.md`, 기능 `feature_request.md`, 리팩터링 `refactor.md`, 문서 `docs.md`, 그 밖 `chore.md`
3. **제목·본문 쓰기**: 템플릿의 제목 접두사(`fix: ` 등)와 칸을 그대로 쓴다.
   - 두 플랫폼을 함께 바꾸면 제목을 `클라이언트`로 시작한다. 예: `feat: 클라이언트 알람 모두 읽기 버튼 추가`
   - 모르는 내용은 지어내지 않고 `확인 필요`로 남긴다. 계획 단계에서 채운다.
   - 버그는 재현 방법을 Given / When / Then으로 쓴다.
   - 이슈 확인 칸에는 이슈를 만드는 사람 말고, 확인해 줄 팀원을 `- [ ] @<로그인>`으로 넣는다. 자기 자신을 넣지 않는다.
     - 고르는 법: 내가 만든 지난 이슈 30개의 `👀 이슈 확인` 칸에서 가장 많이 언급한 사람이다. 나 자신과 이 레포에 배정할 수 없는 이름은 뺀다. 그래야 본문의 코드 어노테이션(`@LoginUser` 등)도 걸러진다.
       ```bash
       me=$(gh api user -q .login)
       allowed=$(gh api "repos/{owner}/{repo}/assignees" --paginate -q '.[].login' | tr 'A-Z' 'a-z')
       REVIEWER=$(gh issue list --state all --search "author:@me" --limit 30 --json body -q '.[].body' \
         | awk '/^## .*이슈 확인/{f=1;next} /^## /{f=0} f' \
         | grep -oE '@[A-Za-z0-9-]+' | tr -d '@' | tr 'A-Z' 'a-z' \
         | grep -vxi "$me" | grep -Fx -f <(printf '%s\n' "$allowed") \
         | sort | uniq -c | sort -rn | awk 'NR==1{print $2}')
       ```
     - `REVIEWER`가 비면(지난 이슈가 없는 팀원 등) 템플릿 그대로 `- [ ] 팀원명`으로 두고, 이슈를 만든 뒤 알릴 때 "이슈 확인 팀원을 채워 주세요"라고 적는다.
4. **이슈 만들기**:
   ```bash
   gh issue create --title "<제목>" --body-file <본문 파일> --label "<종류>" --label "<플랫폼>" --assignee @me
   ```
   라벨은 종류(`feat` `fix` `refactor` `docs` `chore` `test` `style`) 하나와 바꿀 플랫폼(`Android` `iOS` `Web`)마다 하나씩이다. 두 플랫폼이면 `--label Android --label iOS`. 하네스(공통 약속의 경로 표)도 `--label Android --label iOS`다.
5. **연결 브랜치 만들기**: 이슈 페이지의 Development에 브랜치가 붙도록 GitHub에서 만들고, 지금 작업 폴더에서 그 브랜치로 바꾼다.
   ```bash
   git fetch origin
   git branch -a | grep 'client/'                  # 이름 규칙 확인
   gh issue develop <번호> --list                  # 이미 연결된 브랜치가 있으면 그걸 쓴다
   gh issue develop <번호> --base client/develop \
     --name "client/<종류>/#<번호>-<플랫폼>-<영어 요약>" --checkout
   ```
   - 지금 레포 규칙은 `client/{feature|fix|refactor|chore|docs}/#번호-{android|ios|web}-{요약}`이다.
     두 플랫폼을 함께 바꾸면 플랫폼 자리를 뺀다. 예: `client/refactor/#907-like-logic-update`
     하네스도 플랫폼 자리를 뺀다. 예: `client/chore/#952-harness-verify-step`
     종류 자리는 커밋 type과 달리 `feat`가 아니라 `feature`다. 예: `client/feature/#901-android-home-top-redesign`
   - `git branch -a`에서 다른 규칙이 보이면 그쪽을 따른다.
   - 커밋하지 않은 변경이 있으면 브랜치를 바꾸기 전에 알리고 멈춘다.
   - 원격에는 `client/develop`과 같은 위치의 빈 브랜치만 생긴다. 코드 push는 `client-pr`에서 한다.
   - `--base`를 주면 나중에 `gh pr create`의 기준 브랜치도 `client/develop`으로 잡힌다.
   - 연결 브랜치 만들기가 실패하면 이슈를 다시 만들지 않는다. 이슈 주소와 실패 메시지를 알리고 멈춘다.

## 알릴 것

이슈 주소, 연결 브랜치 이름, 이슈 확인에 넣은 팀원(못 찾았으면 채워 달라는 말), `확인 필요`로 남긴 항목.
