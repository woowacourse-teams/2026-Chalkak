# 찰캌 백엔드

모노레포의 백엔드 모듈이다. Gradle 루트는 이 `backend/` 디렉터리이므로 모든 명령은 여기서 실행한다.

## 개발환경 표준

| 대상          |              확정 버전 | 통일 방법                                 |
|-------------|-------------------:|---------------------------------------|
| JDK 배포판     | Eclipse Temurin 25 | `.java-version`, Gradle Toolchain, CI |
| Java        |                 25 | Toolchain                             |
| Spring Boot |              4.1.0 | `build.gradle.kts` 플러그인               |
| Gradle      |              9.6.1 | Gradle Wrapper                        |
| PostgreSQL  |               18.4 | Docker 공식 이미지 태그                      |
| DB 스키마      |             Flyway | `src/main/resources/db/migration`     |

Spring Boot 애플리케이션은 각자의 로컬 JVM에서 실행하고, PostgreSQL만 Docker Compose로 띄운다.

```text
Spring Boot (로컬 JVM, ./gradlew bootRun)
        │
        ▼
PostgreSQL (Docker Compose, localhost:5432)
```

## 사전 설치

- Git
- Eclipse Temurin JDK 25
- Docker Desktop 또는 Docker Engine (Compose Plugin 포함)
- IntelliJ IDEA

로컬 Gradle은 설치하지 않는다. 저장소에 포함된 Gradle Wrapper를 사용한다.

로컬에 JDK 25가 없어도 Gradle Toolchain이 Temurin 25를 자동으로 내려받는다. 다만 IntelliJ에서 직접 실행할 때를 위해 JDK 25 설치를 권장한다.

## 최초 실행

```bash
cd backend
./scripts/setup-local.sh
```

스크립트가 하는 일은 다음과 같다.

1. `.env.example` 을 `.env` 로 복사
2. `java -version`, `docker compose version` 출력
3. `docker compose up -d`
4. `chalkak-postgres` 가 healthy 가 될 때까지 대기
5. `./gradlew build`

수동으로 진행할 경우:

```bash
cd backend
cp .env.example .env
java -version
./gradlew --version
docker compose up -d
docker compose ps
./gradlew bootRun
```

헬스체크:

```bash
curl http://localhost:8080/actuator/health
# {"status":"UP"}
```

## 자주 사용하는 명령

모두 `backend/` 에서 실행한다.

| 목적            | 명령                                                        |
|---------------|-----------------------------------------------------------|
| PostgreSQL 시작 | `docker compose up -d`                                    |
| PostgreSQL 종료 | `docker compose down`                                     |
| 로그 확인         | `docker compose logs -f postgres`                         |
| 컨테이너 상태       | `docker compose ps`                                       |
| DB 접속         | `docker compose exec postgres psql -U chalkak -d chalkak` |
| 애플리케이션 실행     | `./gradlew bootRun`                                       |
| 테스트           | `./gradlew test`                                          |
| 전체 빌드         | `./gradlew clean build`                                   |

Windows에서는 `./gradlew` 대신 `gradlew.bat` 을 사용한다.

### 로컬 DB 초기화

로컬 데이터를 전부 버리고 처음부터 다시 시작해야 할 때만 사용한다.

```bash
docker compose down -v && docker compose up -d
```

> `-v` 는 볼륨을 삭제한다. 테이블, 데이터, Flyway 적용 기록이 모두 사라지고 되돌릴 수 없다.
> **로컬 개발환경에서만** 사용하고, 공유 개발 서버나 운영환경에서는 절대 실행하지 않는다.

## 프로필

| 프로필 | 설정 파일 | 활성화 방법 |
| --- | --- | --- |
| `local` | `application-local.yml` | **기본값. 아무것도 지정하지 않으면 자동 적용된다** |
| `test` | `application-test.yml` | `./gradlew test` 실행 시 자동 적용 |
| `prod` | `application-prod.yml` | 배포 환경에서 `SPRING_PROFILES_ACTIVE=prod` 로만 활성화 |

`application.yml` 에 `spring.profiles.default: local` 이 있어서 **로컬 개발은 별도 설정 없이 `local` 프로필로 실행된다.**

```bash
./gradlew bootRun            # local 프로필로 실행된다
```

IntelliJ에서 메인 클래스를 실행할 때도 마찬가지로 `local` 이 적용된다. 실행 중인 프로필은 기동 로그에서 확인할 수 있다.

```text
The following 1 profile is active: "local"
```

프로필을 명시하고 싶다면 다음 중 하나를 쓴다.

```bash
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
./gradlew bootRun --args='--spring.profiles.active=local'
```

> `prod` 프로필은 RDS 등 배포 환경 전용이다. **로컬 터미널에서 `prod` 로 실행하지 않는다.** 실수로 원격 스키마에 마이그레이션이 적용될 수 있다.
> `application-prod.yml` 은 DB 접속 정보에 기본값을 두지 않으므로, 환경변수 없이 실행하면 기동 단계에서 즉시 실패한다.

## 환경변수

`.env` 는 Docker Compose가 읽는다. IntelliJ에서 실행한 Spring Boot는 `.env` 를 자동으로 읽지 않으므로, `application-local.yml` 에 Compose와 동일한
기본값(`chalkak` / `chalkak` / `local-password` / `5432`)을 넣어두었다. 기본값을 바꾸지 않는 한 별도 설정 없이 동작한다.

기본값을 바꿨다면 IntelliJ Run Configuration의 환경변수에 `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_PORT` 를 등록한다.

새 환경변수를 추가하면 `.env.example`, `application-*.yml`, 이 README, CI Workflow를 함께 수정한다. Secret 값 자체는 커밋하지 않는다.

