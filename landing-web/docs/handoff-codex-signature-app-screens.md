# Codex 핸드오프: 랜딩 체험에 사인 단계 추가, 히어로를 앱 화면으로 교체, 최신 앱 화면 직접 캡처

작성: 2026-10-13 · 대상 작업 폴더: `landing-web/` · 사용자 응답 언어: **한국어**

## 0. 지금 상태 (먼저 읽기)

- 작업 위치: 이 워크트리 `…/랜딩페이지`, 브랜치 `landing-web/feature/landing-page`(`origin/main` 기준). **아직 커밋 전**이며 `landing-web/`과 `.github/workflows/landing-web-ci.yml`이 미추적 상태다. 사용자가 요청하기 전에는 커밋·푸시·이슈 생성·배포를 하지 않는다.
- 랜딩페이지는 이미 동작한다(`npm run lint && npm run typecheck && npm run test && npm run build` 통과). 구조와 에셋 출처는 `landing-web/README.md`를 읽는다.
- `landing-web/AGENTS.md`: 이 Next.js(16.3.3)는 학습 데이터와 다를 수 있으니 필요한 API는 `landing-web/node_modules/next/dist/docs/`에서 확인한다.
- 공통 정책: `.chalkak-harness/` 아래 배포본은 **읽기 전용**이다. 문구가 정책에 닿으면 `docs/business-rules/rules/*.md`(sign.md, post.md 등)를 읽고 맞추되 수정하지 않는다.
- 하지 말 것: `client/`, `admin-web/`, `backend/` 수정 / `git stash`(다른 세션과 공유됨) / 이미 부팅된 `iPhone 17 Pro` 시뮬레이터 사용(다른 세션이 쓰는 중일 수 있음).

현재 체험 흐름: `topic(주제 카드) → shoot(가상 카메라) → review(미리보기 dialog) → reveal(시선 지도·전시)`
관련 파일: `src/features/experience/{experience.tsx, reveal.tsx, camera.ts, scene.ts, experience.module.css}`, `src/features/landing/{hero.tsx, app-guide.tsx, faq.tsx, landing.module.css}`

## 1. 할 일 요약

| # | 작업 | 완료 기준 |
| --- | --- | --- |
| A | 체험에 **앱과 같은 사인 흐름** 추가 | 찍은 뒤 사인 그리기 → "이렇게 보여요" 미리보기 → 전시에서 내 작품에 내 사인이 보인다 |
| B | **히어로의 풍경+프레임 그림 삭제**, 최신 앱 화면으로 교체 | 히어로 오른쪽에 실제 앱 캡처 2~3장(휴대폰 프레임) |
| C | **최신 앱을 직접 빌드·실행해 캡처**하고 사용법 섹션의 예시(mock) 화면을 전부 실제 캡처로 교체 | `public/app-screens/`가 최신 앱 캡처로 채워지고 mock 컴포넌트 삭제 |

사용자에게 캡처를 요청하지 않는다. 직접 실행해서 찍는다.

## 2. 작업 A — 사인 단계 (앱 온보딩과 동일하게)

### 2-1. 원본 동작 (iOS, `origin/client/develop` 기준)

읽을 파일: `git show origin/client/develop:client/iOS/chalkak/chalkak/Features/Onboarding/<파일>`
- `OnboardingSignatureScreen.swift`, `OnboardingSignaturePreviewScreen.swift`, `OnboardingSignatureModels.swift`, `OnboardingSignaturePngEncoder.swift`, `OnboardingRoute.swift`
- 토큰: `DesignSystem/Foundation/ChalkakColor.swift`, `ChalkakShape.swift`, `ChalkakSpacing.swift`, `ChalkakTypography.swift`

