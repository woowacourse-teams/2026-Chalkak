# Chalkak Landing Web

서비스 주소로 처음 들어온 사람이 찰캌이 어떤 서비스인지 알고, 직접 한 장을 찍어 본 뒤 앱 설치로 이어지게 하는 랜딩페이지입니다. 모바일 앱만 있는 서비스라 로그인 없이 모든 내용을 공개합니다. 찰캌 백엔드 API는 호출하지 않으며, 체험 작품 공유만 이 프로젝트의 서버리스 함수(`/api/shots`)와 전용 Neon Postgres를 씁니다.

Next.js App Router와 strict TypeScript를 사용하며, Vercel에서는 이 디렉터리(`landing-web`)를 Root Directory로 지정합니다.

## 로컬 실행

    npm ci
    npm run dev

브라우저에서 http://localhost:3000 을 엽니다.

## 검증

    npm run lint
    npm run typecheck
    npm run test
    npm run build

## 페이지 구성

| 섹션 | 내용 | 제출 요구사항 |
| --- | --- | --- |
| 히어로 | 서비스 이름과 한 줄 설명, 최신 앱 화면 3장(휴대폰에서는 1장), 스토어 배지 | 서비스 소개 |
| 체험하기 | 주제 `온기`와 풍경을 받아 9:16 가상 카메라로 한 장을 찍고 제목을 붙이고 사인을 그린 뒤, 다른 방문자들의 시선 지도와 전시를 본다 | 서비스 소개 |
| 사용법 | 사인 → 오늘의 주제 → 올리기 → 전시 감상 → 좋아요·기록, 앱 화면 5장 | 사용법 |
| 자주 묻는 질문 | 공통 비즈니스 규칙(POST-002·003·007, SIGN-003·005, LIKE-001)과 맞춘 답변 | — |
| 시작하기 | App Store·Google Play 배지, 데스크톱용 QR | 시작하기 |

- `/download`: QR이 가리키는 주소입니다. iOS·Android는 각 스토어로 이동하고, 그 밖의 기기에는 두 스토어를 보여줍니다.
- 체험 결과: 내 작품(선택 입력한 제목 포함)과 `scene.ts`의 예시 작품 13장을 함께 보여 줍니다. 찍은 결과와 제목, 사인은 브라우저 메모리에서만 처리하며 서버로 보내거나 저장하지 않습니다. 실제 회원가입이나 작품·사인 등록이 아닙니다.
- 방문자 작품 공유(현재 꺼짐): `src/features/experience/shared-shot.ts`의 `SHARING_ENABLED`를 `true`로 바꾸면 아래처럼 동작합니다. 꺼져 있으면 `/api/shots`는 404를 돌려주고, 체험 안내·FAQ 문구도 이 값을 따라 바뀝니다.
  - 결과 화면에 들어오면 풍경 대비 9:16 프레임 좌표와 제목(10자)만 `/api/shots`로 보내 익명으로 저장하고, 예시 대신 다른 방문자의 최근 작품 40개를 받아 보여 줍니다. 그린 사인과 IP는 저장하지 않습니다.
  - 서버는 좌표가 체험 카메라로 찍을 수 있는 범위인지, 제목이 길이·금지어 기준(`shared-shot.ts`)에 맞는지 다시 검사합니다. 같은 방문자는 1분에 5장, 전체는 1분에 60장까지 받고 최근 1000장만 남깁니다.
  - 아직 다른 방문자가 없으면 첫 번째 시선이라고 안내합니다. 좋아요는 저장하지 않고 내 화면에서만 반영합니다.
  - 저장소는 Vercel의 Neon 연동이 넣어 주는 `DATABASE_URL`을 쓰며, 테이블(`landing_shots`)은 첫 요청 때 만듭니다. 로컬에서 주소가 없으면 메모리에 두고, 배포 환경에서 없으면 내 작품만 보여 줍니다.
  - 부적절한 제목을 지우려면 Neon 콘솔에서 `DELETE FROM landing_shots WHERE id = …;`를 실행합니다.

## 배포

