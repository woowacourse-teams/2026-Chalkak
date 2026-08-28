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
