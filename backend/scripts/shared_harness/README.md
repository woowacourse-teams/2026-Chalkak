# 공통 하네스 배포와 사용

백엔드는 `be/develop` 기반 작업 브랜치에서 공통 하네스를 수정한다. 검토한 공통 파일만 전용 배포 브랜치에 게시하고, Android는 그 배포본을 별도 cache에 내려받아 프로젝트 안에 설치한다. Android 개발 브랜치에 백엔드나 배포 브랜치를 병합하지 않는다.

**현재 상태 (2026-09-23):** 작업 브랜치를 push해 실제 GitHub Actions에서 최초 게시·문서만 수정·문서만 삭제를 각각 검증했다. `harness/validation-477`의 공통 파일을 원본과 대조하고 실제 client/develop 임시 복제본에서 설치·읽기 전용·sync/apply 버전 전환을 확인했다. 정식 `harness/shared`는 아직 없으며, 검증용 실행 경로를 제거했으며 커밋·PR·병합·최초 정식 게시가 남아 있다. 실제 팀원 설치·macOS 로그인·AI 정책 적용은 미검증이다.

## 무엇을 함께 공유하는가

- 저장소 최상위 `AGENTS.md`·`CLAUDE.md`
- `.agents/skills/business-rules/`·`.claude/skills/business-rules/` 전체
- `docs/business-rules/`의 현재 규칙·결정 기록·템플릿·참조 문서
- 위 문서에서 연결한 캘린더 설계 기록 한 개 — `manage.py`의 `REFERENCES` 허용 목록에 명시

백엔드 전용 Java 규칙·리뷰 자동화·개발 순서 스킬은 배포하지 않는다. 참조 파일을 추가하면 허용 목록과 검사도 함께 갱신한다. 배포 시 로컬 Markdown 링크 대상 누락을 검사하므로 참조 문서 없이 스킬만 전달할 수 없다. 링크 검사는 단순 인라인 Markdown 링크의 경로를 검사하며 문서의 의미·외부 URL·앵커는 검증하지 않는다.

원본 커밋, 전체 파일의 SHA-256, 파일 목록으로 계산한 버전을 `manifest.json`에 남긴다. 해시는 전송·수정 오류 검출용이며 서명이 아니다. 팀이 관리하는 원격과 검토된 배포 브랜치를 사용한다.

## 한 번 준비할 것

Python 3.10 이상, Git, 저장소 읽기 인증이 필요하다. macOS/Linux 로컬 작업을 지원한다. Windows·클라우드 세션은 이 설치 방식의 검증 대상이 아니다.

설치 도구는 검토된 백엔드 커밋의 `backend/scripts/shared_harness/manage.py`를 별도 위치에 복사해 사용한다. Android에 이 스크립트가 없으면 백엔드 담당자가 검토된 파일과 원본 커밋을 전달한다. 배포 파일을 내려받는 것만으로 설치 도구 자체를 자동 교체하거나 실행하지 않는다. 도구 변경 시에는 새 파일을 검토한 뒤 다시 복사해야 한다.

정식 자동 게시 대상은 **`harness/shared`**다. 아직 원격에 생성하지 않았으며 `be/develop` 반영 후 최초 자동 게시가 생성한다. 사전 검증에는 별도 `harness/validation-477`을 사용했고, 해당 실행 경로는 검증 후 제거했다. 게시기는 원본 `be/develop`과 대상 `harness/shared`만 사용한다. 아래 컴퓨터 경로는 각자 실제 경로로 변경한다.

```bash
HARNESS_TOOL="$HOME/.local/share/chalkak-harness/manage.py"
HARNESS_CACHE="$HOME/.local/share/chalkak-harness/cache"
CHALKAK_PROJECT="$HOME/projects/2026-Chalkak"
```

위 변수는 같은 터미널에서 다음 명령에 사용한다. GitHub 인증은 기존 SSH 설정을 사용한다. 백그라운드 갱신은 암호 입력을 기다리지 않으므로 로그인 시 SSH 인증이 불가능하면 실패 기록을 남긴다.

## 백엔드 팀원: 코드와 문서를 같은 PR로 올리면 된다