사인 화면 (`OnboardingSignatureScreen`)
- 제목 `작가님의\n사인을 그려주세요`(title1), 설명 `모든 사진에 함께할 사인이에요.\n자유롭게 남겨주시고, 실제 서명은 피해 주세요.`(subheadline, textSecondary)
- 패드: 바깥 카드(inputBackground `#FCFAF6`, 테두리 textPrimary 12% 1px, radius `shapes.large`, 패딩 16/16/16/14) 안에 **정사각형 캔버스**
  - 캔버스 배경 actionPrimary `#2B2724`, 안쪽 1px 점선 테두리(흰색 28%, dash 6, radius 10)
  - 선: 흰색, 굵기 4pt, round cap/join, 점 하나면 반지름 2 원
  - 비어 있을 때 가운데 `사인` 플레이스홀더(손글씨 서체, 흰색 56%, 크게)
  - 캔버스 아래 캡션 `여기에 손가락으로 그리기`(footnote, textMuted)
  - 좌표는 캔버스 대비 0~1 비율로 저장, 획마다 배열
  - 부드럽게 그리기: 중간 점들 사이 quadratic curve(제어점=현재 점, 끝점=현재·다음 점의 중점), 마지막 점은 직선 (`smoothPath`)
- 컨트롤: 캡슐 외곽선 버튼 `되돌리기`(반시계 아이콘, 마지막 획 삭제) · `전체 지우기`
- 주 버튼 `이 사인으로 할래요` — 획이 없으면 아무 동작 안 함

미리보기 화면 (`OnboardingSignaturePreviewScreen`)
- 제목 `이렇게 보여요`, 5:6 비율 사진(radius xlarge) 오른쪽 아래에 사인 PNG를 112×84 박스에 맞춰 얹음(오른쪽·아래 여백 `spacing.sm`)
- 버튼: 외곽선 `다시 그리기`(사인 화면으로) · 채움 `시작하기`

사인 PNG (`DefaultOnboardingSignaturePngEncoder`)
- 출력 1024×512 투명 PNG, 흰 선 굵기 14, round cap/join
- 모든 점의 bounding box를 구해(최소 크기 0.02) 패딩 48 안에 비율 유지로 꽉 차게 맞추고 가운데 정렬
- 점 하나짜리 획은 지름 14 원

### 2-2. 웹 구현 지침

- 흐름: `topic → shoot → review → sign → signPreview → reveal`
  - review의 `이 한 장으로` → sign. sign의 `이 사인으로 할래요` → signPreview. signPreview `다시 그리기` → sign, 주 버튼 → reveal.
  - 미리보기 사진은 앱의 `preview_sunset` 대신 **방금 찍은 내 한 장**(9:16 크롭)을 쓴다. 비율은 체험과 같은 9:16으로 둔다.
  - 미리보기 주 버튼 문구: 앱은 `시작하기`지만 웹에서는 다음 동작이 전시이므로 `전시하기`를 쓴다(그 외 문구·버튼 구성은 앱과 동일).
  - 다시 찍기로 돌아가도 그린 사인은 유지한다. `다시 찍어보기`(reveal 하단)도 사인 유지.
- 단계 표시(`.steps`): `주제 받기 → 골목에서 찍기 → 사인 남기기 → 다른 시선 보기`
- UI 위치: 지금 review가 쓰는 `<dialog>` 시트를 단계별로 이어 쓰거나 같은 스타일의 시트로 만든다. 휴대폰 폭에서는 화면 높이에 맞게(스크롤 없이 캔버스·버튼이 보이게) 조정한다.
- 파일 제안
  - `src/features/experience/signature.ts` — 순수 로직: 점 정규화·clamp, `smoothPath` 명령 생성, PNG 배치 계산(bbox→scale/offset). **vitest 테스트 필수**(`signature.test.ts`)
  - `src/features/experience/signature-pad.tsx` — 캔버스(pointer events, `touch-action: none`, devicePixelRatio 대응), 되돌리기/전체 지우기
  - `src/features/experience/signature-sheet.tsx` — 사인 화면 + 미리보기 화면
  - PNG는 오프스크린 `<canvas>`로 1024×512를 그려 `toDataURL("image/png")` → 상태에 보관. **서버 전송·저장 금지**
- 공개 단계 반영
  - 갤러리의 내 카드: 텍스트 `나` 대신 내 사인 PNG를 오른쪽 아래에 얹는다(앱 비율 112×84를 카드 폭에 맞게 축소).
  - 다른 작가 카드의 손글씨 사인(Nanum Pen Script)은 그대로 둔다.
