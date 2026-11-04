# 화면 확인 자료

화면 확인 스킬(`android-screen-check`, `ios-screen-check`)이 서브에이전트로 찍고 비교·판단까지 해서 돌려주고, `client-pr`이 PR에 올린다. 파일은 `$EVID/media/`에 두고 지우지 않는다.

## 형식

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

## 파일 이름

`<플랫폼>-before-<n>`, `<플랫폼>-after-<n>`, `<플랫폼>-compare-<n>`. 플랫폼은 `android` 또는 `ios`, `n`은 계획의 테스트 밖 확인 항목 번호다. 버그 재현 녹화는 `<플랫폼>-repro.mp4`. 업로드는 파일 이름만으로 경로를 정하므로 플랫폼을 빼면 두 플랫폼의 같은 번호가 서로 덮어쓴다.

## 전·후 비교: 번호 테두리

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

## 번호마다 판단

화면 확인 스킬(서브에이전트)이 번호마다 둘 중 하나로 적어 돌려준다. 계획의 테스트 밖 확인 항목과 맞춰 보고, 애매하면 `설명 못 함`으로 적는다.
코드를 고친 쪽(메인)은 `설명 못 함`으로 돌아온 번호의 비교 이미지를 직접 열어 확인한다.

- `의도한 변화 — <어느 항목의 무엇>`
- `설명 못 함 — <무엇이 보이는지>`

`설명 못 함`이 하나라도 있으면 PR 전에 원인을 찾아 고치거나, 고칠 수 없으면 📸 칸 맨 위에 그대로 적고, PR을 열고 알릴 때 먼저 말한다.

## 📸 칸 쓰기

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
