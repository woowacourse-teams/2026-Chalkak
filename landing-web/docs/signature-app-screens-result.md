# 사인 체험·최신 앱 화면 작업 결과

작업일: 2026-10-13. 커밋·푸시·이슈 생성·배포는 하지 않았다.

## 구현과 변경 범위

- 체험: `topic → shoot → review → sign → signPreview → reveal`. 앱 원본과 같은 정규화 획, 흰색 부드러운 곡선, 되돌리기·전체 지우기, 앱 예시 사인, 투명 PNG(1024×512)를 구현했다. 재촬영해도 사인은 유지한다.
- 미리보기와 내 작품에는 그린 사인 PNG가 나타난다. 사진·사인은 메모리에서만 처리하며 서버 전송·영구 저장·실제 회원가입/작품 등록은 없다. 안내와 FAQ에도 표시했다.
- 히어로: 풍경 프레임 코드를 삭제하고 실제 앱 화면으로 교체했다. 960px 초과 3장, 640~960px 2장, 640px 이하 1장.
- 사용법: 5장 모두 최신 실제 캡처. 파일 존재 여부 분기, mock 컴포넌트/CSS와 사용하지 않는 옛 `display-popular.webp`를 삭제했다.
- `app-screens.ts`에서 크기·캡처 버전을 공유한다. 같은 파일명으로 교체할 때 Next 이미지 캐시가 옛 화면을 재사용하는 문제를 발견해 버전 query를 추가했다. `next.config.ts`는 해당 query만 정확히 허용한다. **다음 캡처 교체 시 `APP_SCREEN_VERSION`도 갱신해야 한다.**
- 변경은 `landing-web/` 안에만 있다. 기존 `.github/workflows/landing-web-ci.yml`와 `.omc/` 계열 미추적 파일은 유지했으며 `client/`, `admin-web/`, `backend/`, 공통 규칙, OG 이미지는 수정하지 않았다.

## 앱 캡처 정보

- 기준: `origin/client/develop`, SHA `00d4741a19237e83dc63d7b8b70f0804fd2578dc`
- 전용 시뮬레이터: iPhone 17 Pro / iOS 26.5 / light / 한국어 / 9:41
- Debug 빌드 후 직접 실행. 원본 1206×2622 → WebP 642×1396, quality 86.

| 파일 (`public/app-screens/`) | 진입 인자·조작 |
| --- | --- |
| `signature.webp` | `-show-onboarding-signature`, XCUITest 포인터 드래그로 획 그리기 |
| `signature-preview.webp` | `-show-onboarding-preview`, 내장 예시 획 |
| `today.webp` | `-stonefive.chalkak.guest-access YES -notificationSetup.hasCompleted YES` |
| `display-feed.webp` | 위 게스트 인자로 실행 후 `전시` 탭 |
| `display-detail.webp` | 전시 피드의 사진 탭 |
| `upload.webp` | `-test-photo-upload-entry` → `추가` → `앨범에서 사진 선택` |

iOS 빌드와 최종 사인/전시/업로드 XCUITest는 통과했다. 초기 clone/accessibility 실패 후 재실행했으며 최종 attachment를 사용했다. 캡처용 worktree의 임시 UI 테스트만 추가했고 앱 fixture는 변경하지 않았다. 실제 업로드·좋아요 변경은 하지 않았다.

- **공개 전 사진 확인 필요:** `today.webp`, `display-feed.webp`, `display-detail.webp`는 개발 서버의 실제 게시물 사진을 포함한다. 육안 검수에서 민감해 보이는 사진은 발견하지 않았지만 공개 사용 승인 확인을 대체하지 않는다.
- `upload.webp`는 실제 시스템 사진 선택기이며 시뮬레이터 기본 Apple 샘플 사진만 표시한다. 별도 미디어는 넣지 않았다.
- 기록 달력은 로그인 계정이 없어 생략했다. 사인 미리보기의 배경은 앱에 내장된 사진이다.
- 전용 UDID `F77BB73A-C798-44B5-BC8D-FC10FE12E57E`와 `/private/tmp/chalkak-landing-capture-1791823831` worktree는 삭제했으며 목록에서 제거됨을 확인했다. 다른 시뮬레이터는 사용하지 않았다.

