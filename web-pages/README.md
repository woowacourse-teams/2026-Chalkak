# 찰캌 공개 웹페이지

안내·설문·캠페인처럼 로그인 없이 읽는 페이지를 모으는 독립 정적 웹 프로젝트입니다. 배포 브랜치는 `web/pages`이고 Vercel Root Directory는 `web-pages`입니다. 앱·백엔드·관리자·기존 `landing-web`과 별도로 빌드합니다.

관련 작업: [이슈 #761](https://github.com/woowacourse-teams/2026-Chalkak/issues/761)

- [공개 페이지 목록](https://2026-chalkak-pages.vercel.app/)
- [AI 피드백 사용 후기 페이지](https://2026-chalkak-pages.vercel.app/ai-feedback/)
- [Vercel 프로젝트](https://vercel.com/chalkak/2026-chalkak-pages)

## 실행과 검증

Node.js 22 이상이 필요합니다. CI와 Vercel은 Node.js 24를 사용합니다. 런타임·개발 외부 패키지 의존성은 없습니다.

```sh
cd web-pages
npm ci
npm test
npm run dev
```

`http://localhost:4173/`에서 목록, `http://localhost:4173/ai-feedback/`에서 첫 페이지를 확인합니다. 파일을 수정한 뒤에는 다시 `npm run build`를 실행합니다.

```sh
npm run build
npm run preview
```

`dist/`는 빌드 결과이므로 직접 고치거나 Git에 커밋하지 않습니다. 빌드에서 내부 링크·프래그먼트·에셋과 설문 설정을 검사합니다. 검증 실패 시 배포도 실패합니다.

## 디렉터리

```text
web-pages/
├── config/
│   ├── pages.json              # 목록에 노출할 공개 페이지와 메타데이터
│   └── surveys.json            # 설문별 Google Forms 응답 주소, 준비 중이면 null
├── public/
│   ├── ai-feedback/            # /ai-feedback/
│   │   ├── index.html
│   │   ├── page.css
│   │   └── assets/             # 이 페이지에서만 쓰는 이미지
│   └── shared/
│       ├── assets/             # 공통 찰캌 로고
│       ├── styles/             # 색상·타이포·상단 바·버튼·목록 스타일
│       └── scripts/            # 공통 설문 이동·준비 중 안내
├── scripts/                    # 정적 빌드·내부 링크 검증·로컬 서버
├── tests/                      # 페이지 추가와 설문 설정 등 배포 계약 검증
└── vercel.json
```

공개 경로는 `/`(목록), `/<slug>/`(각 페이지)입니다. 공통 자원만 `shared`에서 공유하고, 각 페이지의 스타일·이미지는 그 페이지 안에 둡니다. 모든 페이지를 하나의 거대한 HTML이나 전역 CSS로 합치지 않습니다.

## 새 페이지 추가

1. `public/new-story/index.html`을 만들고, 전용 CSS·이미지도 같은 폴더에 둡니다.
2. `config/pages.json`의 `pages` 배열에 아래 항목을 추가합니다.
3. `npm test`와 `npm run build`를 실행합니다. 목록과 사이트맵은 자동 생성됩니다.
4. `web/pages`에 푸시하면 같은 Vercel 프로젝트의 `/new-story/`로 배포됩니다.

```json
{
  "slug": "new-story",
  "title": "새 소식 제목",
  "description": "페이지를 소개하는 짧은 설명",
  "category": "소식",
  "thumbnail": "/new-story/assets/cover.webp"
}
```

슬러그는 영문 소문자·숫자·하이픈만 사용합니다. `shared`, `assets`, `api`는 공통 리소스용 예약어입니다. 이미지 경로는 해당 페이지의 `assets/` 안에 둡니다.

공통 셸은 다음 스타일을 불러오고, 그 다음 전용 스타일을 불러옵니다.

```html
<link rel="stylesheet" href="/shared/styles/tokens.css" />
<link rel="stylesheet" href="/shared/styles/base.css" />
<link rel="stylesheet" href="/new-story/page.css" />
```

## 구글폼 연결

현재 구글폼은 아직 없어서 `ai-feedback` 값이 `null`입니다. 이 상태에서는 상단·하단 설문 링크가 준비 중 안내를 띄우며 응답을 수집하지 않습니다. 임의의 구글폼이나 분석 API를 호출하지 않습니다.

사용자가 설문을 만든 뒤 공개 **응답용** URL 한 곳만 바꾸면 됩니다.

```json
{
  "ai-feedback": "https://forms.gle/실제공유링크"
}
```

위 예시 문구는 실제 링크가 아니므로 그대로 커밋하지 않습니다. `forms.gle/<id>` 또는 `docs.google.com/forms/d/.../viewform` 형식의 HTTPS 응답 주소를 사용합니다. 편집 링크, 다른 사이트, 사용자 정보가 포함된 URL은 빌드에서 거절됩니다.

HTML은 아래 템플릿을 사용합니다. 설정이 있으면 빌드 결과에 실제 주소가 들어가므로 JavaScript 없이도 링크로 이동합니다. JavaScript가 있으면 새 탭으로 같은 설문을 열고, 미설정 상태에서는 공통 준비 중 대화상자를 엽니다.

```html
<a data-survey-link href="{{survey:ai-feedback}}" aria-haspopup="dialog">
  설문하러 가기
</a>
<span>{{survey-status:ai-feedback}}</span>
<script type="module" src="/shared/scripts/survey-links.js"></script>
```

다른 설문을 쓰는 페이지는 `surveys.json`에 새 ID를 등록하고 같은 방식으로 사용합니다. 미설정 안내를 사용하려면 `ai-feedback/index.html`의 `data-survey-dialog`, `data-survey-close` 마크업도 함께 둡니다.

## Vercel 배포

| 설정              | 값                               |
| ----------------- | -------------------------------- |
| Vercel 프로젝트   | `chalkak / 2026-chalkak-pages`   |
| Git 저장소        | `woowacourse-teams/2026-Chalkak` |
| Production 브랜치 | `web/pages`                      |
| Root Directory    | `web-pages`                      |
| Framework         | Other (`null`)                   |
| Node.js           | 24.x                             |
| Install Command   | `npm ci`                         |
| Build Command     | `npm run build`                  |
| Output Directory  | `dist`                           |

프로젝트는 웹 공개 페이지 전용으로 연결하며 기존 관리자·랜딩·베타 프로젝트의 설정은 바꾸지 않습니다. Production 도메인은 공개 페이지용이고, Preview의 기존 Vercel 접근 정책은 유지합니다. `web/**` 브랜치만 이 프로젝트의 자동 배포 대상으로 설정했습니다.

`web/pages`는 여러 페이지가 공유하는 배포 브랜치이므로 첫 페이지 작업 후 삭제하지 않습니다. 이후 작업은 이 브랜치에서 분기해 `web/pages` 대상으로 PR을 올리고 병합하는 방식으로 운영할 수 있습니다. `main`으로의 통합 여부와 별개로 현재 공개 배포는 이 브랜치를 추적합니다.

배포 환경의 `VERCEL_PROJECT_PRODUCTION_URL`을 canonical·OG·사이트맵 주소로 사용합니다. 커스텀 도메인이 생기면 공개 빌드 설정 `PUBLIC_SITE_URL=https://도메인`을 지정합니다. 로컬 빌드는 `http://localhost:4173`을 사용합니다. 개인 Vercel 인증, 관리자·백엔드 비밀값은 필요하지 않습니다.

루트 `.vercelignore`에는 새 프로젝트를 업로드할 수 있는 예외만 추가했습니다. 기존 관리자·클라이언트 예외는 유지합니다.

전용 CI는 `web-pages/**`, 루트 `.vercelignore`와 해당 workflow 변경에만 테스트·빌드를 실행합니다. 관련 없는 앱·백엔드 테스트를 이번 프로젝트에서 실행하지 않습니다.

## AI 피드백 페이지의 내용과 에셋

- 페이지 흐름: 선택한 썸네일과 설문 바로가기 → 평가 방식 소개 → 구도 평가 → 피드백 예시 → 사용 후기. 주제 해석의 독창성은 소개 문장에서 간단히 안내하며 별도 섹션은 두지 않습니다.
- 설문 바로가기는 썸네일 사진 안쪽 오른쪽 아래에 작은 버튼으로 배치합니다. 상단 고정 바는 없으며, 썸네일과 하단에서 같은 설문으로 이동합니다. 작은 장식 문구와 페이지 푸터는 사용하지 않습니다.
- 사용자가 AI에게 제공한다고 확인한 자료는 아래 네 가지입니다. 본문에 구도와 주제 해석·독창성·시선을 설명하고, 원문은 펼침 영역에 모았습니다.
  - [Nikon, 5 Easy Composition Guidelines](https://www.nikonusa.com/learn-and-explore/c/tips-and-techniques/5-easy-composition-guidelines): 삼분할·수평선·방향의 여백·유도선·패턴과 질감.
  - [PPA, 12 Elements of a Merit Image](https://www.ppa.com/ppmag/articles/12-elements-of-a-merit-image): 구도·창의성·스타일·빛과 색·주제·스토리텔링 등 12요소.
  - [PPA, A Fresh Look at the 12 Elements](https://www.ppa.com/ppmag/articles/a-fresh-look-at-the-twelve-elements): 요소들의 관계와 조화, 창의적인 표현과 의도가 사진의 인상을 만든다는 해석.
  - [Canon, Tips for Better In-Camera Composition](https://www.usa.canon.com/learning/training-articles/training-articles-list/tips-for-better-in-camera-composition): 삼분할·여백·균형·유도선·색과 대비·프레이밍.
- 본문의 항목은 네 자료를 사용자가 설명한 찰캌의 피드백 범위에 맞게 정리한 안내입니다. 원문의 전체 평가 항목·대회 점수 체계를 찰캌의 점수·가중치로 옮기거나, 해당 기관이 AI의 정확도를 보증한다고 표시하지 않습니다.
- 표지는 이 작업에서 생성하고 사용자가 선택한 이미지입니다. WebP로 최적화했습니다.
- 예시 사진과 로고는 사용자가 제공한 앱 화면에서 추출했습니다. 페이지의 예시 피드백은 실시간 AI 분석 결과가 아닙니다.
- 실제 AI API, 회원·게시물 정책, 설문 응답 저장, 계정 기능은 이 프로젝트에서 변경하지 않습니다.

## 최초 배포 확인 · 2026-10-22

- `web/pages`의 초기 구현 커밋 `fd4fc26a`를 GitHub에 푸시했고, 전용 CI가 통과했습니다.
- Vercel 프로젝트의 Root Directory `web-pages`, Production 브랜치 `web/pages`, Node.js `24.x`, 정적 출력 `dist`를 확인했습니다.
- 공개 목록·AI 피드백 페이지·썸네일·예시 사진·공통 스크립트가 비로그인 HTTP 200으로 응답했습니다. canonical도 실제 Vercel 주소를 사용합니다.
- 로컬 테스트 8개와 빌드가 통과했고, 1280px·390px·320px 화면, 상단 고정 바와 설문 준비 중 안내를 확인했습니다.
- 구글폼 생성·실제 응답 수집은 아직 하지 않았습니다.
