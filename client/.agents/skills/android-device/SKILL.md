---
name: android-device
description: 찰칵 Android 화면 코드를 바꾸는 작업에서 에뮬레이터·기기에 앱을 설치·실행하고, 확인할 화면까지 이동해 수정 전·후를 같은 조건으로 캡처·녹화할 때 쓴다. 버그 재현 녹화와 전·후 비교 이미지 만들기도 포함한다.
user-invocable: false
---

# android-device

화면을 확인하는 방법을 적는다. **어떤 화면을 어떤 순서와 도구로 볼지는 AI가 정한다.**
자료 형식과 비교 방법은 [화면 확인 자료](../client-pr/references/screen-evidence.md)를 따른다.
화면별로 가는 길과 재현 조건은 `references/<화면>.md`에 있다. 확인할 화면 파일이 있으면 먼저 읽는다.

## 막히면 멈춘다

- 로그인·기기·서버 중 **무엇이 막혔는지** 적고 멈춘다. 예: `에뮬레이터가 없음`, `앱 로그인이 풀려 있음`, `개발 서버가 502`.
- "빌드만 확인"이나 "유닛 테스트로 대신"으로 넘어가지 않는다. 사람이 "기기 확인 없이 진행"이라고 할 때만 넘어가고, PR에 못 한 이유를 적는다.
- 로그인은 사람이 한다. 계정 정보를 묻거나 입력하지 않는다.

## 남길 것

- 테스트 밖 확인 항목마다 수정 전·후를 **같은 조건으로** 찍은 캡처(버그면 재현 녹화).
- 수정 전은 **코드를 고치기 전에** 찍는다.
- 파일은 `$EVID/media/`에 두고 지우지 않는다. 보고에는 `file://` 절대 경로를 적는다.
  커맨드 없이 이 스킬만 불렸으면 먼저 폴더를 정한다. 브랜치 이름의 이슈 번호를 쓰고, 없으면 브랜치 이름을 쓴다.
  ```bash
  branch=$(git branch --show-current); num=$(printf '%s' "$branch" | grep -oE '#[0-9]+' | head -1 | tr -d '#')
  EVID="$(cd "$(git rev-parse --git-common-dir)" && pwd)/client-flow/${num:-$(printf '%s' "$branch" | tr '/#' '--')}"; mkdir -p "$EVID/media"
  ```

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
- **`connectedAndroidTest`는 실행 전에 사람에게 알린다.** 실행하면 앱이 지워져 로그인이 풀린다.

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

## 화면까지 이동

```bash
$ADB exec-out uiautomator dump /dev/tty     # 화면 요소와 좌표
$ADB shell input tap <x> <y>
$ADB shell input swipe <x1> <y1> <x2> <y2> 300
$ADB shell input keyevent KEYCODE_BACK
```

`references/<화면>.md`에 가는 길이 없으면 이번에 찾은 길을 `client-record`가 남길 수 있게 보고에 적는다.

## 캡처·녹화

```bash
$ADB exec-out screencap -p > "$EVID/media/before-1.png"

$ADB shell screenrecord --time-limit 20 /sdcard/repro.mp4   # 백그라운드로 돌리고 조작
$ADB shell pkill -INT screenrecord                          # 일찍 끝낼 때 (INT로 멈춰야 파일이 닫힌다)
$ADB pull /sdcard/repro.mp4 "$EVID/media/repro.mp4"
```

수정 후에는 같은 조건으로 `after-<n>.png`를 찍고 `mark-changes.py`로 비교한다([화면 확인 자료](../client-pr/references/screen-evidence.md)).
