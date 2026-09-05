# 백엔드 AI 자동 리뷰

백엔드 PR이 등록되거나 새 커밋이 추가되면 선택한 AI가 사전 리뷰 댓글을 작성한다. 사람이 최종 리뷰·승인·병합을 담당한다.

## 대상

- base가 `be/develop`이고 `Server` 라벨이 있는 열린 PR
- Draft가 아니며 현재 GitHub 계정이 작성하지 않은 PR
- 같은 리뷰 계정이 현재 head SHA를 아직 리뷰하지 않은 PR

30개 파일 또는 1,200개 변경 줄을 초과하면 AI를 호출하지 않고 수동 검토가 필요하다는 댓글만 남긴다.

## 최초 설치

macOS에서 저장소를 받은 뒤 백엔드 디렉터리에서 실행한다.

```bash
git pull
cd backend
./scripts/pr-review/manage.sh install
```

화면에서 `1. Codex` 또는 `2. Claude`를 선택한다. `gh`와 선택한 AI CLI가 설치되고 로그인되어 있어야 한다. 선택값과 처리 기록은 `~/Library/Application Support/Chalkak PR Review/`에만 저장된다.

설치 후 macOS 로그인 시 자동으로 시작하고 `launchd`가 5분마다 검사 프로그램을 한 번 실행한다. 검사 사이에는 프로그램이 계속 떠 있지 않는다. 변경된 PR이 없으면 AI를 호출하지 않는다. 컴퓨터가 잠들거나 꺼진 동안 등록된 PR은 다음 실행에서 확인한다.

사용량이 한꺼번에 소모되지 않도록 한 번 확인할 때 새 PR 하나만 리뷰한다. 여러 PR이 대기 중이면 오래된 PR부터 5분 간격으로 처리한다.

## 관리

```bash
# 실행 상태와 선택한 AI 확인
./scripts/pr-review/manage.sh status

# 일시 중지와 재시작
./scripts/pr-review/manage.sh stop
./scripts/pr-review/manage.sh start

# AI 변경
./scripts/pr-review/manage.sh configure

# 즉시 한 번 확인하거나 실패 항목 재시도
./scripts/pr-review/manage.sh run-once
./scripts/pr-review/manage.sh retry

# 최근 로그 확인
./scripts/pr-review/manage.sh logs

# 자동 실행과 로컬 설정 완전 제거
./scripts/pr-review/manage.sh uninstall
```

`status`에는 마지막 GitHub 확인 시각·성공 여부·발견한 PR 수와 누적 처리 결과가 표시된다. 리뷰가 실제로 게시되면 GitHub PR과 `logs`에도 기록이 남는다.

감시 자체는 GitHub CLI만 사용하므로 AI 토큰을 소비하지 않는다. 조건에 맞는 새 head SHA를 발견해 실제 리뷰를 생성할 때만 선택한 계정의 사용량을 쓴다.

## 권한 범위

- AI는 읽기 전용 임시 세션에서 동작하며 PR 코드를 실행하거나 수정하지 않는다.
- GitHub 리뷰는 `COMMENT`로만 등록한다.
- 자동 승인, 변경 요청, 병합과 브랜치 삭제는 하지 않는다.
