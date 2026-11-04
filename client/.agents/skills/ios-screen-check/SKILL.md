---
name: ios-screen-check
description: 찰칵 iOS 앱 화면을 시뮬레이터에서 직접 확인할 때 쓴다. 화면 코드를 바꾼 작업의 수정 전·후 캡처, 버그 재현 녹화, 사람이 요청한 화면 캡처·녹화에 쓴다. 앱 설치·실행, 화면 이동, 상태 표시줄 고정, 전·후 비교 이미지 만들기를 포함하고 결과만 돌려준다.
user-invocable: false
context: fork
model: sonnet
---

# ios-screen-check

이 본문은 **화면 확인을 맡은 서브에이전트**가 실행한다. 기기 조작·화면 덤프·스크린샷은 서브에이전트 안에서만 쌓이고, 부른 쪽에는 아래 "돌려줄 결과"만 돌아간다.

- Claude Code는 `context: fork`로 자동으로 따로 떨어져 실행된다.
- Claude Code는 머리말의 `model: sonnet`으로 가벼운 모델에서 돈다. 찍고 비교하고 판단만 하는 일이라서다(공통 약속의 "서브에이전트 모델").
- 메인 대화에서 이 본문을 직접 읽었다면(Codex 등): 직접 하지 말고 서브에이전트를 만들어 이 파일 경로와 입력을 넘기고 결과만 받는다. 서브에이전트를 만들 수 없을 때만 직접 하되, 결과 형식은 같게 한다.
  - Codex는 `spawn_agent`의 `agent_type`을 `chalkak-screen-check`로 준다(`client/.codex/agents/chalkak-screen-check.toml`, `gpt-6.1-terra` 추론 `medium`). 그 이름을 모른다고 거부되면 `agent_type` 없이 만들고, 부른 쪽은 그 사실을 PR의 Technical Concerns에 적는다.

## 받는 입력

$ARGUMENTS

| 항목 | 내용 |
|---|---|
| `mode` | `before`(코드를 고치기 전), `after`(전체 검사 뒤), `adhoc`(사람이 요청한 캡처·녹화) |
| `items` | 계획의 테스트 밖 확인 항목. `번호: 내용`. `adhoc`이면 요청 내용 |
| `evid` | 작업 기록 폴더. 없으면 아래 방법으로 정한다 |
| `hint` | (선택) 화면까지 가는 길, 재현 조건, 테스트 데이터 |

입력이 빠져 무엇을 찍을지 정할 수 없으면 추측해서 찍지 않는다. `blocked`에 무엇이 빠졌는지 적어 돌려준다.

`evid`가 없으면 브랜치 이름의 이슈 번호로 정한다. 번호가 없으면 브랜치 이름을 쓴다.
```bash
branch=$(git branch --show-current); num=$(printf '%s' "$branch" | grep -oE '#[0-9]+' | head -1 | tr -d '#')
EVID="$(git rev-parse --show-toplevel)/client/.work/${num:-$(printf '%s' "$branch" | tr '/#' '--')}"; mkdir -p "$EVID/media"
```

## 할 일

- 어떤 화면을 어떤 순서와 도구로 볼지는 스스로 정한다. `hint`에 가는 길·조건이 있으면 그대로 따른다.
- 파일 이름 앞에는 플랫폼을 붙인다(`ios-`). 두 플랫폼을 함께 확인해도 같은 폴더에서 겹치지 않게 하기 위해서다.
- `before`: 항목마다 `ios-before-<n>.png`를 찍는다. 버그면 `ios-repro.mp4`도 녹화한다. 비교는 하지 않는다.
- `after`: `hint`로 받은 수정 전 `route`를 그대로 따라 `before`와 **같은 조건**으로 `ios-after-<n>.png`를 찍고, 항목마다 `mark-changes.py`로 `ios-compare-<n>.png`를 만든 뒤 번호마다 판단한다.
- `adhoc`: 요청대로 찍거나 녹화한다.
- 형식(PNG 기본, 움직임만 GIF)과 비교 방법은 [화면 확인 자료](../client-pr/references/screen-evidence.md)를 따른다.

## 번호마다 판단

`mark-changes.py`가 붙인 번호마다 둘 중 하나로 적는다.

- `의도한 변화 — <어느 항목의 무엇>`: `items`의 어느 항목으로 설명될 때만
- `설명 못 함 — <무엇이 보이는지>`: 그 밖의 모든 것. **애매하면 설명 못 함으로 적는다**

코드를 고친 쪽은 바뀐 곳을 의도대로라고 보기 쉽다. 이 판단은 그 치우침 없이 이미지와 항목만 보고 한다.

## 하지 않는 것