- 접근성
  - 캔버스 `aria-label="사인 입력창"`, 버튼은 44px 이상
  - 키보드·보조기기 사용자를 위해 작은 보조 버튼 `예시 사인으로 채우기`를 둔다. 앱 `OnboardingRoute.previewStrokes`의 좌표를 그대로 쓴다. 자동 검증에서도 이 버튼으로 다음 단계로 넘어갈 수 있다.
- 문구 갱신
  - `faq.tsx` 마지막 문항 답변: 찍은 사진과 **그린 사인** 모두 어디에도 저장되지 않는다는 내용으로 바꾼다.
  - 체험 아래 안내(`.note`)도 같은 뜻으로 맞춘다.
- 정책 확인: SIGN-001~005(사인 규칙)와 어긋나는 표현을 쓰지 않는다. 웹 체험은 실제 등록이 아님을 분명히 한다.

## 3. 작업 C — 최신 앱 실행·캡처

최신 앱 소스는 **`origin/client/develop`**이다(`main`의 클라이언트는 iOS 0.2.0 릴리스 시점으로 147커밋 뒤처짐). 랜딩 브랜치를 건드리지 않도록 **별도 워크트리**에서 빌드한다.

### 3-1. 준비

```bash
git fetch
CAP=/private/tmp/chalkak-landing-capture        # 작업 후 삭제
git worktree add --detach "$CAP" origin/client/develop
# post-checkout 훅(~/.config/chalkak/hooks)이 Secrets.xcconfig, GoogleService-Info.plist를 준비한다. 생겼는지 확인:
ls "$CAP/client/iOS/chalkak/chalkak/Configuration/"
```
비밀 파일이 없으면 이 워크트리의 같은 파일을 **복사만** 하고 내용은 출력하지 않는다.

시뮬레이터는 전용으로 새로 만든다(Xcode 26.6, iPhone 17 Pro 런타임 있음).
```bash
UDID=$(xcrun simctl create "Chalkak Landing Capture" "iPhone 17 Pro")
xcrun simctl boot "$UDID"
xcrun simctl spawn "$UDID" defaults write -g AppleLanguages -array ko
xcrun simctl spawn "$UDID" defaults write -g AppleLocale ko_KR
xcrun simctl shutdown "$UDID" && xcrun simctl boot "$UDID"
xcrun simctl ui "$UDID" appearance light
xcrun simctl status_bar "$UDID" override --time 9:41 --batteryState charged --batteryLevel 100 --cellularBars 4 --wifiBars 3
```

빌드·설치 (Debug: 테스트용 실행 인자는 `#if DEBUG`에서만 동작)
```bash
cd "$CAP/client/iOS/chalkak"
xcodebuild -project chalkak.xcodeproj -scheme chalkak -configuration Debug \
  -destination "platform=iOS Simulator,id=$UDID" -derivedDataPath "$CAP/dd" build
xcrun simctl install "$UDID" "$CAP/dd/Build/Products/Debug-iphonesimulator/chalkak.app"
BUNDLE=$(/usr/libexec/PlistBuddy -c 'Print CFBundleIdentifier' "$CAP/dd/Build/Products/Debug-iphonesimulator/chalkak.app/Info.plist")
```

### 3-2. 화면별 진입 방법 (ContentView·OnboardingRoute의 DEBUG 실행 인자)

| 저장 파일(`landing-web/public/app-screens/`) | 화면 | 실행 인자 / 조작 |
| --- | --- | --- |
| `signature.webp` | 사인 그리기(**획이 그려진 상태**) | `-show-onboarding-signature` → 캔버스에 사인을 그린 뒤 캡처(드래그가 필요하므로 3-3의 UI 테스트로) |
| `signature-preview.webp` | 이렇게 보여요 | `-show-onboarding-preview` (앱에 내장된 예시 획) → 바로 캡처 |
| `today.webp` | 홈 · 오늘의 주제 | 게스트: `-stonefive.chalkak.guest-access YES -notificationSetup.hasCompleted YES` (개발 서버 실제 데이터) |
| `display-feed.webp` | 전시(최신순 그리드) | 위 게스트 상태에서 하단 `전시` 탭 |
| `display-detail.webp` | 작품 상세 + 좋아요 | 전시에서 사진 하나를 탭 |
| `upload.webp` | 사진 올리기 | `-test-photo-upload-entry` → 하단 `+` → 사진 선택 화면. 앨범 사진은 `xcrun simctl addmedia "$UDID" <이미지>`로 미리 넣는다(예: `public/scene/onggi-2x.webp`에서 9:16으로 자른 JPG) |

