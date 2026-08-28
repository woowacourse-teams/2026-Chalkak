# Chalkak Admin Web

모바일 클라이언트와 백엔드에서 독립적으로 실행·배포하는 Chalkak 관리자 웹입니다. Next.js App Router와 strict TypeScript를 사용하며, Vercel에서는 이 디렉터리(admin-web)를 Root Directory로 지정합니다.

## 요구 사항

- Node.js 20.9 이상
- npm

## 로컬 실행

    npm ci
    cp .env.example .env.local
    npm run dev

브라우저에서 http://localhost:3000 을 엽니다.

## 검증

    npm run lint
    npm run typecheck
    npm run test
    npm run build

테스트를 파일 변경과 함께 반복 실행하려면 npm run test:watch 를 사용합니다.

## 디렉터리

    src/
    ├── app/       # App Router 진입점과 라우트
    ├── features/  # 도메인별 화면과 비즈니스 기능
    ├── shared/    # 여러 기능이 함께 쓰는 UI·API·유틸리티
    ├── mocks/     # MSW 개발·테스트 Mock
    └── test/      # 공통 테스트 설정

## 환경변수와 비밀값

.env.example을 .env.local로 복사해 로컬 값을 설정합니다. NEXT_PUBLIC_ 접두사가 붙은 값은 빌드 시 브라우저 번들에 포함되므로 공개 가능한 API 주소와 모드 값만 사용합니다. 토큰, 비밀번호, 개인 키 같은 비밀값은 NEXT_PUBLIC_ 변수나 저장소에 넣지 않습니다.

관리자 인증·인가는 후속 작업에서 연결합니다. 현재 프로젝트는 API 계약과 개발 화면 검증을 막지 않도록 인증 경계를 별도 계층으로 둘 예정이며, 브라우저가 임의의 관리자 식별자를 만들거나 저장하지 않습니다.

## API 모드

- NEXT_PUBLIC_API_MODE=mock: 로컬 개발에서 MSW 응답을 사용합니다.
- NEXT_PUBLIC_API_MODE=real: NEXT_PUBLIC_ADMIN_API_BASE_URL의 실제 API를 호출합니다.

모드 전환은 위 환경변수 한 곳에서만 수행합니다. API 주소가 없거나 잘못되면 요청 전에 명확한 설정 오류를 표시하며, production build는 Mock 모드를 거부합니다. Mock에는 정상·빈 목록·지연·400·403·404 시나리오가 준비되어 있습니다.

공통 API Client는 JSON과 {errorCode, message} 오류 계약, 10초 timeout을 처리합니다. 응답의 UTC Instant 문자열은 API 계층에서 변환하지 않고 보존하며 실제 화면에서만 사용자 시간대로 표시합니다.

## Vercel Preview 배포

실제 관리자 인증·인가가 완성되기 전에는 **Preview만** 사용합니다. `next.config.ts`가 `VERCEL_ENV=production` 빌드를 실패시켜 실수로 Production을 공개하지 못하게 합니다. 인증·인가 이슈가 끝난 뒤 이 보호 장치를 별도 PR에서 제거합니다.

### 최초 프로젝트 연결

1. Vercel에서 이 GitHub 저장소를 Import합니다.
2. 프로젝트 이름은 `chalkak-admin-web`, Framework Preset은 Next.js로 설정합니다.
3. Root Directory를 `admin-web`으로 설정합니다.
4. Production 환경변수는 비워 둡니다. Production 배포·운영 도메인·운영 API는 연결하지 않습니다.
5. Preview 환경에 아래 공개 변수만 설정합니다.

       NEXT_PUBLIC_API_MODE=real
       NEXT_PUBLIC_ADMIN_API_BASE_URL=https://REPLACE_WITH_DEV_API_HOST/api/v1/admin
       NEXT_PUBLIC_APP_ENV=preview

6. Settings → Deployment Protection에서 Vercel Authentication과 Standard Protection을 켭니다.
7. GitHub Integration을 연결한 뒤 Preview PR에 배포 링크와 `Vercel` 상태 검사가 생성되는지 확인합니다.

`NEXT_PUBLIC_*` 값은 모두 브라우저에 공개됩니다. 비밀번호, API 토큰, AWS 자격증명, Webhook, FCM 등록값을 Vercel 공개 변수나 저장소에 넣지 않습니다. 개발 API 주소는 HTTPS여야 하며 개발 DB·개발 S3만 사용해야 합니다.

### CORS 연결

Vercel이 제공하는 브랜치별 고정 Preview URL을 확인한 뒤 그 Origin 전체(예: `https://프로젝트-git-브랜치-팀.vercel.app`)를 개발 EC2의 `ADMIN_CORS_ALLOWED_ORIGINS`에 설정합니다. 임의 배포 URL마다 값이 바뀌지 않도록 커밋 URL이 아닌 브랜치 URL을 사용합니다.

- `*.vercel.app` 같은 와일드카드는 허용하지 않습니다.
- Production 호스트나 운영 API 주소를 추가하지 않습니다.
- 변경 후 개발 백엔드를 재시작하고 브라우저의 CORS preflight와 실제 요청을 확인합니다.

### 배포와 검증

Preview 브랜치에 푸시하면 Vercel이 새 배포를 생성합니다. CLI로 확인할 때는 프로젝트를 연결한 `admin-web` 디렉터리에서 실행합니다.

    vercel inspect <preview-url> --logs
    vercel curl / --deployment <preview-url>

Preview에서 게시물 승인·거절, 사용자 검색·차단·해제, 한글 주제 등록·수정·삭제를 개발 데이터로 검증합니다. 데스크톱과 320px·390px 화면에서 목록, 상세, 확인 대화상자, 오류 상태가 가로로 넘치지 않는지도 확인합니다.

### 재배포와 되돌리기

같은 소스를 다시 빌드하려면 Vercel Deployments에서 Redeploy를 선택하거나 다음 명령을 사용합니다.

    vercel redeploy <preview-url>

Preview에서 문제가 생기면 직전 정상 커밋으로 새 Preview를 만들거나 해당 정상 배포 URL을 다시 사용합니다. `vercel rollback`은 Production용 명령이므로 인증 전 Preview 운영에는 사용하지 않습니다. Production 승격은 관리자 인증·인가와 운영 API 연결이 완료된 뒤 별도 배포 절차에서 수행합니다.
