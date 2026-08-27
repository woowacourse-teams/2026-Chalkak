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

## 임시 인증

로그인 사용자는 `X-User-Id` 헤더로 식별한다.

```bash
curl -X PUT localhost:8080/api/v1/users/me/signature \
  -H "X-User-Id: 01a03199-f6e2-764c-afbf-23f7b0429eb6" \
  -H 'Content-Type: application/json' \
  -d '{"signatureOriginalUploadId":"<uuid>"}'
```

> **이 방식은 한시적이다.** MVP 핵심 기능을 먼저 만들고 로그인·회원가입·인증인가를 마지막에 붙이기로 해서, 그 전까지 쓰는 임시 수단이다. **헤더 값을 검증 없이 신뢰하므로 누구나 남의 계정을 조작할 수 있다.**

배포 환경 유출을 막기 위해 임시 인증 관련 빈과 컨트롤러에 `@Profile("!prod")`를 붙인다.

| 대상 | 역할 |
|---|---|
| `LoginUserArgumentResolver` | `X-User-Id`를 `AuthenticatedUser`로 변환. 없거나 UUID가 아니면 401 |
| `WebMvcConfig` | 리졸버를 필수 생성자 파라미터로 주입받아 등록 |
| `UserController` 등 임시 인증 사용 컨트롤러 | prod에서 미등록 → 404 |
| `OptionalLoginUserArgumentResolver` | 공개 API에서 헤더가 없으면 비로그인, 있으면 로그인 사용자로 변환. prod에서는 헤더를 무시 |
| `OptionalLoginUserWebMvcConfig` | 선택 인증 리졸버를 모든 프로필에 등록 |

규칙:

- **`@LoginUser`를 쓰는 컨트롤러에는 반드시 `@Profile("!prod")`를 붙인다.** 빠뜨리면 prod에 리졸버가 없어 `AuthenticatedUser`가 `@ModelAttribute`로 바인딩되고 userId가 null인 채 동작할 수 있다.
- `WebMvcConfig`의 생성자 파라미터를 `Optional`이나 `ObjectProvider`로 바꾸지 않는다. 비운영 프로파일에서 리졸버가 사라지면 기동 단계에서 드러나야 한다.
- 컨트롤러는 헤더를 직접 읽지 않고 `@LoginUser AuthenticatedUser`를 받는다.
- 로그인 여부에 따라 응답만 개인화하는 공개 API는 `@OptionalLoginUser Optional<AuthenticatedUser>`를 받는다. 헤더가 없으면 비로그인으로 처리하고, 임시 헤더를 신뢰하지 않는 prod에서는 항상 비로그인으로 처리한다.

Spring Security를 도입할 때 리졸버가 `SecurityContextHolder`를 읽도록 바꾸고 `@Profile`을 제거한다. **컨트롤러 시그니처와 Service는 바뀌지 않는다.** 도입 이슈는 아직 생성되지 않았다.

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
| `IMAGE_PROCESSOR_CALLBACK_SECRET` | 백엔드·Lambda | 콜백 HMAC 서명 키. **32자 이상**이어야 기동한다 |
| `POST_IMAGE_PROCESSING_TIMEOUT` | 백엔드 | 게시물 이미지 처리 완료 콜백을 기다리는 필수 환경변수 |
| `SIGNATURE_PROCESSING_TIMEOUT` | 백엔드 | 사인 처리 완료 콜백을 기다리는 필수 환경변수 |
| `DEV_BACKEND_CALLBACK_URL` | Lambda | dev 백엔드 콜백 URL. **`/internal/v1/signature-processing`까지 포함**해야 한다 |
| `PROD_BACKEND_CALLBACK_URL` | Lambda | prod 백엔드 콜백 URL. **`/internal/v1/signature-processing`까지 포함**해야 한다 |

백엔드와 Lambda가 같은 secret을 공유한다. 값이 어긋나면 콜백이 전부 `401`로 떨어지고 사인이 영원히 `PROCESSING`에 남으므로, 서명 실패는 즉시 알람 대상이다.

> **콜백 URL의 경로를 빠뜨리면 조용히 실패한다.** Lambda는 서명 대상 경로를 `/internal/v1/signature-processing/{uploadId}/{result}`로 **고정**해 계산하고, 실제 요청 URL은 `{base_url}/{uploadId}/{result}`로 만든다. 둘은 `base_url`이 정확히 그 경로로 끝날 때만 일치한다. 경로를 빼고 호스트만 넣으면 서명은 유효한데 요청이 `404`로 떨어지고 SQS가 무한 재시도한다.

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