1. `be/develop`에서 작업 브랜치를 만들고 **기능 코드와 관련 정책 문서를 함께 수정**한다. 원본 스킬이 있는 백엔드는 배포본을 중복 설치하지 않는다.
2. 같은 PR에 코드·문서를 올리고 리뷰받는다. 정책 변경의 이유와 Android에 미치는 영향도 설명한다.
3. `be/develop`에 병합하면 GitHub의 **Shared Harness** 워크플로가 관련 변경을 검사한다.
4. 검사가 통과하면 공통 지침·스킬·문서만 추출하여 **`harness/shared`에 자동 커밋·푸시**한다. 문서 삭제도 반영하며, 최초에는 앱 이력과 분리된 배포 브랜치를 자동 생성한다.
5. 담당자는 Actions의 결과를 확인하고 Android 팀에 **변경 규칙·앱 영향·서버 적용 여부**를 전달한다. 공통 하네스가 게시되었다고 API도 배포된 것은 아니다.

개발자는 파일을 따로 복사하거나 공통 브랜치에서 수동 커밋하지 않는다. 자동 게시가 실패하면 기존 정상 배포를 유지한다. 실패 원인을 수정하고 해당 워크플로를 다시 실행하거나 수정 PR을 병합한다.

### 자동 게시가 실행되는 조건과 보호

- 워크플로: [shared-harness.yml](../../../.github/workflows/shared-harness.yml), 게시 도구: [publish.py](publish.py).
- `be/develop` 대상 PR에서는 검사만 한다. `be/develop`의 관련 경로에 push가 발생한 뒤에만 게시한다. 정상 팀 흐름은 PR 병합이며, 직접 push도 기술적으로 같은 이벤트이므로 리뷰 강제는 저장소의 브랜치 보호 설정이 담당한다.
- 감지 경로는 공통 지침·스킬·정책, 백엔드 설계 기록, 하네스 도구·검사기·workflow다. 일반 기능 코드만 변경하면 이 워크플로는 실행하지 않는다.
- 구조 검사, 로컬 Git 통합 테스트, 원본 커밋 기반 export가 모두 성공해야 게시 작업이 실행된다. PR 검사에는 읽기 권한만, 게시 작업에는 `contents: write`를 부여한다.
- GitHub가 제공하는 `GITHUB_TOKEN`과 checkout 인증을 사용하며 별도 PAT를 요구하지 않는다. 저장소·조직의 Actions 권한이나 브랜치 규칙이 게시를 막으면 실패로 남기고 우회하지 않는다. 실제 권한은 첫 배포에서 확인한다.
- 게시기는 원본이 원격 `be/develop`에 포함되는지 확인한다. 이미 더 최신 원본이 게시되었다면 늦게 끝난 작업을 건너뛴다. 원본 이력이 갈라지면 중단한다.
- 같은 원본 커밋을 다시 실행하면 중복 게시하지 않는다. 공통 내용이 같아도 새 게시 대상 원본 커밋이면 manifest의 기준 커밋을 갱신해 순서를 추적한다.
- 별도 Git index로 배포 트리를 만들기 때문에 개발 브랜치를 바꾸거나 원본 코드·index를 수정하지 않는다. 최초 커밋에는 앱 이력이 없고, 이후에는 직전 배포 커밋에 이어서 게시한다.
- 일반 push만 사용한다. 다른 게시자가 먼저 원격을 갱신하면 실패하며 자동 강제 push나 무제한 재시도를 하지 않는다. 자동 게시가 만든 커밋은 `harness/shared`에만 기록되므로 이 workflow를 다시 촉발하지 않는다.
- Actions 실행 결과에 원본 커밋·배포 커밋·콘텐츠 버전을 남긴다. `harness/shared`를 사람이 직접 수정하는 운영은 사용하지 않는다.

### 게시 없이 배포 파일만 확인할 때

검토된 커밋에서 다음 명령으로 내용을 확인할 수 있다. 실제 게시·push는 하지 않는다. 미커밋 공통 변경은 거절하며 추적하지 않는 로컬 파일은 포함하지 않는다.

```bash
python3 "$HARNESS_TOOL" export \
  --source "$CHALKAK_PROJECT" \
  --output /tmp/chalkak-harness-release
```

## Android: 최초 설치

AI 세션을 시작하기 전에 실행한다. 설치 대상은 Android 하위 폴더가 아닌 **Git 저장소 최상위 경로**다.

