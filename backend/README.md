# 찰캌 백엔드

## 목차

- [개발환경 표준](#개발환경-표준)
- [사전 설치](#사전-설치)
- [최초 실행](#최초-실행)
- [IntelliJ 설정](#intellij-설정)
- [AI 하네스](#ai-하네스)
- [자주 사용하는 명령](#자주-사용하는-명령)
- [프로필](#프로필)
- [API 문서](#api-문서)
- [임시 인증](#임시-인증)
- [이미지 저장소](#이미지-저장소)
- [DB 스키마](#db-스키마)
- [테스트](#테스트)
- [배포 환경](#배포-환경)

모노레포의 백엔드 모듈이다. Gradle 루트는 `backend/` 디렉터리이므로 모든 백엔드 명령은 이 디렉터리에서 실행한다.

## 개발환경 표준

| 대상 | 확정 기술 및 버전 |
|---|---|
| JDK 배포판 | <img src="https://cdn.simpleicons.org/eclipseadoptium/FF1464/FFFFFF?viewbox=auto" width="18" height="18" alt="Eclipse Adoptium"> Eclipse Temurin 25 |
| Java | <img src="https://cdn.simpleicons.org/openjdk/437291/FFFFFF?viewbox=auto" width="18" height="18" alt="OpenJDK"> Java 25 |
| 프레임워크 | <img src="https://cdn.simpleicons.org/springboot/6DB33F/FFFFFF?viewbox=auto" width="18" height="18" alt="Spring Boot"> Spring Boot 4.1.0 |
| 빌드 도구 | <img src="https://cdn.simpleicons.org/gradle/02303A/FFFFFF?viewbox=auto" width="18" height="18" alt="Gradle"> Gradle 9.6.1 |
| 데이터베이스 | <img src="https://cdn.simpleicons.org/postgresql/4169E1/FFFFFF?viewbox=auto" width="18" height="18" alt="PostgreSQL"> PostgreSQL 18.4 |
| 스키마 관리 | <img src="https://cdn.simpleicons.org/flyway/CC0200/FFFFFF?viewbox=auto" width="18" height="18" alt="Flyway"> Flyway |

Spring Boot 애플리케이션은 로컬 JVM에서 실행하고, 개발·테스트 PostgreSQL은 Docker Compose로 실행한다.

## 사전 설치

- Git
- Eclipse Temurin JDK 25
- Docker Desktop 또는 Docker Engine 및 Compose Plugin
- IntelliJ IDEA

로컬 Gradle은 설치하지 않고 저장소에 포함된 Gradle Wrapper를 사용한다.

로컬에 JDK 25가 없어도 Gradle Toolchain이 Temurin 25를 자동으로 내려받는다. 다만 IntelliJ에서 애플리케이션을 직접 실행하기 위해 JDK 25 설치를 권장한다.

## 최초 실행

```bash
git clone https://github.com/woowacourse-teams/2026-Chalkak.git
cd 2026-Chalkak
git switch be/develop
cd backend
./scripts/setup-local.sh
```

스크립트는 다음 작업을 수행한다.

1. `.env.example`을 `.env`로 복사
2. Java와 Docker Compose 설치 확인
3. 개발·테스트 PostgreSQL 컨테이너 실행
4. 컨테이너가 `healthy` 상태가 될 때까지 대기
5. Gradle 빌드 및 테스트 실행

수동으로 진행하려면 다음 명령을 실행한다.

```bash
cd backend
cp .env.example .env
java -version
./gradlew --version
docker compose up -d
docker compose ps
./gradlew build
./gradlew bootRun
```

## IntelliJ 설정

모노레포이므로 저장소 루트가 아니라 `backend/build.gradle.kts`를 Gradle 프로젝트로 Import한다.

프로젝트 SDK와 Gradle JVM은 Eclipse Temurin 25를 사용한다.

## AI 하네스

백엔드 작업에서 Claude와 Codex가 팀 컨벤션을 따르도록 AI 하네스를 사용한다.

### 실행 방법

하네스는 `backend/`를 기준으로 구성되어 있으므로 저장소 최상위가 아닌 `backend/`에서 AI를 실행한다.

Codex:

```bash
cd backend
codex
```

Claude:

```bash
cd backend
claude
```

AI 하네스는 Codex CLI 0.138.0 이상과 Claude Code 2.1.228 이상에서 사용한다.

### 운영 방식

하네스는 최소한의 컨벤션으로 시작한다.

1. 코드 리뷰에서 같은 피드백이 반복되는지 관찰한다.
2. 반복되는 피드백을 팀 컨벤션으로 합의한다.
3. 하네스 변경 이슈를 생성한다.
4. Codex와 Claude 규칙에 함께 반영한다.
5. 적용 결과를 관찰해 규칙을 유지·수정·제거한다.

하네스 지침과 컨벤션은 [`AGENTS.md`](AGENTS.md), [`CLAUDE.md`](CLAUDE.md), [`.agents/skills/`](.agents/skills/), [`.claude/rules/`](.claude/rules/), [`.claude/skills/`](.claude/skills/)에서 관리한다. 클라이언트 코드 수정 제한은 [`.codex/config.toml`](.codex/config.toml)과 [`.claude/settings.json`](.claude/settings.json)에서 관리한다.

## 자주 사용하는 명령

모든 명령은 `backend/`에서 실행한다.

| 목적 | 명령 |
|---|---|
| 개발 DB 시작 | `docker compose up -d postgres` |
| 테스트 DB 시작 | `docker compose up -d postgres-test` |
| 전체 DB 시작 | `docker compose up -d` |
| PostgreSQL 종료 | `docker compose down` |
| 개발 DB 접속 | `docker compose exec postgres psql -U chalkak -d chalkak` |
| 애플리케이션 실행 | `./gradlew bootRun` |
| 테스트 | `./gradlew test` |
| 전체 빌드 | `./gradlew clean build` |
| 개발 DB 로그 확인 | `docker compose logs -f postgres` |

Windows에서는 `./gradlew` 대신 `gradlew.bat`을 사용한다.

## 프로필

| 프로필 | 설정 파일 | 활성화 방법 |
|---|---|---|
| `local` | `application-local.yml` | 기본값으로 자동 적용 |
| `test` | `application-test.yml` | `./gradlew test` 실행 시 자동 적용 |
| `dev` | `application-dev.yml` | 개발 EC2에서 `SPRING_PROFILES_ACTIVE=dev`로 활성화 |
| `prod` | `application-prod.yml` | 운영 환경에서 `SPRING_PROFILES_ACTIVE=prod`로 활성화 |

`application.yml`에 다음 설정이 있어 별도 지정 없이 `local` 프로필로 실행된다.

```yaml
spring:
  profiles:
    default: local
```

프로필을 직접 지정하려면 다음 중 하나를 사용한다.

```bash
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
./gradlew bootRun --args='--spring.profiles.active=local'
```

> `prod` 프로필은 배포 환경 전용이므로 로컬에서 실행하지 않는다. 원격 DB에 Flyway 마이그레이션이 적용될 수 있다.  
> `application-prod.yml`에는 DB 접속 정보의 기본값이 없으므로 필수 환경변수가 누락되면 애플리케이션이 기동되지 않는다.

## API 문서

로컬·개발 환경의 Swagger UI는 `http://localhost:8080/swagger-ui.html`에서 확인한다. 첫 화면은 `user-api` 그룹으로 연다.

| 그룹 | 포함 경로 |
|---|---|
| `user-api` | `/api/v1/**` 중 `/api/v1/admin/**` 제외 |
| `admin-api` | `/api/v1/admin/**` |
| `internal-api` | `/internal/v1/**` |

운영 환경에서는 API 문서 JSON과 Swagger UI를 모두 비활성화한다.

게시물 생성 API는 임시 인증을 사용하는 동안 로컬·개발 환경의 `user-api`
그룹에만 표시한다. 생성 전용 `PostCreationApiDocs`를 기존 공개 조회용
`PostApiDocs`와 분리하므로 `prod`에서 생성 API를 제외해도 게시물 목록·상세
조회는 유지된다. 요청·응답과 이미지 키 계약은
[`docs/post-create-api-design.md`](docs/post-create-api-design.md)를 참고한다.

Spring Boot 4 지원과 최신 기능을 위해 `springdoc-openapi` 3.1.0을 유지한다. 다만 이 버전은 Bean Validation 제약이 붙은 숫자 파라미터를 문서화할 때 경고 로그를 출력하는 [알려진 회귀 문제](https://github.com/springdoc/springdoc-openapi/issues/3314)가 있다. 현재 문서 응답과 스키마 생성에는 문제가 없으므로 하위 버전으로 내리지 않고, [수정 PR](https://github.com/springdoc/springdoc-openapi/pull/3315)이 반영된 정식 버전이 나오면 업그레이드한다.

## 인증과 인가

소셜 로그인 또는 회원가입 응답으로 받은 액세스 토큰을 `Authorization` 헤더에 담아 호출한다.

```bash
curl -X PUT localhost:8080/api/v1/users/me/signature \
  -H "Authorization: Bearer <accessToken>" \
  -H 'Content-Type: application/json' \
  -d '{"signatureOriginalUploadId":"<uuid>"}'
```

액세스 토큰은 HS256으로 서명한 JWT이며 `sub`에 회원 식별자를 담는다. 회원가입 토큰과는 서명 키,
`aud`, `purpose` 세 가지로 분리해, 키를 같은 값으로 잘못 설정해도 서로를 대신할 수 없게 한다.
일반 사용자 토큰에는 `USER`, 관리자 로그인 토큰에는 `ADMIN` scope를 서명해 같은 JWT 검증 계약을
재사용하면서 `/api/v1/admin/**`의 권한을 구분한다. 기존에 발급한 scope 없는 회원 토큰도 유지한다.
관리자 토큰의 식별자는 회원 식별자로 사용하지 않으며, 회원 인증 주체가 필요한 API에서는 403으로 거부한다.

| 대상 | 역할 |
|---|---|
| `SecurityConfig` | 필터 체인. 공개 경로를 열고 나머지는 모두 인증을 요구한다 |
| `JwtAccessTokenProvider` | 액세스 토큰 발급과 검증용 `JwtDecoder` 제공 |
| `LoginUserArgumentResolver` | 인증 주체를 `AuthenticatedUser`로 변환. 인증이 없으면 401 |
| `OptionalLoginUserArgumentResolver` | 공개 API에서 인증이 없으면 비로그인으로 처리 |
| `SecurityContextAdminActorResolver` | `ADMIN` scope를 가진 인증 주체를 `AuthenticatedAdmin`으로 변환 |
| `UsableUserPolicy` | 토큰이 가리키는 회원의 상태를 판정. 없거나 탈퇴하면 401, 정지면 403 |
| `UnauthorizedEntryPoint` | 필터 단계의 401을 공통 에러 형식으로 응답 |

공개 경로는 소셜 로그인·회원가입(`/api/v1/auth/**`), 주제와 게시물 조회(`GET`), Lambda 콜백
(`/internal/v1/**`, HMAC으로 자체 인증), 헬스체크다. 이 목록은 `SecurityFilterChainTest`가 고정한다.

Bearer 토큰을 쓰는 stateless API라 CSRF 보호는 끈다. 켜 두면 모든 쓰기 요청이 403이 된다.

관리자는 `POST /api/v1/admin/auth/login`으로 로그인한 뒤 응답의 액세스 토큰을 같은 방식으로
전달한다. 로그인만 공개이며 현재 관리자 조회, 로그아웃과 나머지 관리자 API는 모두 `ADMIN`
scope가 필요하다. 로그아웃 응답을 받으면 브라우저가 Bearer 토큰을 폐기한다.
서버가 발급한 JWT 자체를 즉시 무효화하지는 않으므로 이미 복사된 토큰은 만료까지 유효하다.
MVP에는 refresh token, 토큰 폐기 목록, 서버 세션을 별도로 추가하지 않는다.

local과 test는 `chalkak.admin.authentication.development-bypass-enabled=true`를 명시해 기존
`dev-admin`을 사용할 수 있다. dev와 prod의 기본값은 false이며 실제 관리자 로그인을 거쳐야 한다.
dev/prod를 포함한 local/test 외 환경에서 이 값을 true로 설정하면 서버 시작을 실패시킨다.
dev/prod와 local/test 프로필을 함께 활성화해도 인증 우회를 허용하지 않는다.

규칙:

- 컨트롤러는 헤더를 직접 읽지 않고 회원은 `@LoginUser AuthenticatedUser`, 관리자는
  `@CurrentAdmin AuthenticatedAdmin`을 받는다.
- 로그인 여부에 따라 응답만 개인화하는 공개 API는
  `@OptionalLoginUser Optional<AuthenticatedUser>`를 받는다.
- 인증이 필요한 모든 엔드포인트에 회원 상태 판정을 붙인다. 쓰기는 `@RequiresUsableUser`
  (탈퇴 401 · 정지 403), 조회와 자기 데이터 정리는 `@RequiresExistingUser`(탈퇴 401, 정지는
  통과)를 쓴다. 정지 회원도 탈퇴와 자기 정리는 할 수 있어야 하기 때문이다.
- `GET /api/v1/posts`만 익명 호출이 가능하므로 애노테이션을 붙이지 않고 `PostQueryService`가
  판정한다.
- 토큰이 가리키는 회원이 없거나 탈퇴했으면 404가 아니라 401을 응답한다. 404는 실제로 없는
  자원에만 쓴다. 부재는 401, 정지는 403, 리소스 없음은 404다.
- 일반 사용자 토큰은 관리자 API에서 403, 토큰이 없는 요청은 401을 받는다.

### 관리자 초기 계정

MVP는 운영자가 준비한 단일 관리자 계정으로 운영한다. 관리자 회원가입, 여러 관리자 역할,
비밀번호 찾기·변경 UI, MFA, 관리자 소셜 로그인은 범위에 포함하지 않는다.
DB 전체 행 수를 하나로 제한하거나 기존 개발 관리자·감사 기록을 삭제하는 방식은 아니다.

dev와 prod는 `ADMIN_USERNAME`, `ADMIN_PASSWORD_HASH`로 초기 관리자 계정을 준비한다. 서버에는
평문 비밀번호가 아니라 BCrypt 해시만 저장한다. `htpasswd`가 설치된 안전한 관리 단말에서 다음
명령을 실행하면 비밀번호를 대화형으로 입력해 해시를 만들 수 있다.

```bash
htpasswd -nBC 12 operator
```

출력에서 콜론 뒤의 BCrypt 해시만 `ADMIN_PASSWORD_HASH`에 넣는다. 애플리케이션은 해당
`ADMIN_USERNAME`이 DB에 없을 때만 계정을 생성하며, 이미 있으면 저장된 해시를 덮어쓰지 않는다.
따라서 비밀번호 변경은 환경변수만 바꾸지 말고 별도 변경 절차로 DB의 해시도 갱신해야 한다.

### 로컬에서 실제 관리자 로그인 검증

`backend/.env.example`의 관리자 항목을 참고해 개인 `backend/.env`에 다음 키를 추가한다.
기존 `.env`를 예제 파일로 덮어쓰지 않고 누락된 키만 추가한다.

```dotenv
ADMIN_DEVELOPMENT_BYPASS_ENABLED=false
ADMIN_USERNAME=operator
ADMIN_PASSWORD_HASH=REPLACE_WITH_BCRYPT_HASH
```

- `operator`는 예시다. 고정 개발 계정 `dev-admin`과 다른, 사용할 새 아이디로 바꾼다.
- `ADMIN_PASSWORD_HASH`에는 위 `htpasswd` 명령 결과의 콜론 뒤 해시만 넣는다.
  `.env`는 Spring properties로도 읽으므로 해시의 `$`를 그대로 쓰고 따옴표로 감싸지 않는다.
- `ADMIN_DEVELOPMENT_BYPASS_ENABLED=false`이면 local에서도 해당 계정을 최초 생성하고
  관리자 JWT 인증을 사용한다. 로그인 API에는 해시가 아닌 원래 비밀번호를 입력한다.
- 로컬 우회 기본값과 예제 기본값은 `true`다. 실제 로그인을 시험할 때만 `false`로 바꾼다.
  test 프로필은 기존 개발 우회를 유지하며, dev/prod는 이 로컬 전용 환경변수를 사용하지 않는다.
- localhost 관리자 웹 Origin은 이미 허용돼 있으므로 로컬 `.env`에는
  `ADMIN_CORS_ALLOWED_ORIGIN`을 추가하지 않는다. 이 키는 dev/prod 배포 설정용이다.
- 관리자 JWT도 기존 액세스 토큰 설정을 사용하므로 `ACCESS_TOKEN_SECRET`,
  `ACCESS_TOKEN_EXPIRATION` 등 나머지 `.env.example` 항목도 필요하다.

설정 후 `backend` 디렉터리에서 `./gradlew bootRun`으로 실행한다. 이미 실행 중이면 종료 후
다시 실행해야 변경된 `.env`를 읽는다. 초기 계정 생성 외 기존 계정의 해시는 변경하지 않는다.
이 단계는 백엔드 로그인 API 검증이며, 관리자 웹의 로그인 화면·세션 연결은 #186에서 진행한다.

### 환경변수 자동 검사

`./gradlew test`, `./gradlew check`, `./gradlew build`는 환경변수 계약 검사를 먼저 실행한다.
GitHub CI와 CodeBuild도 기존 `./gradlew clean test bootJar --no-daemon` 경로를 통해 같은
검사를 실행하므로 실제 관리자 비밀번호나 해시를 CI 비밀변수에 추가할 필요가 없다.

검사는 Spring 설정의 변수 이름, 로컬·dev·prod 예제, CD 필수 키 목록을 대조한다. 개인 `.env`가
있으면 그 파일의 키 누락·중복·불필요한 키도 검사한다. 키가 맞지 않으면 테스트 실행 전에
실패하므로 최신 예제와 맞춰야 한다. 값 자체는 로그에 출력하거나 파일에 복사하지 않는다.
CI처럼 `.env`가 없으면 개인 파일 비교만 생략한다.
검사기 자체도 합성 설정으로 누락·중복·불필요한 키·잘못된 형식·필수 파일 누락·값 미출력을
검증한다. 이 회귀 테스트는 개인 `.env`를 읽거나 복사하지 않는다.

이 검사는 **키 계약**을 확인한다. placeholder가 실제 값으로 교체됐는지, EC2 설정이 준비됐는지,
로그인에 성공하는지까지 보장하지는 않는다. 설정한 해시는 애플리케이션 시작 시 별도로 검증한다.

### 관리자 웹 연결 순서

1. #223: 백엔드 관리자 인증·인가를 검증하고, dev 서버 환경변수 준비 후 병합·배포한다.
2. #186: 관리자 웹 로그인·세션 처리와 만료·401·403 처리를 연결한다.
3. #207: 인증된 Preview에서 실제 API와 모바일 화면을 검증한 뒤 관리자 웹을 병합한다.
4. 나머지 기능과 운영 배포를 검증한다. 알림 기능은 뒤로 미룬다.

백엔드 인증 PR은 `be/develop`, 관리자 웹 PR은 `admin-web`을 기준으로 진행한다.

## 이미지 저장소

이미지는 클라이언트가 S3에 직접 업로드하고, 조회는 CloudFront를 통한다. 백엔드는 이미지 바이트를 다루지 않고 업로드 완료 여부만 `HeadObject`로 확인한다.

```text
{bucket}/chalkak/
├── staging/{environment}/
│   ├── signatures/{uploadId}.png       사인 업로드 직후. Lambda 처리 대기
│   └── posts/{uploadId}.webp           포스트 업로드 직후. 처리·검수 대기
├── signatures/{environment}/
│   ├── original/{uploadId}.png         Lambda가 만든 검증 통과 원본
│   └── thumbnail/{uploadId}.png        Lambda가 만든 썸네일
└── posts/{environment}/
    ├── original/{uploadId}.webp        포스트 처리 후 원본 경로
    └── thumbnail/{uploadId}.webp       포스트 처리 후 썸네일 경로
```

- `{environment}`는 `dev` 또는 `prod`다. 하나의 Lambda가 두 환경을 함께 처리하므로, **입력 키의 이 세그먼트가 어느 백엔드로 콜백할지를 결정한다.**
- 확장자도 종류별 계약이다. 사인은 PNG, **포스트는 WebP 전용**이다.
- 하위 폴더 구조는 Lambda와 공유하는 약속이라 코드 상수다. 설정으로 두는 것은 `S3_PREFIX`(전 환경 `chalkak`)와 환경 세그먼트뿐이다.
- CloudFront 오리진이 `{bucket}/chalkak`을 가리키므로 **공개 URL에는 `chalkak/`이 들어가지 않는다.** `CLOUDFRONT_ORIGIN_PATH`가 이 값을 잡는다.

### 포스트 생성 파이프라인

포스트 이미지도 사인처럼 Lambda 완료를 기다리지 않는다. 대신 **presigned URL을
발급하는 시점에 `post_image_uploads` 행을 먼저 만든다.** 행이 항상 먼저
존재하므로 완료 콜백과 게시물 생성 요청 중 어느 쪽이 먼저 도착해도 상대가 남긴
상태를 읽는다. 순서 경쟁 자체가 사라진다.

```text
POST /api/v1/posts/uploads            ISSUED claim 행 생성, presigned URL 발급
  → 클라이언트가 staging에 WebP PUT
  → S3 ObjectCreated → SQS → Lambda    WebP 검증, 원본·썸네일 생성, EXIF 추출
  → POST /internal/v1/post-image-processing/{uploadId}/complete
                                       claim을 READY로 올리고 EXIF 보관
  → Lambda가 staging 객체 삭제          콜백 2xx를 받은 뒤에만

POST /api/v1/posts                    claim 상태에 따라 분기
  READY   → APPROVED 게시물로 즉시 생성. 썸네일 키·메타데이터 반영
  ISSUED  → VALIDATING 게시물로 생성. 완료 콜백이 APPROVED로 승격
  REJECTED→ 400. 거절 사유별 메시지
```

게시물 생성 요청은 `photoUploadId`만 받고 bucket, environment, storage key는
받지 않는다. 제목은 선택값이며 생략, `null`, 빈 문자열, 공백 문자열을 모두
`null`로 저장하고 최대 10자로 제한한다. 작성자는 삭제되지 않은 `ACTIVE`
사용자여야 하며 주제 참여 구간은 `startsAt <= now < endsAt`인 `OPEN` 상태다.

claim은 `SELECT ... FOR UPDATE`로 잠그고 `claimed_at`을 기록해 소비한다.
**다른 회원의 uploadId는 권한 없음이 아니라 `404`로 답한다.** 존재 여부를
알려주지 않기 위해서다. 사전 중복 검사 뒤에도 DB 유니크 제약을 최종 동시성
방어선으로 사용한다.

EXIF는 Lambda가 읽어 콜백으로 보내고 S3 객체에서는 제거한다. 위치와 촬영
시각, 기종 정보는 `photos.metadata`에만 남으며 **공개 조회 응답에는 포함하지
않는다.**

콜백이 유실되면 `VALIDATING` 게시물이 남는다. 정합성 보정 스윕은 후속
범위다.

### 사인 처리 파이프라인

사인 등록은 Lambda 처리를 기다리지 않는다. DB가 **active(현재 보여줄 사인)** 와 **pending(처리 중인 사인)** 을 분리해 들고 있다가, Lambda의 완료 콜백을 받은 뒤에만 active를 교체한다.

```text
클라이언트가 staging에 PNG 업로드
  → PUT /api/v1/users/me/signature      백엔드가 staging 객체 검증 후 pending 기록, 즉시 응답
  → S3 ObjectCreated → SQS → Lambda     원본·썸네일 생성
  → POST /internal/v1/signature-processing/{uploadId}/complete
                                        백엔드가 pending 일치를 확인하고 active로 승격
  → Lambda가 staging 객체 삭제           콜백 2xx를 받은 뒤에만
```

핵심은 **존재하지 않는 URL을 active로 노출하지 않는 것**이다. 새 사인 처리가 실패해도 기존 active 사인은 그대로 유지되고, 다른 사용자의 게시물 목록은 active 썸네일만 사용한다.

상태 불변 규칙:

1. active 키는 해당 S3 출력 객체가 저장된 뒤에만 바꾼다.
2. 새 pending을 등록해도 기존 active는 바꾸지 않는다.
3. 콜백의 `uploadId`가 현재 pending과 같고 `PROCESSING`일 때만 상태를 바꾼다.
4. 성공 승격은 원본·썸네일을 한 트랜잭션에서 함께 바꾼다.
5. 영구 실패는 `FAILED`로 저장하고, 타임아웃은 시작 시각과 현재 시각으로 판단한다. 둘 다 active는 바꾸지 않는다.
6. 모든 콜백은 멱등하다. 중복·역순 콜백은 상태를 바꾸지 않고 `204`로 끝난다.

백엔드는 콜백 본문에서 저장소 키를 받지 않고 `uploadId`로 직접 유도한다. 사인을 연속 등록하면 마지막 `uploadId`가 pending을 덮어쓰고, 먼저 보낸 작업의 완료 콜백은 pending 불일치로 무시된다.

### 환경 변수

| 변수 | 사용처 | 설명 |
|---|---|---|
| `S3_PREFIX` | 백엔드·Lambda | 루트 prefix. 전 환경 `chalkak` |
| `chalkak.image.environment` | 백엔드 | 경로의 `{environment}` 세그먼트. 프로필별로 `local`·`dev`·`prod`·`test` |
| `IMAGE_PROCESSOR_CALLBACK_SECRET` | 백엔드 | 이미지 처리 내부 API HMAC 검증 키. **32자 이상**이어야 기동한다 |
| `IMAGE_PROCESSING_API_SECRET` | Lambda | 백엔드의 `IMAGE_PROCESSOR_CALLBACK_SECRET`과 동일한 HMAC 서명 키 |
| `POST_IMAGE_PROCESSING_TIMEOUT` | 백엔드 | 게시물 이미지 처리 완료 콜백을 기다리는 필수 환경 변수 |
| `SIGNATURE_PROCESSING_TIMEOUT` | 백엔드 | 사인 처리 완료 콜백을 기다리는 필수 환경 변수 |
| `CALLBACK_MAX_BODY_BYTES` | 백엔드 | 이미지 처리 내부 API 요청 본문 상한 |
| `DEV_BACKEND_IMAGE_PROCESSING_API_BASE_URL` | Lambda | dev 백엔드 이미지 처리 API 주소. **`/internal/v1`까지 포함**해야 한다 |
| `PROD_BACKEND_IMAGE_PROCESSING_API_BASE_URL` | Lambda | prod 백엔드 이미지 처리 API 주소. **`/internal/v1`까지 포함**해야 한다 |
| `IMAGE_PROCESSING_API_TIMEOUT_SECONDS` | Lambda | presigned URL 발급과 완료·실패 요청의 HTTP timeout. 기본값 `3`초 |

백엔드의 `IMAGE_PROCESSOR_CALLBACK_SECRET`과 Lambda의 `IMAGE_PROCESSING_API_SECRET`은
이름만 다르고 값은 같아야 한다. 값이 어긋나면 내부 API 요청이 전부 `401`로 떨어지고 사인이
영원히 `PROCESSING`에 남으므로, 서명 실패는 즉시 알람 대상이다.

> **API base URL의 경로를 빠뜨리면 기동에 실패한다.** 환경 변수에는 `/internal/v1`까지
> 포함한다. Lambda는 이미지 종류에 따라 `/signature-processing` 또는
> `/post-image-processing`을 붙이고, 이어서 `/{uploadId}/{result}`를 붙인다.

### 로컬 실행

**로컬에서는 사인 처리가 완료되지 않는다.** Lambda 라우터가 `staging/dev/`와 `staging/prod/`만 인식하고 콜백 URL도 두 환경만 설정돼 있어서, `environment: local`로 올라간 객체는 `unsupported staging path`로 반려된다. 로컬은 등록 API의 pending 기록까지만 확인할 수 있다.

조회 자체는 개인 IAM 자격증명 없이도 동작한다. 버킷에 익명 공개 읽기 권한이 있어 `application-local.yml`의 `chalkak.image.anonymous-access: true`가 익명 호출로 전환하기 때문이다. 기본값은 `false`이므로 배포 환경은 EC2 인스턴스 역할을 그대로 사용한다.

> 업로드는 여전히 자격증명이 필요하다. 로컬에서 검증하려면 권한이 있는 팀원이 콘솔로 `{uuid}.png`(확장자 소문자)를 한 번 올려두면 된다.
>
> 버킷 정책에서 공개 읽기가 제거되면 로컬 조회가 즉시 막힌다. 그때는 LocalStack으로 전환해야 한다.

## DB 스키마

모든 스키마 변경은 다음 경로의 Flyway 마이그레이션 파일로 관리한다.

```text
src/main/resources/db/migration/VyyyyMMddHHmm__변경_내용.sql
```

예시:

```text
V202608071030__create_posts.sql
V202608071045__create_likes.sql
```

규칙:

- 버전은 파일 생성 시각을 KST 기준 `yyyyMMddHHmm` 형식으로 작성한다.
- PR 병합 전 최신 브랜치와 버전 중복 및 순서를 확인한다.
- 공유 브랜치나 DB에 반영된 파일은 수정하거나 이름을 변경하지 않는다. 변경이 필요하면 새 파일을 추가한다.
- Hibernate는 `ddl-auto: validate`만 사용하며 스키마 변경은 Flyway로 관리한다.
- Entity 변경 PR에는 마이그레이션 SQL과 Repository 또는 통합 테스트를 포함한다.

## 테스트

로컬 개발 DB와 테스트 DB는 서로 다른 PostgreSQL 컨테이너와 볼륨으로 분리한다.

| 용도 | 컨테이너 | DB | 포트 |
|---|---|---|---|
| 개발 | `chalkak-postgres` | `chalkak` | `5432` |
| 테스트 | `chalkak-postgres-test` | `chalkak_test` | `5433` |

규칙:

- `./gradlew test`는 `test` 프로필로 실행하며 테스트 DB만 사용한다.
- 테스트 실행 전 `docker compose up -d postgres-test`로 테스트 DB를 실행한다.
- 테스트에서 개발 DB에 접근하거나 개발 데이터를 사용하지 않는다.
- 개발 DB와 테스트 DB는 동일한 Flyway 마이그레이션으로 스키마를 구성한다.
- 각 테스트는 실행 순서나 기존 데이터에 의존하지 않는다.
- 테스트 데이터는 트랜잭션 롤백 또는 명시적인 정리를 통해 격리한다.
- CI는 별도의 PostgreSQL 서비스 컨테이너와 `chalkak_test` DB를 생성한다.

## 배포 환경

개발·운영 CI/CD 구성과 CodePipeline·CodeBuild·CodeDeploy·Flyway 운영 규칙은 [`deploy/README.md`](deploy/README.md)를 참고한다.

민감한 환경변수는 저장소에 커밋하거나 build artifact에 포함하지 않는다. 각 EC2의 `/etc/chalkak/application.env`에 `root:root`, `600` 권한으로 저장하고 systemd가 애플리케이션에 전달한다.

주의사항:

- 기존 테이블이 있는 DB에 Flyway를 처음 적용하면 실패할 수 있다. 필요한 경우에만 `spring.flyway.baseline-on-migrate=true`를 한시적으로 사용한다.
- 마이그레이션 실패는 애플리케이션 기동 실패로 이어진다.
- 애플리케이션을 이전 버전으로 롤백해도 이미 적용된 스키마는 자동으로 되돌아가지 않는다.
- 적용된 마이그레이션 파일을 수정하면 체크섬 불일치로 이후 배포가 실패한다.
