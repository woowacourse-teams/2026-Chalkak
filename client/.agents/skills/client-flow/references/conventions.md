# 클라이언트 커맨드 공통 약속

`client-flow`, `client-issue`, `client-plan`, `client-tdd`, `client-record`, `client-pr`가 함께 따르는 약속이다.
커맨드를 하나만 불러도 이 파일을 먼저 읽는다.

## 사람이 확인하는 곳은 두 군데

1. 계획 승인 (`client-plan`)
2. PR 리뷰 (`client-pr` 이후): 사람이 드래프트 PR을 보고, 리뷰할 만하면 드래프트를 직접 푼다

push, 드래프트 PR은 묻지 않는다. 사람은 터미널보다 PR에서 코드·본문을 한 번에 보는 게 낫고, 드래프트라 리뷰 요청도 가지 않는다.

화면 확인의 판단은 사람이 PR 리뷰에서 한다. Android는 그 전에 플랫폼 서브에이전트가 스크립트로 자동 확인한다(#987, `client-tdd`의 [화면 자동 확인](../../client-tdd/references/screen-auto-check.md)). 멈춰 있는 모양은 Preview 스냅샷의 수정 전·후 비교, 움직여야 아는 것은 바로 열기 + 확인 스크립트(수정 전 실패 → 수정 후 통과)다. AI는 PR 📸 칸에 화면 기준, 자동 확인 결과와 캡처, 사람이 볼 항목(가는 길·데이터)을 채운다. 사람은 그것을 보고, 고칠 곳이 있으면 수정을 요청하고(`/client-pr #번호 리뷰 지적: <무엇>`), 다 확인되면 드래프트를 푼다.
- 왜 판단은 사람이: 화면 기준(레퍼런스)이 틀리면 자동 확인은 틀린 화면도 통과시킨다(#938).
- 왜 자동 확인: 모델이 기기를 눈으로 보며 조작하면 같은 확인의 반복이 비용의 78%였고, 로그인·서버 데이터에 자주 막혔다. 스크립트는 다시 돌릴 때 토큰이 들지 않고, 바로 열기는 로그인·서버가 필요 없다.
- 모델이 캡처를 보고 누를 곳을 정하는 기기 조작은 하지 않는다. 화면 확인 스킬은 사람이 캡처·녹화를 따로 요청할 때만 쓴다.
- 캡처는 공개 레포에 올라가 한 번 올리면 지워도 커밋 주소로 계속 열린다. 그래서 화면 확인은 테스트 계정으로만 하고, 테스트 계정에 공개하기 곤란한 사진을 두지 않는다.

그 밖에는 묻지 않고 진행한다. 다음 경우에만 멈추고 묻는다.

- 테스트 환경이 막혔을 때(iOS 테스트용 시뮬레이터 없음, 빌드 도구 오류)
- TDD 중 TC가 틀렸다고 판단했을 때(계획을 다시 승인받는다)
- 같은 검사가 4번 연속 실패했을 때(아래 "멈춤 규칙")
- 원격에 새 브랜치를 처음 만들 때(`client/pr-assets`, 사람이 요청한 캡처를 올릴 때만)
- 하네스 작업에서 시험 이슈를 새 세션으로 돌려야 할 때(`client-tdd`의 "하네스 작업일 때")

멈춘 곳에서는 같은 대화에서 묻고, 답을 받으면 이어 간다. 커맨드를 다시 부르는 건 세션이 끊겼을 때만이다.

## 멈춤 규칙

같은 검사가 고친 뒤에도 **4번 연속** 실패하면 더 고치지 않고 멈춘다.
검사 하나는 Red 만들기, Green 만들기, push 전 전체 검사, 하네스 문서 검사 중 하나다.
통과하면 횟수를 0으로 되돌린다.

멈출 때 알리는 것:

- 실패한 검사와 마지막 실패 메시지 한 줄
- 4번 동안 바꾼 것(무엇을 왜)
- 남은 가설과, 사람이 정해 줘야 하는 것

## 기준 브랜치와 플랫폼

- 기준 브랜치는 `client/develop`이다(`develop` 아님). 브랜치는 `origin/client/develop`에서 만들고 PR도 여기로 연다.
  - 예외: 하네스 시험 브랜치는 하네스 브랜치에서 따고 PR도 거기로 연다. 브랜치의 `gh-merge-base` 설정(`git config branch.<브랜치>.gh-merge-base`)에 기준 브랜치가 있다.
- 작업 브랜치는 `client-issue`가 이슈에 연결된 브랜치로 만든다(`gh issue develop`). 이때 원격에 빈 브랜치가 생기는 것은 묻지 않는다. 코드 push는 `client-pr`에서 push 전 전체 검사를 통과한 뒤에만 한다.
- 플랫폼은 바꿀 파일 경로로 정한다. 표에서 더 자세한 경로가 먼저다(`client/android/AGENTS.md`는 Android가 아니라 하네스). 플랫폼 `AGENTS.md`·`CLAUDE.md`는 AI 지침이라 앱 코드처럼 시험 측정을 흐리지 않는다.

| 경로 | 플랫폼 | 라벨 | 브랜치 이름의 플랫폼 자리 |
|---|---|---|---|
| `client/android/` | Android | `Android` | `android` |
| `client/iOS/` | iOS | `iOS` | `ios` |
| `client/web/` | Web | `Web` | `web` |
| `client/.agents/`, `client/.claude/skills/`, `client/.codex/`, `client/android/AGENTS.md`·`CLAUDE.md`, `client/iOS/AGENTS.md`·`CLAUDE.md` | 하네스 | `Android` `iOS` | 뺌(예: `client/chore/#952-harness-verify-step`) |

- Android와 iOS를 함께 바꾸면 라벨을 둘 다 달고, 제목은 `클라이언트`로 시작하고, 브랜치 이름에서 플랫폼 자리를 뺀다(예: `client/refactor/#907-like-logic-update`). 검증은 `client-tdd`대로 플랫폼별 서브에이전트가 동시에 한다.
- Web은 이슈·계획·PR만 쓴다. TDD, Gradle·xcodebuild 검사, 기기 확인은 하지 않는다.
- 하네스는 두 플랫폼 작업 모두에 쓰여서 라벨을 둘 다 단다(#898 선례). TDD 대신 문서 검사 → 실제 이슈로 시험 → v0 대비 측정을 한다(`client-tdd`의 "하네스 작업일 때"). 계획 칸도 다르다(`client-plan`의 계획 형식).
  - 하네스와 Android·iOS·Web 코드를 한 이슈에서 같이 바꾸지 않는다. 섞이면 계획을 쓰지 말고 이슈를 나눠 달라고 알리고 멈춘다. 시험으로 하네스 효과를 재는데, 앱 코드가 섞이면 무엇의 효과인지 알 수 없기 때문이다.
- 루트의 `.claude/`, `AGENTS.md`, `CLAUDE.md`는 백엔드 영역이라 고치지 않는다.
- 서비스 정책은 `business-rules`를 읽기만 한다. 정책이 없거나 서버와 다르면 백엔드에 전달할 문구로 남긴다.

## 명령어

하네스 문서 검사: `python3 client/.agents/skills/client-flow/scripts/check-harness.py` (레포 맨 위에서)

| 하는 일 | Android (`client/android`에서) | iOS (`client/iOS/chalkak`에서) |
|---|---|---|
| 테스트 클래스 하나 | `./gradlew testDebugUnitTest --tests "*.FeedViewModelTest"` | `xcodebuild test -project chalkak.xcodeproj -scheme chalkak -destination "platform=iOS Simulator,id=$UDID" -only-testing:chalkakTests/FeedViewModelTests` |
| push 전 전체 검사 | `./gradlew ktlintCheck lintDebug testDebugUnitTest` | `xcodebuild test -project chalkak.xcodeproj -scheme chalkak -destination "platform=iOS Simulator,id=$UDID" -only-testing:chalkakTests` |

- 두 플랫폼을 바꾸면 전체 검사도 둘 다 돌리고, 둘 다 종료 코드 0이어야 통과다.
- iOS의 `$UDID`는 앱이 지원하는 범위(iPhone, iOS 18.0 이상)의 시뮬레이터다. 기종은 하네스가 정하지 않고 개발자가 자기 로컬에서 정한다. 팀원마다 설치된 기종이 다르기 때문이다.
  - 로컬 환경 변수 `CHALKAK_IOS_UDID`가 있으면 그것을 쓴다(예: 셸 설정에 `export CHALKAK_IOS_UDID=<UDID>`).
  - 없으면 켜 둔 iPhone 시뮬레이터를, 그것도 없으면 가장 새 iOS의 iPhone을 고른다.
  ```bash
  UDID="${CHALKAK_IOS_UDID:-}"
  [ -n "$UDID" ] || UDID=$(xcrun simctl list devices available -j | python3 -c 'import json,sys,re
  d=json.load(sys.stdin)["devices"]
  c=[(tuple(map(int,re.findall(r"iOS-(\d+)-(\d+)",k)[0])),x["state"]=="Booted",x["udid"]) for k,v in d.items() if "iOS-" in k for x in v if x["name"].startswith("iPhone")]
  c=[x for x in c if x[0]>=(18,0)]
  print(max(c,key=lambda x:(x[1],x[0]))[2] if c else "")')
  ```
  - 기록에는 고른 기종과 iOS 버전을 적는다.
  - iPhone 17(Pro 아님)에서는 기존 `FeedPhotoRatioTests` 1건이 이번 변경과 상관없이 실패한다(#931). 그것만 실패하면 다른 iPhone에서 다시 돌리고, 두 결과를 기록에 적는다.
- 통과 여부는 종료 코드로 정한다. 출력을 훑어보고 "통과한 것 같다"고 쓰지 않는다.

## 작업 기록 폴더

세션이 끊겨도 이어 갈 수 있게, 커밋하지 않는 기록은 이 폴더에 둔다.

```bash
EVID="$(git rev-parse --show-toplevel)/client/.work/<이슈번호>"
mkdir -p "$EVID/media"
```

작업 폴더 안에 두고 git은 무시한다(`client/.gitignore`의 `.work/`). 작업 폴더 밖(공용 `.git` 등)에 두면 워크트리에서 읽고 쓸 때 권한에 막힌다.
작업 중에는 아무것도 지우지 않는다. 워크트리를 지우면 이 폴더도 같이 지워지므로, 남겨야 할 캡처·녹화는 `client-pr`이 `client/pr-assets`에 올려 보존한다.

| 파일 | 쓰는 커맨드 | 내용 |
|---|---|---|
| `plan.md` | `client-plan` | 승인된 계획(이슈 댓글과 같은 내용) |
| `evidence-<플랫폼>.md` | `client-tdd`의 플랫폼 서브에이전트 | 그 플랫폼 TC의 Red 메시지 한 줄과 Green 통과, 전체 검사 결과. 플랫폼 서브에이전트가 동시에 쓰므로 파일을 나눈다. 계획이 바뀌면 `## 계획 수정 (v2): …` 줄을 덧붙이고 그 아래 기록만 유효하다(`client-plan`) |
| `evidence-harness.md` | `client-tdd`(하네스) | 문서 검사 명령과 종료 코드 |
| `trial.md` | `client-tdd`(하네스) | 시험 이슈, 시험 브랜치, 하네스 브랜치·커밋, 확인할 TC 번호 |
| `metrics.md` | `client-tdd`(하네스) | 측정 표, TC마다 시험에서 확인됨/안 됨과 근거 |
| `media/` | `client-tdd`의 Android 서브에이전트(화면 자동 확인), 화면 확인 스킬(사람이 요청할 때) | 자동 확인 캡처(`android-before-<n>.png`, `android-after-<n>.png`, `preview-before/`, `preview-after/`, `preview-diff/`), 사람이 요청한 캡처·녹화(예: `ios-repro.mp4`) |
| `checks/` | `client-tdd`의 Android 서브에이전트 | 기기 항목의 확인 스크립트(`android-<n>.py`)와 판정 기록(`results.jsonl`) |
| `route-<플랫폼>.md` | (지금 안 씀) | 자동 화면 확인을 다시 붙이면 수정 전 `route`를 남긴다 |

플랫폼은 `android` 또는 `ios`, `n`은 계획의 테스트 밖 확인 항목 번호다. 사용자에게 보고할 때는 `file://` 절대 경로로 적는다.

## 서브에이전트 모델

| 누가 | Claude | Codex |
|---|---|---|
| 메인 대화 | 사람이 고른 모델 | 사람이 고른 모델 |
| 플랫폼 TDD 서브에이전트 | 지정 안 함(메인과 같음) | 지정 안 함(메인과 같음) |
| 화면 확인 서브에이전트(사람이 요청할 때만) | `sonnet` (스킬 머리말 `model`) | `gpt-6.1-terra`, 추론 `medium` (`client/.codex/agents/chalkak-screen-check.toml`) |

코드를 고치는 일은 고른 모델로 하고, 찍고 비교하는 화면 확인만 가벼운 모델로 돌린다. Codex가 레포의 에이전트 정의를 못 읽어 `agent_type`을 거부하면 지정 없이 띄우고, 그 사실을 PR의 Technical Concerns에 적는다.

## GitHub에 올리는 글

이슈 본문, 계획 댓글, PR 본문에는 `~`를 쓰지 않는다. 한 줄에 `~`가 두 개면 그 사이가 취소선이 된다. 범위는 `1–8월`처럼 `–`로 쓴다.

## 커밋 메시지

`type: 한글로 간결하게 (#이슈번호)`. `type`은 `feat`, `fix`, `chore`, `docs`, `refactor`, `test`, `style`, `perf`, `build`, `ci` 중 하나다.