```bash
python3 "$HARNESS_TOOL" sync \
  --cache "$HARNESS_CACHE" \
  --remote git@github.com:woowacourse-teams/2026-Chalkak.git \
  --branch harness/shared
python3 "$HARNESS_TOOL" install \
  --cache "$HARNESS_CACHE" \
  --project "$CHALKAK_PROJECT"
```

설치하면 다음 구조를 만든다.

| 위치 | 역할 |
| --- | --- |
| 공유 cache의 `versions/<버전>/` | 내려받은 공통 파일; 여러 checkout이 사용할 수 있음 |
| 프로젝트의 `.chalkak-harness/versions/<버전>/` | 프로젝트에서 읽을 완전한 버전 사본 |
| `.chalkak-harness/active` | 현재 적용 버전을 가리키는 연결; 명시적 apply에서만 교체 |
| 최상위 `.agents/skills/business-rules`·`.claude/skills/business-rules` | active 아래 각 도구의 스킬 디렉터리로 연결 |
| 최상위 `AGENTS.md`·`CLAUDE.md` | 기존 내용 끝에 표시된 공통 하네스 진입 구간 추가 |

`client/android/AGENTS.md`·`CLAUDE.md`는 수정하지 않는다. 공통 진입 구간과 Android 자체 지침이 함께 적용된다. 설치 대상에 같은 이름의 스킬이 있거나 경로가 외부 심볼릭 링크이면 덮어쓰지 않고 중단한다. 기존 지침 파일이 Git 추적 대상이면 진입 구간 추가가 로컬 변경으로 표시된다.

설치 파일은 각 컴퓨터·checkout 전용이다. 커밋하지 않는다. 로컬 Git 제외 파일에 아래 항목을 추가할 수 있다. 기존 내용을 유지하고 `git rev-parse --git-path info/exclude`로 실제 위치를 확인한다. 기존 **추적 파일의 변경은 exclude로 숨겨지지 않는다.**

```gitignore
/.chalkak-harness/
/.agents/skills/business-rules
/.claude/skills/business-rules
```

최초 설치로 새로 생성된 루트 `AGENTS.md`·`CLAUDE.md`도 로컬 전용으로 둘 경우에만 해당 두 파일을 제외한다. 플랫폼별 branch를 하나의 작업 폴더에서 전환하면 추적 파일과 설치 파일이 충돌할 수 있으므로 백엔드·Android는 별도 checkout/worktree를 사용하고 각 checkout에 설치한다.

설치 후 Codex·Claude의 **새 세션**을 시작한다. 스킬 목록에 `business-rules`가 나타나는지, 관련 기능 작업에서 실제 스킬과 필요한 규칙을 읽는지 확인한다. 사용자 범위에 같은 이름의 스킬이나 더 가까운 지침이 있으면 충돌 여부도 확인한다.

## 이후 업데이트와 AI 작업

```bash
python3 "$HARNESS_TOOL" status --project "$CHALKAK_PROJECT" --check
```

원격을 확인하고 새 배포를 cache에 내려받는다. 설치 버전, 사용 가능한 버전, 마지막 원격 확인의 성공·실패, 비교할 cache 경로, 변경된 파일을 보여준다. **프로젝트의 active 버전은 유지된다.** `--check` 없이 실행하면 마지막 확인 결과만 읽으므로 그 확인 시각 이후의 최신 여부는 알 수 없다.

AI는 작업 시작·재개에 이 명령과 관련 규칙 읽기를 연결한다. 변경 파일이 있으면 관련 문서의 전후 내용을 비교한다. 사용자가 현재 작업을 정리하고 적용할 시점에 다음 명령을 실행한다.

```bash
python3 "$HARNESS_TOOL" apply --project "$CHALKAK_PROJECT"
```

적용 후 새 AI 세션을 시작하고 이전 작업 기록을 이어받는다. 명령은 실행 중인 AI 세션을 감지하거나 자동 종료하지 않는다. 여러 세션이 같은 checkout을 쓰면 모든 세션을 정리한 뒤 적용한다. 동시 작업이 필요하면 별도 worktree를 쓴다.

AI가 실제로 읽은 뒤에는 예를 들어 “좋아요 관련 규칙과 설치 버전을 확인했고, 이번 작업에서는 정지 회원의 취소 허용 조건을 반영하겠습니다”처럼 **확인한 규칙과 적용 내용을 짧게 설명**하도록 했다. 이 예시 문구를 읽지 않고 반복하는 것은 검증이 아니다.

