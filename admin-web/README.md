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

관리자 아이디·비밀번호는 로그인 화면에서 입력합니다. 웹 환경변수에 관리자 계정이나 토큰을 설정하지 않습니다. 실제 환경에서는 Next.js 서버가 백엔드 로그인 API의 JWT를 받아 HttpOnly 쿠키로 보관하고, 이후 요청에만 Bearer 토큰으로 전달합니다. JWT는 브라우저 JavaScript, React 상태, localStorage 또는 sessionStorage에 전달하지 않습니다.

### 로그인과 화면 흐름

- `/login`: 단일 관리자 로그인. 로그인 후 원래 요청한 내부 화면 또는 검수 대기 목록으로 이동합니다.
- `/posts`: 검수 대기 / 승인 / 거절 탭 → 게시물 상세 → 승인·거절 → 기존 필터 목록으로 복귀합니다.
- `/users`: 활동 / 차단 / 탈퇴 상태별 조회와 사용자 상세. 게시물 건수를 누르면 해당 사용자·상태의 게시물 목록으로 이동합니다.
- `/topics`: 한글 주제 목록·등록·수정·삭제와 주제별 게시물 조회입니다.
- `/audit-logs`: 백엔드 처리 이력을 행동·대상별로 조회합니다. 데이터는 조회만 할 수 있습니다.

검증 중(`VALIDATING`) 게시물은 관리자 화면·집계에 포함하지 않습니다. 알림 기능은 이번 범위에 포함하지 않습니다. 모바일에서는 하단 메뉴를 사용하며, 검수 대기 게시물의 상세 화면에서는 메뉴 대신 거절·승인 버튼만 하단에 고정합니다.

세션 복원은 `/auth/me`로 확인합니다. 만료/401, 로그아웃 또는 계정 변경 시 이전 관리 데이터 캐시를 비웁니다. 로그아웃은 브라우저 쿠키를 삭제하지만, 현재 백엔드의 JWT 자체를 즉시 폐기하는 기능은 아닙니다. 이미 발급된 토큰의 서버 측 유효기간은 백엔드 정책을 따릅니다.

## API 모드

- `NEXT_PUBLIC_API_MODE=mock`: 로컬 MSW 데모입니다. 로그인 화면의 **데모 계정으로 둘러보기** 버튼으로 들어갑니다. 실제 계정·데이터를 사용하지 않으며, 새로고침하면 메모리 기반 데모 상태가 초기화됩니다.
- `NEXT_PUBLIC_API_MODE=real`: 브라우저 → 같은 출처의 `/api/admin/*` → `NEXT_PUBLIC_ADMIN_API_BASE_URL`의 실제 백엔드 순서로 호출합니다. 공개 주소 변수에 비밀값을 넣지 않습니다.

모드 전환은 위 환경변수 한 곳에서만 수행합니다. API 주소가 없거나 잘못되면 요청 전에 명확한 설정 오류를 표시하며, production build는 Mock 모드를 거부합니다. Mock에는 정상·빈 목록·지연·400·403·404 시나리오가 준비되어 있습니다.

공통 API Client는 JSON과 `{errorCode, message}` 오류 계약, 10초 timeout을 처리합니다. Next.js 중계는 허용된 관리자 API만 연결하며 8초 timeout, 동일 출처 검사, 요청 크기 제한을 적용합니다. 쿠키는 HttpOnly·SameSite=Lax이고 운영 빌드에서는 Secure 속성을 사용합니다. 실제 API 주소는 운영 빌드에서 HTTPS만 허용합니다. 응답의 UTC Instant는 보존하고 화면에서 사용자 시간대로 표시합니다.

중계 오류는 응답 계약 불일치(`ADMIN_INVALID_RESPONSE`), 백엔드 오류(`ADMIN_UPSTREAM_ERROR`), 연결 실패(`ADMIN_API_UNAVAILABLE`), 시간 초과(`ADMIN_API_TIMEOUT`)로 구분합니다. 진단을 위해 비밀번호·토큰·백엔드 응답 원문을 로그나 화면에 노출하지 않습니다.

## Vercel Preview 배포

실제 웹 로그인과 운영 연결 검증·승인 전에는 **Preview만** 사용합니다. `next.config.ts`의 `VERCEL_ENV=production` 빌드 차단을 유지합니다. Production 공개는 별도 승인 후 보호 장치를 제거하고 진행합니다.

### 최초 프로젝트 연결