- 단순 화면: `xcrun simctl launch --terminate-running-process "$UDID" "$BUNDLE" <인자들>` → 몇 초 대기 → `xcrun simctl io "$UDID" screenshot <파일>.png`
- 앱 데이터 초기화가 필요하면 `xcrun simctl uninstall` 후 재설치
- `-test-photo-upload-entry`의 가짜 주제 제목은 `테스트`다. 캡처용 워크트리 안에서만 `ContentView.swift`의 DEBUG fixture 제목을 바꿔 다시 빌드해도 된다(예: 실제 운영 주제와 비슷한 단어). **이 변경은 절대 커밋·푸시하지 않는다.**
- 오늘의 주제가 비어 있으면(`오늘의 주제가 아직 준비되지 않았어요`) 그 화면은 쓰지 말고, 전시 화면에서 날짜를 지난 날짜로 옮겨 사진이 많은 주제를 고른다.
- 기록(달력)은 로그인이 필요하다. 테스트 계정이 없으면 건너뛰고 결과 보고에 적는다.

### 3-3. 그리기·탭이 필요한 화면 — 임시 XCUITest

캡처용 워크트리의 `chalkakUITests/`에 임시 테스트 파일(예: `LandingCaptureTests.swift`)을 만든다. **캡처용 워크트리에서만 쓰고 커밋하지 않는다.**
- `app.launchArguments = [...]`로 진입
- 사인: 캔버스(`accessibilityLabel "사인 입력창"`) 좌표에 `press(forDuration:thenDragTo:)`를 여러 번 이어 붙여 짧은 필기체 모양을 그린다
- 탭 이동: `app.buttons["전시"]` 등
- 캡처: `let a = XCTAttachment(screenshot: app.screenshot()); a.name = "signature"; a.lifetime = .keepAlways; add(a)`

```bash
xcodebuild test -project chalkak.xcodeproj -scheme chalkak -destination "platform=iOS Simulator,id=$UDID" \
  -derivedDataPath "$CAP/dd" -only-testing:chalkakUITests/LandingCaptureTests -resultBundlePath "$CAP/capture.xcresult"
xcrun xcresulttool export attachments --path "$CAP/capture.xcresult" --output-path "$CAP/shots"
```

### 3-4. 후처리와 반영

- 원본 PNG를 폭 642px WebP(quality 86)로 저장한다(Python PIL 사용 가능, 비율 유지).
- `app-guide.tsx`의 `SCREEN_WIDTH`/`SCREEN_HEIGHT`를 실제 캡처 비율로 맞춘다. iPhone 17 Pro 캡처는 1206×2622이므로 예: 642×1396.
- 기존 `display-*.webp`(App Store 0.2.0 스크린샷)는 새 캡처로 **교체**한다.
- 사용법 단계 매핑: 1 사인 → `signature.webp`(또는 `signature-preview.webp`), 2 오늘의 주제 → `today.webp`, 3 올리기 → `upload.webp`, 4 전시 → `display-feed.webp`, 5 좋아요·기록 → `display-detail.webp`
- 모든 단계가 실제 캡처가 되면 `app-guide.tsx`의 mock 컴포넌트(`SignatureMock`, `TodayMock`, `UploadMock`, `StatusBar`, `TabBar`), `existsSync` 분기, `landing.module.css`의 `.mock*` 스타일을 **삭제**하고 README의 "앱 화면 교체" 절을 갱신한다.
- 다른 사용자의 사진이 보이는 캡처(홈·전시·상세)는 개발 서버의 실제 게시물이다. 어떤 캡처에 다른 사용자 사진이 들어갔는지 결과 보고에 적어서 **공개 배포 전에 사용자가 확인**할 수 있게 한다. 신고·민감해 보이는 사진이 보이면 그 화면은 쓰지 않는다.
- 끝나면 정리: `xcrun simctl delete "$UDID"`, `git worktree remove --force "$CAP"`(임시 테스트·fixture 변경도 함께 사라짐). `git worktree list`로 확인한다.