공통 문서의 게시와 API의 운영 배포는 다르다. 문서만 보고 미래 정책을 현재 API 동작으로 가정하지 않는다. 문서가 없거나 실제 API와 충돌하면 해당 쟁점만 백엔드 확인 대상으로 남긴다.

## Android: 공통 하네스는 읽기 전용, 변경은 백엔드에 요청

배포받은 공통 지침·스킬·정책은 파일 `0444`, 디렉터리 `0555`로 설치해 일반적인 수정·삭제·덮어쓰기를 막는다. 원본 백엔드 파일과 Android 자체 개발 지침은 잠그지 않는다. 업데이트는 기존 버전을 고치는 대신 새 버전을 설치하고 active 연결을 바꾼다.

정책이 불편하거나 변경하고 싶으면 Android 팀원·AI가 다음을 정리한다.

- 현재 규칙 ID와 내용
- 문제가 되는 상황과 이유
- 원하는 변경과 Android 화면·요청·오류 처리 영향

**백엔드에 전달 → 함께 논의·합의 → 백엔드 코드·규칙 PR → 병합 후 자동 재배포 → Android 새 버전 적용** 순서로 진행한다. AI는 전달할 문구를 작성하며 별도 요청 없이 메시지를 발송하지 않는다. 합의 전 제안을 현재 정책으로 구현하거나 권한을 풀어 배포본을 수정하지 않는다. 영향받지 않는 작업은 계속할 수 있다.

파일 권한은 실수 방지 장치다. 컴퓨터 소유자가 의도적으로 권한을 바꾸는 것까지 막지는 못하며, 그런 수정은 이후 해시 검사에서 감지한다. 중앙 배포 브랜치의 쓰기 권한은 별도의 GitHub 저장소 설정이며 이번 로컬 변경으로 팀원 권한을 변경하지 않았다.

## 선택: macOS 로그인할 때 내려받기

여기서 로그인은 **맥을 켜고 사용자 계정으로 들어가는 것**이다. GitHub·앱 로그인이 아니다. 최초 수동 sync가 성공한 뒤 각 컴퓨터에서 한 번 등록하면, macOS가 로그인 시 내려받기 프로그램을 실행한다. 프로그램은 별도 임시 폴더에 공통 브랜치를 받아 검사한 다음 cache에 저장한다. **Android 개발 폴더에서 git pull이나 merge를 실행하지 않는다.** 로그인 이후의 모든 push를 실시간 감시하지는 않는다. AI 작업 시작·재개의 `status --check`가 그 사이 변경을 확인한다.

```bash
python3 "$HARNESS_TOOL" login-plist \
  --cache "$HARNESS_CACHE" \
  --output "$HOME/Library/LaunchAgents/com.chalkak.shared-harness.plist"
launchctl bootstrap "gui/$(id -u)" \
  "$HOME/Library/LaunchAgents/com.chalkak.shared-harness.plist"
```

`login-plist`는 설정 파일을 생성할 뿐 등록하지 않는다. `launchctl bootstrap`은 사용자가 선택해서 실행한다. 등록 시 한 번, 이후 로그인 시 실행하며 프로젝트 apply·AI 호출·GitHub 메시지 게시를 하지 않는다. Python·SSH 경로·인증은 해당 컴퓨터에서 실제 로그로 확인해야 한다.

`cache/check.json`에 마지막 성공·실패, `cache/login.log`·`login-error.log`에 실행 결과가 남는다. 실패 시 다음 로그인이나 수동 `sync`·`status --check`로 다시 시도한다. 컴퓨터가 잠들어 있거나 꺼져 있을 때 계속 실행되는 서버 작업은 아니다.

등록을 해제하려면:

```bash
launchctl bootout "gui/$(id -u)" \
  "$HOME/Library/LaunchAgents/com.chalkak.shared-harness.plist"
```

해제 확인 후 해당 plist 파일만 삭제한다. 다른 LaunchAgent를 일괄 제거하지 않는다.

## 실패·제거·도구 갱신

