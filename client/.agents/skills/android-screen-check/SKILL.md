---
name: android-screen-check
description: 찰칵 Android 앱 화면을 에뮬레이터·기기에서 직접 확인할 때 쓴다. 화면 코드를 바꾼 작업의 수정 전·후 캡처, 버그 재현 녹화, 사람이 요청한 화면 캡처·녹화에 쓴다. 앱 설치·실행, 화면 이동, 상태 표시줄 고정, 전·후 비교 이미지 만들기를 포함하고 결과만 돌려준다.
user-invocable: false
context: fork
model: sonnet
---

# android-screen-check

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
- 파일 이름 앞에는 플랫폼을 붙인다(`android-`). 두 플랫폼을 함께 확인해도 같은 폴더에서 겹치지 않게 하기 위해서다.
- `before`: 항목마다 `android-before-<n>.png`를 찍는다. 버그면 `android-repro.mp4`도 녹화한다. 비교는 하지 않는다.
- `after`: `hint`로 받은 수정 전 `route`를 그대로 따라 `before`와 **같은 조건**으로 `android-after-<n>.png`를 찍고, 항목마다 `mark-changes.py`로 `android-compare-<n>.png`를 만든 뒤 번호마다 판단한다.
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
- `connectedAndroidTest`처럼 앱 데이터를 지우는 일이 필요했다면 실행하지 말고 notes에 적는다.

막힘의 예: `에뮬레이터가 없음`, `앱 로그인이 풀려 있음`, `개발 서버가 502`.

## 돌려줄 결과

이 형식만 돌려준다. 시도한 명령이나 화면 덤프 같은 과정은 넣지 않는다.

```text
mode: after
blocked: 없음                     ← 막혔으면 무엇이 막혔는지 한 줄
items:
  1. 소개가 3줄을 넘으면 말줄임으로 끝난다
     before: file:///…/media/android-before-1.png
     after: file:///…/media/android-after-1.png
     compare: file:///…/media/android-compare-1.png (marked)
     ① 의도한 변화 — 소개 문구 영역 추가
     ② 설명 못 함 — 탑바 아래 1px 가로줄
route: ProfileScreen: 피드 → 사진 탭 → 작가 이름 탭 (긴 소개: 소개가 4줄 이상인 테스트 작가)
notes: 없음
```

`route`는 이번에 화면까지 간 길과 조건(테스트 데이터, 스크롤 위치, 비교에서 뺀 영역)이다. 앞에 `<Screen 이름>:`을 붙인다.
부른 쪽이 수정 후 확인 때 이 `route`를 `hint`로 다시 넘겨, 수정 전과 같은 조건으로 찍게 한다.

## 기기 준비

```bash
ADB=~/Library/Android/sdk/platform-tools/adb      # PATH에 adb가 없을 때
$ADB devices                                       # 켜진 기기가 있으면 그대로 쓴다
~/Library/Android/sdk/emulator/emulator -list-avds # 없으면 있는 AVD를 켠다
~/Library/Android/sdk/emulator/emulator -avd <이름> -no-snapshot-save &
$ADB wait-for-device
```

AVD가 하나도 없으면 막힘으로 보고한다. 새 AVD를 만들지 않는다.

## 설치·실행

```bash
cd client/android && ./gradlew installDebug
$ADB shell am start -n com.stonefive.chalkak.debug/com.stonefive.chalkak.MainActivity
```

- debug 빌드의 패키지는 `com.stonefive.chalkak.debug`다.
- 설치 뒤 로그인 화면이 보이면 로그인이 풀린 것이다. 막힘으로 보고한다.
- **`connectedAndroidTest`는 실행하지 않는다.** 실행하면 앱이 지워져 로그인이 풀린다. 필요하면 결과의 `notes`에 적어 부른 쪽이 사람에게 묻게 한다.

## 같은 조건 맞추기

전·후 캡처 전에 매번 같은 상태로 맞춘다.

```bash
# 상태 표시줄 고정 (시계 9:41, 배터리 100, 알림 숨김)
$ADB shell settings put global sysui_demo_allowed 1
$ADB shell am broadcast -a com.android.systemui.demo -e command enter
$ADB shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 0941
$ADB shell am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false
$ADB shell am broadcast -a com.android.systemui.demo -e command network -e wifi show -e level 4
$ADB shell am broadcast -a com.android.systemui.demo -e command notifications -e visible false

# 애니메이션 끄기 — 움직임 자체를 확인할 때는 끄지 않는다
$ADB shell settings put global window_animation_scale 0
$ADB shell settings put global transition_animation_scale 0
$ADB shell settings put global animator_duration_scale 0
```

- 같은 계정, 같은 데이터, 같은 화면 위치(스크롤)에서 찍는다.
- 끝나면 되돌린다: `... -e command exit`, 애니메이션 세 값은 `1`.
- 시계를 고정해도 데모 모드를 다시 켜면 시계 글자가 몇 픽셀 옆으로 움직인다(#931). 그래서 비교할 때 상태 표시줄은 아래 높이만큼 뺀다.
  ```bash
  # 상태 표시줄 높이(px). 1080x2400 에뮬레이터는 63
  SB=$($ADB shell dumpsys window | grep -m1 -oE 'type=statusBars frame=\[0,0\]\[[0-9]+,[0-9]+\]' | grep -oE '[0-9]+\]$' | tr -d ']')
  ```

## 화면까지 이동

```bash
$ADB exec-out uiautomator dump /dev/tty     # 화면 요소와 좌표
$ADB shell input tap <x> <y>
$ADB shell input swipe <x1> <y1> <x2> <y2> 300
$ADB shell input keyevent KEYCODE_BACK
```

이번에 간 길은 결과의 `route`에 적는다.

## 캡처·녹화

```bash
$ADB exec-out screencap -p > "$EVID/media/android-before-1.png"

$ADB shell screenrecord --time-limit 20 /sdcard/android-repro.mp4   # 백그라운드로 돌리고 조작
$ADB shell pkill -INT screenrecord                          # 일찍 끝낼 때 (INT로 멈춰야 파일이 닫힌다)
$ADB pull /sdcard/android-repro.mp4 "$EVID/media/android-repro.mp4"
```

수정 후에는 같은 조건으로 `android-after-<n>.png`를 찍고 `mark-changes.py`로 비교한다([화면 확인 자료](../client-pr/references/screen-evidence.md)). 상태 표시줄은 `--skip-top "$SB"`로 뺀다.