- 코드를 고치지 않는다. 찍고 비교하고 판단만 한다.
- 사람에게 묻지 않는다. 서브에이전트는 중간에 물을 수 없다. 막히면 멈추고 `blocked`로 돌려준다. 묻는 건 부른 쪽이 한다.
- "빌드만 확인"이나 "유닛 테스트로 대신"으로 넘어가지 않는다.
- 로그인하지 않는다. 계정 정보를 입력하지 않는다.
- 찍은 파일을 지우지 않는다.
- 앱을 지웠다 다시 설치해야 할 것 같으면 하지 말고 notes에 적는다(로그인이 풀린다).

막힘의 예: `시뮬레이터 런타임 없음`, `앱 로그인이 풀려 있음`, `개발 서버가 502`.

## 돌려줄 결과

이 형식만 돌려준다. 시도한 명령이나 화면 덤프 같은 과정은 넣지 않는다.

```text
mode: after
blocked: 없음                     ← 막혔으면 무엇이 막혔는지 한 줄
items:
  1. 소개가 3줄을 넘으면 말줄임으로 끝난다
     before: file:///…/media/ios-before-1.png
     after: file:///…/media/ios-after-1.png
     compare: file:///…/media/ios-compare-1.png (marked)
     ① 의도한 변화 — 소개 문구 영역 추가
     ② 설명 못 함 — 탑바 아래 1px 가로줄
route: ProfileScreen: 피드 → 사진 탭 → 작가 이름 탭 (긴 소개: 소개가 4줄 이상인 테스트 작가)
notes: 없음
```

`route`는 이번에 화면까지 간 길과 조건(테스트 데이터, 스크롤 위치, 비교에서 뺀 영역)이다. 앞에 `<Screen 이름>:`을 붙인다.
부른 쪽이 수정 후 확인 때 이 `route`를 `hint`로 다시 넘겨, 수정 전과 같은 조건으로 찍게 한다.

## 시뮬레이터 준비

`$UDID`는 [공통 약속](../client-flow/references/conventions.md)의 명령어 아래 고르는 법을 따른다(로컬 `CHALKAK_IOS_UDID`가 먼저).

```bash
xcrun simctl list devices booted               # 켜진 시뮬레이터가 있으면 그대로 쓴다
xcrun simctl list devices available | grep iPhone
xcrun simctl boot <UDID> && xcrun simctl bootstatus <UDID> -b
open -a Simulator
```

켜진 시뮬레이터를 지우거나(`erase`) 새로 만들지 않는다. 앱 데이터와 로그인이 날아간다.

## 빌드·설치·실행

```bash
cd client/iOS/chalkak
xcodebuild -project chalkak.xcodeproj -scheme chalkak \
  -destination "platform=iOS Simulator,id=$UDID" -derivedDataPath build/sim build
xcrun simctl install "$UDID" build/sim/Build/Products/Debug-iphonesimulator/chalkak.app
xcrun simctl launch "$UDID" com.stonefive.chalkak
```

다시 설치해도 앱 데이터는 남는다. 앱을 지우고 설치하면(`uninstall`) 로그인이 풀리니 하지 않는다.

## 같은 조건 맞추기

```bash
# 상태 표시줄 고정
xcrun simctl status_bar "$UDID" override --time "9:41" --batteryState charged --batteryLevel 100 \
  --wifiBars 3 --cellularMode active --cellularBars 4
# 끝나면
xcrun simctl status_bar "$UDID" clear
```

- 같은 시뮬레이터 기종, 같은 계정, 같은 데이터, 같은 화면 위치(스크롤)에서 찍는다.
- 시뮬레이터에는 애니메이션을 끄는 명령이 없다. 전환이 끝난 뒤(1초 정도 기다린 뒤) 찍는다.
- 비교할 때 상태 표시줄은 뺀다(`mark-changes.py --skip-top`). 앱 화면이 아니어서 바뀌어도 판단할 것이 없다.
  - iPhone 17 Pro(1206x2622)는 `186`(62pt × 3).
  - 다른 기종이면 수정 전 캡처 위쪽을 잘라 보고, 시계·배터리 아래이면서 앱의 첫 글자보다 위인 값으로 정한다.

## 화면까지 이동

`simctl`에는 탭 명령이 없고, 앱에 화면 딥링크도 없다(URL 스킴은 로그인용뿐).
컴퓨터 화면 제어 도구로 Simulator 창을 조작한다. 쓸 수 있는 도구가 없으면 막힘으로 보고한다.

이번에 간 길은 결과의 `route`에 적는다.

## 캡처·녹화

```bash
xcrun simctl io "$UDID" screenshot "$EVID/media/ios-before-1.png"

xcrun simctl io "$UDID" recordVideo --codec h264 "$EVID/media/ios-repro.mp4"   # 백그라운드로 돌리고 조작
pkill -INT -f "simctl io .*recordVideo"                                     # INT로 멈춰야 파일이 닫힌다
```

수정 후에는 같은 조건으로 `ios-after-<n>.png`를 찍고 `mark-changes.py --skip-top <상태 표시줄 높이>`로 비교한다([화면 확인 자료](../client-pr/references/screen-evidence.md)).
