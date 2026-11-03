# 클라이언트 커맨드 공통 약속

`client-flow`, `client-issue`, `client-plan`, `client-tdd`, `client-record`, `client-pr`가 함께 따르는 약속이다.
커맨드를 하나만 불러도 이 파일을 먼저 읽는다.

## 사람이 확인하는 곳은 세 군데

1. 계획 승인 (`client-plan`)
2. push 확인 (`client-pr`)
3. PR 리뷰 (`client-pr` 이후)

그 밖에는 묻지 않고 진행한다. 다음 경우에만 멈추고 묻는다.

- 기기 환경이 막혔을 때(로그인 풀림, 에뮬레이터·시뮬레이터 없음, 서버 응답 없음)
- TDD 중 TC가 틀렸다고 판단했을 때(계획을 다시 승인받는다)
- 같은 검사가 4번 연속 실패했을 때(아래 "멈춤 규칙")
- 원격에 새 브랜치를 처음 만들 때(`client/pr-assets`)

멈춘 곳에서는 같은 대화에서 묻고, 답을 받으면 이어 간다. 커맨드를 다시 부르는 건 세션이 끊겼을 때만이다.

## 멈춤 규칙

같은 검사가 고친 뒤에도 **4번 연속** 실패하면 더 고치지 않고 멈춘다.
검사 하나는 Red 만들기, Green 만들기, push 전 전체 검사, 수정 후 화면 확인 중 하나다.
통과하면 횟수를 0으로 되돌린다.

멈출 때 알리는 것:

- 실패한 검사와 마지막 실패 메시지 한 줄
- 4번 동안 바꾼 것(무엇을 왜)
- 남은 가설과, 사람이 정해 줘야 하는 것

## 기준 브랜치와 플랫폼

- 기준 브랜치는 `client/develop`이다(`develop` 아님). 브랜치는 `origin/client/develop`에서 만들고 PR도 여기로 연다.
- 작업 브랜치는 `client-issue`가 이슈에 연결된 브랜치로 만든다(`gh issue develop`). 이때 원격에 빈 브랜치가 생기는 것은 묻지 않는다. 코드 push는 push 확인 뒤에만 한다.
- 플랫폼은 바꿀 파일 경로로 정한다.

| 경로 | 플랫폼 | 라벨 | 브랜치 이름의 플랫폼 자리 |
|---|---|---|---|
| `client/android/` | Android | `Android` | `android` |
| `client/iOS/` | iOS | `iOS` | `ios` |
| `client/web/` | Web | `Web` | `web` |

- Web은 이슈·계획·PR만 쓴다. TDD, Gradle·xcodebuild 검사, 기기 확인은 하지 않는다.
- 루트의 `.claude/`, `AGENTS.md`, `CLAUDE.md`는 백엔드 영역이라 고치지 않는다.
- 서비스 정책은 `business-rules`를 읽기만 한다. 정책이 없거나 서버와 다르면 백엔드에 전달할 문구로 남긴다.

## 명령어

| 하는 일 | Android (`client/android`에서) | iOS (`client/iOS/chalkak`에서) |
|---|---|---|
| 테스트 클래스 하나 | `./gradlew testDebugUnitTest --tests "*.FeedViewModelTest"` | `xcodebuild test -project chalkak.xcodeproj -scheme chalkak -destination "platform=iOS Simulator,id=$UDID" -only-testing:chalkakTests/FeedViewModelTests` |
| push 전 전체 검사 | `./gradlew ktlintCheck lintDebug testDebugUnitTest` | `xcodebuild test -project chalkak.xcodeproj -scheme chalkak -destination "platform=iOS Simulator,id=$UDID" -only-testing:chalkakTests` |

- iOS의 `$UDID`는 `xcrun simctl list devices available`에서 고른 iPhone 시뮬레이터다.
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
| `evidence.md` | `client-tdd` | TC마다 Red 메시지 한 줄과 Green 통과, 전체 검사 결과 |
| `media/` | 화면 확인 스킬 | `before-<n>.png`, `after-<n>.png`, `compare-<n>.png`, `repro.mp4` 등 |
| `route.md` | `client-tdd` | 수정 전 화면 확인 결과의 `route`. 수정 후에 같은 길·조건으로 찍을 때 `hint`로 넘긴다 |

`n`은 계획의 테스트 밖 확인 항목 번호다. 사용자에게 보고할 때는 `file://` 절대 경로로 적는다.

## 커밋 메시지

`type: 한글로 간결하게 (#이슈번호)`. `type`은 `feat`, `fix`, `chore`, `docs`, `refactor`, `test`, `style`, `perf`, `build`, `ci` 중 하나다.