1. Vercel에서 이 GitHub 저장소를 Import합니다.
2. 프로젝트 이름은 `chalkak-admin-web`, Framework Preset은 Next.js로 설정합니다.
3. Root Directory를 `admin-web`으로 설정합니다.
4. Production 환경변수는 비워 둡니다. Production 배포·운영 도메인·운영 API는 연결하지 않습니다.
5. Preview 환경에 아래 공개 변수만 설정합니다.

       NEXT_PUBLIC_API_MODE=real
       NEXT_PUBLIC_ADMIN_API_BASE_URL=https://chalkak-dev.pysun.kr/api/v1/admin
       NEXT_PUBLIC_APP_ENV=preview

6. Settings → Deployment Protection에서 Vercel Authentication과 Standard Protection을 켭니다.
7. Git 브랜치 추적과 빌드 건너뛰기 설정은 Vercel 프로젝트 설정에서 확인합니다. 백엔드 PR에 Vercel 배포 검사를 추가하지 않도록 기존 팀 설정을 유지합니다.

이 저장소는 `admin-web`과 `be/develop`을 분리해 사용합니다. 관리자 웹 PR의 대상은 `admin-web`입니다. 백엔드 브랜치에 Vercel 설정 파일을 추가하지 않습니다. 이 PR은 기존 Vercel 프로젝트 설정이나 자동 배포 설정을 변경하지 않습니다. 푸시만으로 배포된다고 가정하지 말고 실제 배포 커밋을 확인합니다.

Production이 `admin-web` 브랜치를 추적하는 설정에서는 PR 병합 시 Production 빌드가 시도됩니다. 위 빌드 차단은 공개를 막지만 빌드 시도 자체를 막지는 않습니다. 병합과 정식 배포를 분리하려면 팀 승인하에 Vercel 프로젝트 설정에서 배포 보류 정책을 먼저 정합니다. 검증용 Preview의 실패/성공과 Production 공개 여부를 혼동하지 않습니다.

`NEXT_PUBLIC_*` 값은 모두 브라우저에 공개됩니다. 비밀번호, API 토큰, AWS 자격증명, Webhook, FCM 등록값을 Vercel 공개 변수나 저장소에 넣지 않습니다. 개발 API 주소는 HTTPS여야 하며 개발 DB·개발 S3만 사용해야 합니다.

### 백엔드 연결

브라우저는 같은 출처의 Next.js API만 호출하므로 이 경로에 브라우저→백엔드 CORS 예외를 추가할 필요가 없습니다. 기존 백엔드의 `ADMIN_CORS_ALLOWED_ORIGIN` 설정은 변경하지 않습니다. Next.js 서버에서 개발 API에 접근할 수 있어야 하며, 이미 배포된 백엔드의 관리자 계정과 인증·인가를 그대로 사용합니다. 웹 연결을 위해 EC2에 새 환경변수나 관리자 비밀번호를 추가하지 않습니다.

### 배포와 검증

프로젝트의 기존 수동 Preview 배포 절차를 사용하고, PR 최신 커밋이 배포됐는지 확인합니다. CLI로 확인할 때는 프로젝트를 연결한 `admin-web` 디렉터리에서 실행합니다.

    vercel inspect <preview-url> --logs
    vercel curl / --deployment <preview-url>

Preview에서 실제 계정 로그인 → 새로고침 세션 유지 → 목록·상세 조회 → 로그아웃 → 보호 경로 차단을 먼저 확인합니다. 승인·거절, 차단·해제, 주제 변경은 허가받은 개발 데이터로만 검증합니다. 데스크톱과 320px·390px에서 목록, 상세, 확인창, 오류 상태가 가로로 넘치지 않는지도 확인합니다. 로컬 데모 통과만으로 실제 API 연결 검증을 완료했다고 간주하지 않습니다.

### 재배포와 되돌리기

같은 소스를 다시 빌드하려면 Vercel Deployments에서 Redeploy를 선택하거나 다음 명령을 사용합니다.

    vercel redeploy <preview-url>

Preview에서 문제가 생기면 직전 정상 커밋으로 새 Preview를 만들거나 해당 정상 배포 URL을 다시 사용합니다. `vercel rollback`은 Production용 명령이므로 Preview 운영에는 사용하지 않습니다. Production 승격은 실제 웹 로그인과 운영 연결 검증·승인 후 별도 배포 절차에서 수행합니다.