- 원격 실패·잘못된 manifest·허용 범위 밖 파일·누락 링크: 최신 확인 실패를 기록하고 이전 정상 cache와 프로젝트 적용 버전을 유지한다.
- 설치 문서 수정·진입 구간 변경·스킬 연결 변경: apply·제거를 중단한다. 수정 내용을 별도 보존·검토한 뒤 정상 설치 상태를 복구한다. 강제 덮어쓰기 옵션은 없다.
- 설치 중 일반 파일 쓰기 실패: 기존 진입 지침을 복구하고 새 스킬 연결을 제거한다. 프로세스 강제 종료·전원 차단까지 완전한 트랜잭션을 보장하지 않으므로 불완전한 설치는 자동 덮어쓰지 말고 설치 기록·진입 표시 구간을 대조한다.
- 다른 배포 저장소·브랜치로 바꾸려면 새 cache를 사용한다. 기존 설치는 제거한 뒤 새 cache에 연결한다.
- 도구 자체가 바뀌면 검토된 새 스크립트를 사용한다. 프로젝트 안 도구 사본의 갱신은 세션을 닫고 제거·재설치한다. 로그인 도구도 기존 등록을 해제하고 새 plist로 다시 준비한다. 공통 문서만 갱신할 때는 재설치할 필요가 없다.

```bash
python3 "$HARNESS_TOOL" uninstall --project "$CHALKAK_PROJECT"
```

설치한 스킬 연결과 진입 표시 구간만 제거하며 그 밖의 사용자 지침을 보존한다. cache·versions·도구 사본은 비교·복구를 위해 남긴다. Git 원격과 이미 게시된 문서에는 영향이 없다. 로그인 등록을 사용했다면 별도로 해제한다.

## 검증

### 실제 GitHub Actions 사전 검증 — 추가·수정·삭제 통과

로컬 fixture 테스트만으로 GitHub 이벤트 감지·러너·실제 토큰의 push 권한을 확인할 수 없어, 사용자 승인 후 아래 세 번의 커밋·push를 실제로 검증했다. 각 실행에서 구조 검사·26개 테스트·검증 배포 게시가 통과했고, 정식 publish 작업은 건너뛰었다.