- Vercel 프로젝트의 Root Directory를 `landing-web`으로 지정합니다.
- 누구나 서비스 주소로 볼 수 있어야 하므로 Production 도메인에는 **Vercel Authentication(Deployment Protection)을 적용하지 않습니다.**
- 공개 주소는 빌드 시 Vercel의 `VERCEL_PROJECT_PRODUCTION_URL`로 정합니다. 커스텀 도메인을 쓰면 `NEXT_PUBLIC_SITE_URL`에 `https://` 주소를 넣고 다시 빌드합니다. OG 이미지, canonical, sitemap과 시작하기의 QR이 이 주소를 씁니다.
- 손글씨 폰트(Nanum Pen Script)는 빌드 때 Google Fonts에서 받아 함께 배포하므로, 빌드 환경에서 외부 네트워크가 필요합니다.

## 에셋

| 경로 | 출처 |
| --- | --- |
| `public/scene/onggi-*.webp` | AI로 만든 수채화 풍경(1774×887)을 Real-ESRGAN `realesrgan-x4plus`로 4배 키운 뒤 4x·2x·1600px WebP로 저장. 데스크톱은 4x, 휴대폰은 2x를 쓴다 |
| `public/app-screens/*.webp` | 최신 iOS 앱을 전용 시뮬레이터에서 직접 실행한 캡처 |
| `public/badges/*` | Apple·Google이 배포하는 공식 한국어 스토어 배지 |
| `src/app/fonts/Pretendard-*.woff2` | 앱 번들의 Pretendard(OFL)를 `scripts/subset-fonts.py`로 줄인 파일. 문구에 새 글자가 들어가면 다시 실행한다 |
| `src/app/icon.png`, `apple-icon.png` | iOS 앱 아이콘 |

### 앱 화면 캡처

사용법 5단계와 히어로는 실제 앱 화면을 사용합니다. 파일 존재 여부에 따른 예시 화면 분기는 없습니다. 캡처를 바꾸면 `src/features/landing/app-screens.ts`의 `APP_SCREEN_VERSION`도 갱신합니다.

- 크기: 원본 → WebP 642×1396, quality 86
- 2026-10-15 교체분: `origin/client/develop` `e39d3294`, iPhone 17 Pro / iOS 26.5 Debug 빌드, 상태 표시줄 9:41 고정. 캡처 전용 실행 인자는 임시 워크트리에만 두고 앱 코드에는 반영하지 않았습니다.

| 파일 | 화면 | 출처 |
| --- | --- | --- |
| `signature.webp` | 사인 입력 | 실제 기기 스크린샷(상태 표시줄만 9:41로 교체) |
| `today.webp` | 홈 · 10월 12일 주제 '아침' | 주제·랭킹은 개발 서버 실데이터, 내 사진(아침 커피)과 이번 주 기록은 캡처용 데이터 |
| `upload.webp` | 사진 올리기에서 연 시스템 사진 선택기 | 2026-10-13 캡처(`00d4741a`), 시뮬레이터 기본 샘플 사진 |
| `display-feed.webp` | 전시 · 10월 12일 '아침' 인기순 | 개발 서버 실데이터 |
| `record.webp` | 기록 달력 · 2026년 10월 | 캡처용 데이터, 썸네일은 Pexels 사진(Pexels 라이선스) |
| `display-detail.webp` | 작품 상세·좋아요 (히어로·앱에서는 섹션) | 2026-10-13 캡처(`00d4741a`) |
| `signature-preview.webp` | 앱 내장 예시 사인의 미리보기 (현재 미사용) | 2026-10-13 캡처 |

**공개 배포 전 확인:** `today.webp`(어제 랭킹), `display-feed.webp`, `display-detail.webp`에는 개발 서버의 실제 게시물 사진이 포함되어 있습니다. 공개 사용 여부를 확인해야 합니다. 실제 업로드·좋아요 변경은 하지 않았습니다.

세부 실행 인자와 검증 결과는 [작업 결과](docs/signature-app-screens-result.md)를 참고하세요.

### 풍경을 바꿀 때

예시 작품은 `src/features/experience/scene.ts`의 `onggiShots`에 풍경 대비 비율 좌표(중심 `cx`, `cy`와 높이 `h`)로 정의합니다. 너비는 9:16으로 계산합니다. 풍경을 바꾸면 예시 프레임 좌표도 다시 잡고, 공유를 켠 적이 있다면 `landing_shots`의 기존 행도 비웁니다.