### 배포 환경 (RDS)

`prod` 프로필은 다음 환경변수를 요구한다. 기본값이 없으므로 하나라도 빠지면 기동에 실패한다.

| 변수 | 예시 | 비고 |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | `prod` | 이 값이 있어야 `prod` 프로필이 적용된다 |
| `DB_HOST` | `xxx.ap-northeast-2.rds.amazonaws.com` | RDS 엔드포인트 |
| `DB_PORT` | `5432` | 생략 시 5432 |
| `DB_NAME` | `chalkak` | |
| `DB_USERNAME` | | DDL 권한이 있는 계정 |
| `DB_PASSWORD` | | Secrets Manager / SSM Parameter Store로 주입한다 |

애플리케이션이 기동하면서 Flyway가 RDS에 마이그레이션을 적용한다. 인스턴스가 여러 개여도 Flyway가 PostgreSQL advisory lock을 잡으므로 한 인스턴스만 실행하고 나머지는 대기한다.

주의사항:

- **RDS의 PostgreSQL 메이저 버전을 로컬(18)과 맞춘다.** `aws rds describe-db-engine-versions --engine postgres` 로 지원 버전을 먼저 확인한다.
- 이미 테이블이 있는 RDS에 Flyway를 처음 붙이면 실패한다. 그 경우에만 `spring.flyway.baseline-on-migrate=true` 를 한시적으로 사용한다.
- 마이그레이션 실패는 곧 기동 실패다. 애플리케이션을 이전 버전으로 롤백해도 **스키마는 되돌아가지 않는다.**
- 이미 배포 환경에 적용된 마이그레이션 파일을 수정하면 체크섬 불일치로 다음 배포가 막힌다 (`validate-on-migrate` 기본값 `true`).
- RDS가 private subnet에 있으면 GitHub Actions 러너에서 직접 접근할 수 없다. 마이그레이션은 VPC 안에서 도는 애플리케이션 기동 시점에 수행된다.

## DB 스키마

**아직 DB 설계 전이라 마이그레이션 파일이 없다.** Flyway 설정만 되어 있어 애플리케이션은 정상 기동하며, `flyway_schema_history` 테이블만 생성된다.

설계가 확정되면 아래 위치에 파일을 추가하면 바로 적용된다.

```text
src/main/resources/db/migration/V1__create_users.sql
src/main/resources/db/migration/V2__create_topics.sql
...
```

규칙:

- 개발자가 직접 테이블을 만들지 않는다. 스키마 변경은 전부 마이그레이션 파일로 관리한다.
- 이미 공유 저장소에 반영된 마이그레이션 파일은 수정하지 않고 새 버전 파일을 추가한다.
- 한 PR에서 다른 팀원과 같은 버전 번호를 쓰지 않도록 확인한다.
- 애플리케이션 실행 시 Flyway가 먼저 스키마를 반영하고, Hibernate는 `ddl-auto: validate` 로 검증만 한다.
- `ddl-auto: create`, `create-drop` 은 사용하지 않는다.

엔티티를 변경하는 PR에는 마이그레이션 SQL과 Repository/통합 테스트를 함께 포함한다.

## 테스트

`./gradlew test` 는 `test` 프로필로 실행되며, 기본값은 로컬 Docker PostgreSQL을 바라본다. 따라서 테스트 전에 `docker compose up -d` 가 되어 있어야 한다.

> 이 구성은 테스트가 로컬 개발 DB를 그대로 사용한다. Repository·통합 테스트가 늘어나면 Testcontainers PostgreSQL로 전환해 개발 DB와 분리한다. H2를 PostgreSQL 대체재로
> 사용하지 않는다.

CI는 `postgres:18.4` 서비스 컨테이너의 `chalkak_test` DB를 사용한다 (`.github/workflows/backend-ci.yml`).

## 버전 변경 규칙

Java, Spring Boot, Gradle, PostgreSQL, Flyway, 주요 플러그인 버전은 개인이 임의로 변경하지 않는다. 별도 업그레이드 PR을 만들고 다음을 함께 작성한다.

```text
변경 전 버전 / 변경 후 버전 / 변경 이유
호환성 확인 결과 / 로컬 테스트 결과 / CI 결과
팀원이 추가로 해야 할 작업
```

## 문제 해결

**포트 5432가 이미 사용 중**

```bash
lsof -i :5432
```

로컬에 설치한 PostgreSQL을 종료하거나, `.env` 에서 `POSTGRES_PORT=5433` 으로 바꾼다. Spring 설정도 `${POSTGRES_PORT}` 를 사용하므로 함께 반영된다.

**컨테이너가 시작되지 않음**

```bash
docker compose logs postgres
```

컨테이너 이름 충돌, 포트 충돌, 잘못된 환경변수, 기존 볼륨과의 PostgreSQL 버전 비호환을 확인한다.

**DB 연결 실패**

`docker compose ps` → healthy 여부 → `.env` 값 → `application-local.yml` 의 DB 이름 → 포트 → 사용자명/비밀번호 순으로 확인한다.

**Gradle이 다른 Java를 사용**

```bash
./gradlew --version
```

IntelliJ의 `Gradle JVM` 과 터미널의 `JAVA_HOME` 을 확인한다.

## IntelliJ 설정

```text
Project SDK        : Eclipse Temurin 25
Language level     : 25
Gradle JVM         : Eclipse Temurin 25 (또는 Project SDK)
Build and run using: Gradle
Run tests using    : Gradle
```

모노레포이므로 IntelliJ에서 Gradle 프로젝트를 열 때 저장소 루트가 아니라 `backend/build.gradle.kts` 를 지정해 import한다.
