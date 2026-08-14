# 찰캌 백엔드

## 목차

- [개발환경 표준](#개발환경-표준)
- [사전 설치](#사전-설치)
- [최초 실행](#최초-실행)
- [IntelliJ 설정](#intellij-설정)
- [자주 사용하는 명령](#자주-사용하는-명령)
- [프로필](#프로필)
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

민감한 환경변수는 저장소에 커밋하거나 build artifact에 포함하지 않는다. 각 EC2의 `/etc/chalcak/application.env`에 `root:root`, `600` 권한으로 저장하고 systemd가 애플리케이션에 전달한다.

주의사항:

- 기존 테이블이 있는 DB에 Flyway를 처음 적용하면 실패할 수 있다. 필요한 경우에만 `spring.flyway.baseline-on-migrate=true`를 한시적으로 사용한다.
- 마이그레이션 실패는 애플리케이션 기동 실패로 이어진다.
- 애플리케이션을 이전 버전으로 롤백해도 이미 적용된 스키마는 자동으로 되돌아가지 않는다.
- 적용된 마이그레이션 파일을 수정하면 체크섬 불일치로 이후 배포가 실패한다.