## 검증

| 검증 | 최종 결과 |
| --- | --- |
| `npm run lint` | 통과, 경고 없음 |
| `npm run typecheck` | 통과 |
| `npm run test` | 5 files / 31 tests 통과 |
| `npm run build` | 통과 |
| 브라우저 전체 흐름 | 1280×900, 960×900, 390×844, 390×600 통과 |
| 화면 검수 | 히어로 3/2/1장, 사용법 실제 5장, 포인터로 직접 사인, 예시 사인, 지우기, 다시 그리기, 재촬영 유지, PNG 크기·표시 확인 |
| 모바일 높이·가로 넘침 | 사인·미리보기 시트 내부 스크롤 없이 버튼 표시, 버튼 44px 이상, 페이지 가로 넘침 없음 |
| 정적 확인 | 체험 코드에 fetch/브라우저 영구 저장 API 없음, 예시 화면·옛 히어로 코드 없음, Pretendard 새 한글 글리프 누락 없음 |

`docs/verification/browser-report.json`에 브라우저 측 검사 결과를 저장했다. 처음 발견한 390×600의 버튼 잘림과 이미지 캐시 문제는 수정 후 재검증했다.

| 화면 | 데스크톱 | 모바일 |
| --- | --- | --- |
| 히어로 | [desktop-hero.png](verification/desktop-hero.png) | [mobile-hero.png](verification/mobile-hero.png) |
| 실제 포인터 사인 | [desktop-signature-drawn.png](verification/desktop-signature-drawn.png) | [mobile-signature-drawn.png](verification/mobile-signature-drawn.png) |
| 미리보기 | [desktop-signature-preview.png](verification/desktop-signature-preview.png) | [mobile-signature-preview.png](verification/mobile-signature-preview.png) |
| 내 작품 사인 | [desktop-reveal.png](verification/desktop-reveal.png) | [mobile-reveal.png](verification/mobile-reveal.png) |
| 사용법 | [desktop-guide.png](verification/desktop-guide.png) | [mobile-guide.png](verification/mobile-guide.png) |

### 브라우저 검증 재현

별도 터미널에서 production 서버와 전용 headless Chrome을 실행한 뒤 검증한다. Chrome 경로는 macOS 기준이다. 검증 후 이 전용 프로세스와 임시 프로필을 정리한다.

```bash
# landing-web에서, 빌드 후
npm run start -- -p 3100
# 다른 터미널
'/Applications/Google Chrome.app/Contents/MacOS/Google Chrome' \
  --headless=new --disable-gpu --no-first-run --no-default-browser-check \
  --remote-debugging-port=9317 --user-data-dir=/private/tmp/chalkak-landing-qa-chrome about:blank
# 다른 터미널, landing-web에서
node scripts/verify-signature.mjs docs/verification
```

CDP로 실제 포인터 이벤트와 버튼 클릭을 사용한다. 추가 의존성이나 임시 `public/__drive.html`은 필요하지 않다.

## 정책 연결 상태와 다음 단계

공통 정책 설치 확인 명령 `manage.py status --project . --check`는 `자동 진입 지침이 수정되었습니다: AGENTS.md`로 실패했다. 이를 우회하거나 진입 지침/읽기 전용 배포본을 덮어쓰지 않았다. **최신 정책 동기화 성공으로 표시하지 않는다.** 저장소의 `docs/business-rules/rules/sign.md` SIGN-001~005를 읽기 전용으로 대조했고 실제 서비스 정책/API는 바꾸지 않았다.

공개 사진 사용 여부와 다음 등록 단계에 대한 확인이 필요하다:

1. 이슈 생성
2. 브랜치 `landing-web/feature/#번호-landing-page`
3. 커밋 `feat: 랜딩페이지 추가 (#번호)`
4. PR 등록

이 단계들은 이번 작업에서 실행하지 않았다.