## 4. 작업 B — 히어로 교체

- 삭제: `hero.tsx`의 `<figure className={styles.heroVisual}>…</figure>` 전체(풍경 이미지, 프레임 5개, 캡션 `같은 풍경, 같은 주제 '온기'. 다섯 사람이 찍은 다섯 장.`). `HERO_SHOTS`/`heroShots`/`frameRect` import, CSS의 `.heroScene`, `.heroFrame`, `.heroFrameLabel`, `.heroCaption`도 지운다.
- 추가: 최신 앱 캡처로 만든 휴대폰 프레임 구성
  - 데스크톱: 가운데 앞 `today.webp`, 좌우 뒤쪽 `display-feed.webp`와 `display-detail.webp`를 약간 작게, 살짝 겹치거나 기울여 배치
  - 960px 이하: 2장, 640px 이하: 1장(가로 스크롤 없이)
  - `next/image` + `priority`(가운데 1장만), 의미 있는 `alt`
  - 휴대폰 프레임 스타일은 사용법 섹션 `.phone`과 통일한다(공통 컴포넌트로 빼도 좋다).
- 히어로 문구와 CTA(`지금 한 장 찍어보기`, 스토어 배지)는 유지한다.
- OG 이미지(`src/app/opengraph-image.jpg`)는 이번 범위가 아니다. 바꾸면 결과 보고에 적는다.

## 5. 검증 (완료 전에 모두)

1. `cd landing-web && npm run lint && npm run typecheck && npm run test && npm run build`
2. `npx next start -p 3100` 후 데스크톱 1280px, 모바일 390px에서 확인한다.
   - 히어로 앱 화면 / 체험 전체 흐름(사인 → 미리보기 → 전시의 내 카드 사인) / 사용법 5장 모두 실제 캡처 / 가로 스크롤 없음
   - 이전 세션에서 쓴 방법: `public/__drive.html`에 같은 출처 iframe 드라이버를 임시로 두고, 키보드 이벤트(`+`, 방향키)와 버튼 클릭으로 단계를 진행한 뒤 headless Chrome `--screenshot`으로 캡처
   - 주의 1: `scroll-behavior: smooth` 때문에 `scrollIntoView({behavior:'instant'})`를 써야 한다.
   - 주의 2: headless 가상 시간에서는 CSS 애니메이션이 시작 상태에 멈출 수 있어 `--virtual-time-budget`을 넉넉히(30000) 준다.
   - 주의 3: 모바일 폭은 headless 최소 창 폭(500px) 때문에 iframe 폭을 390px로 지정한다.
   - 사인은 `예시 사인으로 채우기` 버튼으로 진행하고, 실제 손 그리기는 브라우저 도구로 한 번 확인한다.
   - **`__drive.html`은 끝나면 반드시 삭제**
3. `git status`로 변경 범위가 `landing-web/`과 `.github/workflows/landing-web-ci.yml`뿐인지 확인한다.

## 6. 결과 보고 (한국어로, 사용자에게)

- 바뀐 화면 요약(데스크톱·모바일 캡처 경로 포함)과 검증 결과(명령별 통과 여부)
- 캡처에 쓴 앱 커밋 SHA(`origin/client/develop`), 시뮬레이터 기종, 사용한 실행 인자
- 다른 사용자 사진이 들어간 캡처 목록, 건너뛴 화면(예: 기록 달력)과 이유
- 커밋·푸시는 하지 않았다는 점, 다음 단계(이슈 생성 → 브랜치명 `landing-web/feature/#번호-landing-page` → 커밋 `feat: 랜딩페이지 추가 (#번호)` → PR)에 대한 확인 요청
