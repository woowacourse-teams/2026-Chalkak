# 화면 확인 자료

지금은 화면 확인을 **사람이 PR 리뷰에서** 한다(#960). AI는 기기를 조작하지 않고(빌드·설치도 안 함), 드래프트 PR의 📸 칸에 사람이 확인할 체크리스트를 채운다.

- 왜: 자동 비교는 수정 전·후와 계획의 확인 항목만 보고 판단해서, 화면 기준(레퍼런스)이 틀리면 틀린 화면도 `의도한 변화`로 통과한다. 원래 의도를 아는 사람이 봐야 잡힌다(#938). 기기 조작은 비용과 막힘도 가장 크다(#952 시험에서 화면 확인 12번, #938 수정 전 확인 19분 막힘).
- 사람은 체크박스를 다 채운 뒤 드래프트를 푼다. 고칠 곳이 있으면 PR에서 수정을 요청한다(`client-pr`의 "리뷰에서 지적받았을 때").
- 아래 "자동 화면 확인"은 나중에 기기 조작을 자동화해 다시 붙일 자리다. 지금은 `client-tdd`가 부르지 않는다. 화면 확인 스킬은 사람이 캡처·녹화를 따로 요청할 때(`mode: adhoc`)만 쓴다.

## 📸 칸 쓰기 (사람 확인 체크리스트)

계획의 화면 기준과 테스트 밖 확인 항목에서 옮긴다. 번호는 계획의 항목 번호와 같다. 바꾼 플랫폼마다 체크박스를 하나씩 둔다.

```markdown
## 📸 화면 확인

사람이 확인해 주세요. 다 확인한 뒤 드래프트를 풀어 주세요. 고칠 곳은 PR에 남기고 `/client-pr #123 리뷰 지적: <무엇>`으로 알려 주세요.

화면 기준: 바텀시트 2안 (#938 댓글의 그림)

빌드 (개발 서버, 테스트 계정)
- Android (`client/android`에서): `./gradlew installDebug`
- iOS (`client/iOS/chalkak`에서):
  `xcodebuild build -project chalkak.xcodeproj -scheme chalkak -destination "platform=iOS Simulator,id=<UDID>" -derivedDataPath build/devsrv 'API_BASE_URL=https:/$()/chalkak-dev.pysun.kr/api/v1'`
  `xcrun simctl install <UDID> build/devsrv/Build/Products/Debug-iphonesimulator/chalkak.app`

1. 소개가 3줄을 넘으면 말줄임으로 끝난다
   - 가는 길: 홈 → 작가 이름 누르기 → 작가 프로필
   - 데이터: 소개가 4줄인 테스트 작가
   - [ ] Android
   - [ ] iOS
```

- 화면 기준은 계획 가정의 `화면 기준:` 줄을 그대로 옮긴다. 승인 때 사람이 정한 기준이 있으면 그것이다.
- Android 디버그 빌드는 개발 서버(`API_BASE_URL_DEV`)를 본다. iOS 기본 빌드는 운영 서버를 보므로 `API_BASE_URL`을 덮어쓴 위 명령을 쓴다. 개발 서버 로그인은 사람이 한다.
- 리뷰 반영으로 화면이 바뀌면 바뀐 항목의 체크박스를 다시 빈칸으로 둔다.
- 캡처는 사람이 확인하면서 필요하면 PR에 끌어다 붙인다. 공개 레포라 테스트 계정 화면만 붙인다.
- 화면 코드 변경이 없으면 `없음 (화면 코드 변경 없음)`.

## 자동 화면 확인 (지금은 안 씀)

화면 확인 스킬(`android-screen-check`, `ios-screen-check`)이 서브에이전트로 찍고 비교·판단까지 해서 돌려주고, `client-pr`이 PR에 올린다. 파일은 `$EVID/media/`에 두고 지우지 않는다.

### 형식

| 무엇 | 형식 | PR 본문에서 |
|---|---|---|
| 정지 화면 (기본) | PNG | 비교 이미지를 바로 보여 줌 |
| 녹화 원본 | mp4 (iOS는 mov를 mp4로 바꿔도 됨) | 링크만. PR 본문에서 재생되지 않는다 |
| 움직임이 문제일 때만 | GIF, 5초 이내, 폭 360px | 바로 재생 |

- 찰칵은 사진 앱이라 256색 GIF는 화질이 나쁘다. 전환·애니메이션처럼 움직임 자체가 확인 대상일 때만 GIF를 만든다.
- 녹화를 했어도 본문에는 핵심 프레임 PNG를 먼저 둔다.
- 10MB가 넘는 파일은 올리지 않는다(`upload-media.sh`가 막는다).

```bash
# 녹화에서 같은 시점 프레임 뽑기 (전·후 녹화를 같은 시점끼리 비교할 때)
ffmpeg -ss 1.20 -i "$EVID/media/android-before.mp4" -frames:v 1 "$EVID/media/android-before-1.png"
# 움직임 GIF (5초, 폭 360px)
ffmpeg -ss 0.5 -t 5 -i "$EVID/media/after.mp4" \
  -vf "fps=15,scale=360:-1:flags=lanczos,split[a][b];[a]palettegen[p];[b][p]paletteuse" \
  "$EVID/media/android-after-1.gif"
```

### 파일 이름

`<플랫폼>-before-<n>`, `<플랫폼>-after-<n>`, `<플랫폼>-compare-<n>`. 플랫폼은 `android` 또는 `ios`, `n`은 계획의 테스트 밖 확인 항목 번호다. 버그 재현 녹화는 `<플랫폼>-repro.mp4`. 업로드는 파일 이름만으로 경로를 정하므로 플랫폼을 빼면 두 플랫폼의 같은 번호가 서로 덮어쓴다.

### 전·후 비교: 번호 테두리

AI가 놓치는 건 의도하지 않은 변화다(#814의 탑바 아래 그림자 선). 그래서 바뀐 곳을 스크립트가 빠짐없이 표시하고, AI가 번호마다 설명한다.

```bash
python3 client/.agents/skills/client-pr/scripts/mark-changes.py \
  "$EVID/media/android-before-1.png" "$EVID/media/android-after-1.png" -o "$EVID/media/android-compare-1.png" \
  --skip-top "$SB"   # 상태 표시줄 높이. 구하는 법은 화면 확인 스킬의 "같은 조건 맞추기"
```

- 필요한 것: `python3 -m pip install --user pillow numpy`
- 출력 JSON의 `mode`
  - `marked`: 번호마다 판단을 적는다.
  - `side-by-side`: 바뀐 영역이 화면 절반을 넘었다. 번호 없이 나란히 두고 "화면 전체 변경"이라고 적는다.
  - `same`: 바뀐 곳이 없다. 의도한 변화가 있어야 하는 항목이면 이상이다.
- 비교 조건 맞추기는 화면 확인 스킬이 한다(상태 표시줄 고정, 애니메이션 끄기, 같은 데이터).
- 상태 표시줄은 항상 `--skip-top`으로 뺀다. 앱 화면이 아니라서 번호를 붙여도 리뷰어가 볼 것이 없다.
- 서버에서 늦게 오는 사진처럼 찍을 때마다 달라지는 영역은 `--ignore x,y,w,h`로 뺀다. 뺀 영역은 회색 테두리로 남는다.
- mp4에서 뽑은 프레임은 압축 잡음이 있으니 `--threshold 32`.

### 번호마다 판단

화면 확인 스킬(서브에이전트)이 번호마다 둘 중 하나로 적어 돌려준다. 계획의 테스트 밖 확인 항목과 맞춰 보고, 애매하면 `설명 못 함`으로 적는다.
코드를 고친 쪽(메인)은 `설명 못 함`으로 돌아온 번호의 비교 이미지를 직접 열어 확인한다.

- `의도한 변화 — <어느 항목의 무엇>`
- `설명 못 함 — <무엇이 보이는지>`

`설명 못 함`이 하나라도 있으면 PR 전에 원인을 찾아 고치거나, 고칠 수 없으면 📸 칸 맨 위에 그대로 적고, PR을 열고 알릴 때 먼저 말한다.

### 📸 칸 쓰기 (자동 확인 결과)

```markdown
## 📸 화면 확인

1. 소개가 3줄을 넘으면 말줄임으로 끝난다 (왼쪽 수정 전, 오른쪽 수정 후)
   ![android-compare-1.png](https://raw.githubusercontent.com/...)
   - ① 의도한 변화 — 소개 문구 영역 추가
   - ② 설명 못 함 — 탑바 아래 1px 가로줄
   - 녹화: [after.mp4](https://github.com/...)
```

- `설명 못 함`을 맨 위에 둔다. 리뷰어는 그것부터 본다.
- 기기 확인을 못 했으면 `못 함: <무엇이 막혔는지>. 사람이 확인해 주세요.`
- 화면 코드 변경이 없으면 `없음 (화면 코드 변경 없음)`.
