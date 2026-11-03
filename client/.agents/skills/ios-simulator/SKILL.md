---
name: ios-simulator
description: 찰칵 iOS 화면 코드를 바꾸는 작업에서 시뮬레이터에 앱을 빌드·설치·실행하고, 확인할 화면까지 이동해 수정 전·후를 같은 조건으로 캡처·녹화할 때 쓴다. 버그 재현 녹화와 전·후 비교 이미지 만들기도 포함한다.
user-invocable: false
---

# ios-simulator

화면을 확인하는 방법을 적는다. **어떤 화면을 어떤 순서와 도구로 볼지는 AI가 정한다.**
자료 형식과 비교 방법은 [화면 확인 자료](../client-pr/references/screen-evidence.md)를 따른다.
화면별로 가는 길과 재현 조건은 `references/<화면>.md`에 있다. 확인할 화면 파일이 있으면 먼저 읽는다.

## 막히면 멈춘다

- 로그인·시뮬레이터·서버 중 **무엇이 막혔는지** 적고 멈춘다. 예: `시뮬레이터 런타임 없음`, `앱 로그인이 풀려 있음`, `개발 서버가 502`.
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

## 시뮬레이터 준비

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

## 화면까지 이동

`simctl`에는 탭 명령이 없고, 앱에 화면 딥링크도 없다(URL 스킴은 로그인용뿐).
컴퓨터 화면 제어 도구로 Simulator 창을 조작한다. 쓸 수 있는 도구가 없으면 막힘으로 보고한다.

`references/<화면>.md`에 가는 길이 없으면 이번에 찾은 길을 `client-record`가 남길 수 있게 보고에 적는다.

## 캡처·녹화

```bash
xcrun simctl io "$UDID" screenshot "$EVID/media/before-1.png"

xcrun simctl io "$UDID" recordVideo --codec h264 "$EVID/media/repro.mp4"   # 백그라운드로 돌리고 조작
pkill -INT -f "simctl io .*recordVideo"                                     # INT로 멈춰야 파일이 닫힌다
```

수정 후에는 같은 조건으로 `after-<n>.png`를 찍고 `mark-changes.py`로 비교한다([화면 확인 자료](../client-pr/references/screen-evidence.md)).
