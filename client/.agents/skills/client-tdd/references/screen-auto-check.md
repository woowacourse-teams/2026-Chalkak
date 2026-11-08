# 화면 자동 확인 (Android, #987)

화면 코드를 바꾼 작업에서, 계획의 테스트 밖 확인 항목을 **모델이 기기를 눈으로 보며 조작하지 않고** 스크립트로 확인한다.
플랫폼 TDD 서브에이전트가 TC와 같은 흐름(수정 전 실패 → 수정 후 통과)으로 한다. 사람은 지금처럼 PR 리뷰에서 결과와 캡처를 보고 판단한다.

- 왜: 모델이 에뮬레이터를 직접 조작한 화면 확인은 38번 중 29번(비용의 78%)이 같은 확인의 반복이었고, 12번은 로그인·오늘 주제·검수 대기 데이터에 막혔다. 스크립트는 한 번 만들면 다시 돌릴 때 모델 토큰이 0이고, 바로 열기는 로그인·서버가 필요 없다(#987 시험: 키보드 가림 40/40, 한 번 9–21초).
- 지금은 Android만 있다. iOS는 아직 사람이 PR에서 확인한다.

## 항목마다 확인 방법 (계획의 `확인:` 줄)

| 확인 | 무엇에 | 도구 | 판정 |
|---|---|---|---|
| `Preview` | 멈춰 있는 모양: 문구, 배치, 상태별 화면 | Preview 스냅샷(Roborazzi, 기기 없음) | 수정 전·후 그림 비교. 바뀐 그림마다 `의도한 변화`/`설명 못 함` |
| `기기` | 움직여야 아는 것: 키보드, 포커스, 스크롤·제스처, 창 여백 | 바로 열기 + 확인 스크립트 | 스크립트가 참/거짓으로 판정 |
| `사람` | 위 둘로 못 보는 것: 실제 서버 응답, 화면 사이 이동이 실제 데이터에 따라 달라짐, 시스템 화면 | 없음 | PR 📸 칸 체크리스트 |

- 상태 유지·데이터 처리처럼 ViewModel로 확인되는 것은 화면 확인이 아니라 TC로 쓴다.
- `사람`으로 둘 때는 이유를 적는다(예: `사람 — 실제 서버의 응답 형식`).

## Preview

1. **수정 전 찍기**: 코드를 고치기 **전에** 바꿀 화면의 패키지만 찍는다.
   ```bash
   P=client/.agents/skills/client-tdd/scripts/android/preview_snapshots.py
   python3 $P record "$EVID/media/preview-before" com.stonefive.chalkak.feature.upload,com.stonefive.chalkak.feature.instagramconsent
   ```
2. 항목의 상태를 보여 주는 Preview가 없으면 만든다(아래 "Preview 작성 규칙"). 새 Preview는 수정 전 코드로도 찍어 둔다(수정 전 그림이 있어야 비교가 된다).
3. **수정 후 찍고 비교**: 전체 검사가 끝난 뒤.
   ```bash
   python3 $P record "$EVID/media/preview-after" com.stonefive.chalkak.feature.upload,com.stonefive.chalkak.feature.instagramconsent
   python3 $P diff "$EVID/media/preview-before" "$EVID/media/preview-after" "$EVID/media/preview-diff"
   ```
   - `changed`의 `_compare.png`(수정 전 | 차이 | 수정 후)를 열어 그림마다 `의도한 변화 — <항목 번호와 무엇>` 또는 `설명 못 함 — <무엇이 보이는지>`로 적는다.
   - 항목이 바뀌어야 하는데 그 그림이 `same`이면 이상이다(Preview가 그 상태를 못 보여 주거나 수정이 안 됨).
   - `blank_after`가 있으면 그 Preview를 고친다.

### Preview 작성 규칙

스냅샷은 화면이 처음 그려진 직후를 찍는다. 그 순간에 보이지 않으면 빈 그림이나 찍을 때마다 다른 그림이 된다(iOS에서 94장 중 10장이 빈 그림이었다).

- 상태를 받아 그리기만 하는 Composable(`*Screen(uiState, onAction)`, 컴포넌트)을 Preview한다. ViewModel·Route는 Preview하지 않는다.
- 등장 애니메이션이 있으면 Preview에서는 끝난 상태로 그린다.
- 시트·다이얼로그는 내용 Composable을 직접 그린다(띄우는 창은 찍히지 않는다).
- 로딩 상태는 로딩인 채로 멈춘 상태를 넘긴다.
- 이미지는 비동기 로딩 없이 첫 화면부터 보이는 앱 리소스(`R.drawable.preview_photo` 등)를 쓴다.
- 이름에 상태를 적는다(예: `name = "최신 전시 새로고침 중"`). 파일 이름이 되어 비교 결과에서 바로 읽힌다.

## 기기 (바로 열기 + 확인 스크립트)

### 1. 바로 열기 화면 추가

`client/android/app/src/debug/java/com/stonefive/chalkak/debuglaunch/`에 화면 파일을 하나 두고 `DebugScreens.all`에 등록한다. 예: `InstagramConsentDebugScreens.kt`.

- 디버그 빌드에만 들어가고 릴리즈에는 들어가지 않는다(`src/debug`).
- 실제 Screen·컴포넌트를 그대로 쓰고 상태만 만든다.
- **상태는 앱이 실제로 부르는 함수로 만든다**(예: 서버 거절 뒤 상태는 `withInvalidInstagramId()`). 필드를 손으로 채우면 실제로는 나올 수 없는 조합이 생겨 확인이 의미 없어진다.
- 사용자 동작으로 상태가 바뀌어야 하는 확인(스위치를 켜면 칸이 펼쳐짐 등)은 `onAction`을 받아 실제 상태 함수로 바꾼다.
- 서버 요청을 보내지 않는다. 이미지는 앱 리소스를 쓴다.
- 화면 사이 이동(탭 이동, 뒤로 가기)이 필요한 확인은 바로 열기로 재현되지 않는다. TC로 쓰거나 `사람`으로 둔다.

열기: `adb shell am start -S -n com.stonefive.chalkak.debug/com.stonefive.chalkak.debuglaunch.DebugScreenActivity --es screen <이름>`

### 2. 확인 스크립트

`$EVID/checks/android-<항목 번호>.py`에 쓴다. 공용 함수 `chalkak_ui.py`를 쓴다(화면 열기, 노드 읽기, 기다리기, 누르기, 밀기, 키보드 윗변, 캡처, 결과 한 줄).

```python
import sys; sys.path.insert(0, "client/.agents/skills/client-tdd/scripts/android")
import chalkak_ui as ui
ui.open_screen("upload-error")
msg = ui.wait_text("영문, 숫자, 마침표(.), 밑줄(_)만 쓸 수 있어요")
ui.screencap(sys.argv[1])            # 캡처 경로는 인자로 받는다(수정 전·후 이름이 다르다)
checks = dict(message_visible=bool(msg))
ui.record(sys.argv[2], name="android-4", ok=all(checks.values()), checks=checks)
sys.exit(0 if all(checks.values()) else 1)
```

- 판정은 노드의 글자·위치(px)로 한다. 기대 문구는 앱 소스의 상수에서 읽으면 문구가 바뀌어도 따라간다.
- 기다릴 때는 고정 시간 대신 `ui.wait`, `ui.wait_still`(위치가 멈출 때까지)을 쓴다. 애니메이션 중에 누르면 포커스가 안 잡힌다.
- 키보드 확인은 `with ui.soft_keyboard():` 안에서 한다. 에뮬레이터는 Mac 키보드가 붙어 있으면 화면 키보드를 띄우지 않는다(#939 판단 불가 원인). 끝나면 원래 값으로 되돌린다.
- 색은 캡처 픽셀로 본다(필요할 때만). 기준 거리를 정했으면 반대 경우(오류가 없는 화면)에서 떨어지는지도 한 번 돌린다.
- Compose testTag는 uiautomator에 `resource-id`로 보이지 않는다. 글자와 위치로 찾고, 같은 글자가 여럿이면 위치로 고른다.

### 3. 수정 전 실패 → 수정 후 통과

TC의 Red → Green과 같다.

1. 바로 열기 화면과 스크립트를 쓰고 `./gradlew installDebug` 후 **수정 전 코드로** 돌린다. 스크립트가 실패해야(종료 코드 1) 버그를 잡는 확인이다. 캡처: `$EVID/media/android-before-<n>.png`.
   - 수정 전에 통과하면 `처음부터 통과`로 적는다. 버그를 고치는 항목인데 처음부터 통과하면 확인이 버그를 못 잡는 것이니 스크립트를 고친다.
   - 수정 전 코드에 그 상태가 없어(새 필드 등) 바로 열기를 못 만들면 `수정 전: 만들 수 없음 — <이유>`로 적고 수정 후만 돌린다.
2. 구현과 전체 검사가 끝난 뒤 다시 설치하고 **3번** 돌린다. 3번 모두 통과해야 통과다(흔들림 확인). 캡처: `$EVID/media/android-after-<n>.png`.
3. 같은 확인이 고친 뒤에도 4번 연속 실패하면 공통 약속의 멈춤 규칙대로 멈춘다.

### 지키는 것

- 앱 삭제, 데이터 지우기, `connectedAndroidTest`는 하지 않는다(로그인이 지워진다). 설치는 `./gradlew installDebug`(덮어쓰기)만.
- 모델이 캡처를 보고 다음 누를 곳을 정하는 조작은 하지 않는다. 막히면 스크립트를 고치거나 `사람`으로 돌린다.
- 로그인이 필요한 확인은 하지 않는다. 사람이 요청한 캡처 등으로 실제 앱이 필요할 때 로그인이 풀려 있으면 `python3 client/.agents/skills/client-tdd/scripts/android/relogin.py <테스트 계정 표시 이름>`으로 기기에 등록된 테스트 계정을 고른다(비밀번호를 쓰지 않는다). 로그인 화면과 계정 고르기 화면은 캡처하지 않는다.

## 기록

`$EVID/evidence-android.md` 끝에 덧붙인다.

```text
## 화면 확인
- 1 스위치를 켜면 ID 칸과 안내가 펼쳐진다 (Preview)
  수정 전·후: 바뀜 2 (동의_켜짐, 동의_꺼짐) — 의도한 변화: 1번 스위치 행 추가 / 그대로 14 / 빈 그림 0
- 4 서버가 ID를 거절하면 ID 칸 아래 오류 문구가 오류 색으로 보인다 (기기: upload-error, checks/android-4.py)
  수정 전: 실패 — message_visible=false
  수정 후: 통과 3/3 (15.2초, 14.9초, 16.0초)
- 5 실제 서버가 ID 형식을 거절하는 응답 (사람 — 서버 동작)
```