| 실제 실행 | 원본 커밋 | 자동 생성된 배포 커밋 | 확인 결과 |
| --- | --- | --- | --- |
| [최초 게시](https://github.com/woowacourse-teams/2026-Chalkak/actions/runs/35814456151) | `d39cd18` | `6669ba4` | 검증 브랜치 생성, 임시 문서를 포함한 22개 파일 일치 |
| [문서만 수정](https://github.com/woowacourse-teams/2026-Chalkak/actions/runs/35815014984) | `c9f1625` | `750edef` | 문서 한 줄 변경만으로 실행, 수정 내용·원본 SHA 반영 |
| [문서만 삭제](https://github.com/woowacourse-teams/2026-Chalkak/actions/runs/35815257055) | `1c44099` | `e91837a` | 임시 문서만 파일·manifest에서 제거, 실제 공통 파일 21개 보존 |

`client/develop@4b5ba2b`를 별도 임시 폴더에 복제해 설치했다. 최초 설치에서 기존 추적 파일 597개 보존과 Codex·Claude 스킬 연결·읽기 전용 쓰기 거절을 확인했다. 수정·삭제 게시마다 sync로는 기존 활성 버전이 유지되고, 변경 목록에 임시 문서 하나만 표시되며, apply 뒤에만 수정·삭제가 반영됐다. 이전 버전 사본과 실제 앱 파일도 유지됐다.

삭제한 `distribution-probe.md`는 이 검증을 위해 만든 비정책 문서다. 좋아요·사진 조회 등 실제 규칙을 삭제하지 않았다. 남은 21개 공통 파일의 내용은 최초 원본 커밋과 모두 일치했다. 로그인 plist가 생성한 실제 실행 명령도 직접 실행해 sync 성공·활성 버전 유지를 확인했지만, launchctl 등록이나 실제 로그아웃·로그인은 수행하지 않았다.

이 실행은 배포·설치 검증이다. AI가 실제 스킬을 읽고 정책을 적용했다는 증거로 사용하지 않는다. 원본 실행 로그와 전체 SHA·버전은 로컬 `backend/build/shared-harness-validation/`에 보관한다. 아래 절차 1~4의 실제 검증을 완료했고, 5의 임시 실행 경로와 전용 테스트 2개도 제거했다. 현재 회귀 테스트는 운영 경로를 다루는 24개이며, 위 실제 Actions 실행 시점에는 임시 경로 테스트를 포함한 26개였다. 원격 검증 브랜치는 실행 증거로 남겨 두었으며 팀 설치에 사용하지 않는다.

- 원본: `be/feature/#477-shared-harness` (현재 작업 브랜치)
- 검증 배포: `harness/validation-477` (팀 설치 대상 아님)
- 임시 문서: `docs/business-rules/distribution-probe.md` (정책이 아닌 배포 확인용)
- 검증 당시 임시 `publish-validation` 작업과 게시기의 `--validation`은 같은 검사·추출·게시 구현을 사용하되 위 두 브랜치만 연결했다. 검증 후 두 경로를 제거했으며 현재 `publish`는 `be/develop` push에서만 실행한다.

| 순서 | 실제 변경·작업 | 확인할 근거 |
| --- | --- | --- |
| 1 | 구현과 임시 문서의 `검증 단계: 1 (추가)`를 작업 브랜치에 커밋·push | 해당 push SHA의 Actions 실행, verify 성공, 검증 게시 성공, 정식 publish 건너뜀, 검증 브랜치 최초 생성 |
| 2 | 임시 문서만 `검증 단계: 2 (수정)`으로 변경해 커밋·push | 문서 경로만 바뀌어도 실행되는지, 게시 manifest의 source_commit이 해당 SHA인지, 원격 문서 내용·해시·버전 갱신 |
| 3 | 임시 문서만 삭제해 커밋·push | 삭제가 실행을 촉발하고 배포 파일·manifest에서도 제거되는지 |
| 4 | 각 검증 배포본을 새 cache와 임시 client checkout에서 sync/install/apply | sync만으로 활성 버전이 바뀌지 않는지, 변경 목록·명시적 apply·삭제·읽기 전용 권한, 기존 Android 지침·개발 코드 보존 |
| 5 | 검증 완료 후 임시 trigger/job·`--validation` 경로·전용 테스트를 제거하고 재검사 | 임시 정책 문서가 없고 정식 게시 경로만 PR에 남는지 |

각 단계의 Actions 실행 URL·원본 SHA·배포 SHA·콘텐츠 버전·관찰 결과를 기록했고, 실행 완료 후 다음 변경을 진행했다. `be/develop`과 정식 `harness/shared`는 사전 검증에서 변경하지 않았다. 원격 검증 브랜치는 증거 확인 후 별도 승인으로 정리하며 팀원 설치에 사용하지 않는다.

사전 검증 통과 후에도 **정식 PR 병합 → `be/develop` 이벤트 → 최초 `harness/shared` 게시**는 한 번 더 확인해야 한다. 검증 브랜치의 성공은 정식 브랜치에 적용되는 권한·브랜치 규칙까지 보장하지 않는다. 실제 팀원 설치·macOS 로그인 실행·Codex/Claude의 정책 읽기는 각각 별도 검증으로 남긴다.

### 로컬 회귀 검사

백엔드 디렉터리에서:

```bash
python3 -B -m unittest discover -s scripts/shared_harness -p 'test_*.py'
python3 scripts/check_harness.py
```

구조 검사에는 기존 `scripts/requirements-harness.txt` 환경을 사용한다. 설치 도구와 통합 테스트는 Python 표준 라이브러리·Git만 사용하며 임시 저장소에 fixture 커밋을 만든다. 실제 프로젝트 커밋·네트워크·AI 호출·로그인 등록은 하지 않는다.

통합 테스트는 기존 파일 보존, 스킬과 참조 경로, 추가·변경·삭제, 적용 버전 고정, 원격 실패, 변조·경로 충돌, 쓰기 실패 복구, 배포 원본의 미커밋 변경 및 누락 참조 거절을 검증한다. 실제 AI가 읽고 올바르게 판단하는지와 실제 macOS 로그인 실행은 별도 확인 대상이다.

- [Codex의 프로젝트 스킬 위치·심볼릭 링크 지원](https://learn.chatgpt.com/docs/build-skills#where-codex-loads-local-skills)
- [Claude Code의 프로젝트 스킬·부모 디렉터리·심볼릭 링크](https://code.claude.com/docs/en/skills#where-skills-live)
- [작업 이슈 #477](https://github.com/woowacourse-teams/2026-Chalkak/issues/477)

- [GitHub Actions의 브랜치·경로 필터와 이벤트](https://docs.github.com/en/actions/how-tos/write-workflows/choose-when-workflows-run/trigger-a-workflow)
