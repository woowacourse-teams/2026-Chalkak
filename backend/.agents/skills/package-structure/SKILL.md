---
name: package-structure
description: backend/src/main/java에서 Java 클래스나 패키지를 생성·이동하거나 배치 위치를 결정·리뷰할 때 사용한다. 기존 클래스 내부 구현만 수정하거나 테스트 코드만 변경할 때는 사용하지 않는다.
---

# 패키지 구조 컨벤션

## 적용 방법

1. 변경 대상이 `src/main/java`인지 확인한다.
2. 클래스가 속하는 도메인을 먼저 결정한다.
3. 기존 도메인 패키지와 유사한 구현을 확인한다.
4. 아래 배치 규칙에 따라 파일 위치와 `package` 선언을 결정한다.
5. 관련 없는 기존 파일이나 패키지를 함께 이동하지 않는다.

API 버전의 추가·변경·삭제가 포함되면 `$api-versioning`도 함께 적용한다.

## 기본 구조

운영 코드는 다음 소스 루트를 기준으로 도메인 단위로 구성한다.

```text
src/main/java/com/chalkak/backend/
└── {domain}/
    ├── api/
    │   └── v{n}/
    │       ├── controller/
    │       └── dto/
    │           ├── request/
    │           └── response/
    ├── service/
    ├── repository/
    ├── infrastructure/
    │   ├── persistence/
    │   └── infra/
    └── domain/
```

`controller`, `service`, `repository` 같은 레이어를 최상위 패키지로 만들지 않는다. `post`, `topic`, `user`, `like`, `photo`처럼 도메인을 먼저 나누고 그 안에 레이어를 둔다.

여러 도메인이 공유하는 전역 예외 처리와 애플리케이션 부트스트랩 코드는 기존 최상위 위치를 유지한다. 이 규칙을 이유로 `exception/`이나 `BackendApplication`을 개별 도메인으로 이동하지 않는다.

## 배치 기준

| 코드 역할 | 배치 위치 |
|---|---|
| Controller | `{domain}/api/v{n}/controller` |
| 요청 DTO | `{domain}/api/v{n}/dto/request` |
| 응답 DTO | `{domain}/api/v{n}/dto/response` |
| 유스케이스 Service | `{domain}/service/XxxService` |
| 저장소 포트 | `{domain}/repository/XxxRepository` |
| JPA 엔티티·애그리게이트·VO·Enum | `{domain}/domain` |
| Spring Data JPA 인터페이스 | `{domain}/infrastructure/persistence/XxxJpaRepository` |
| 저장소 포트 구현체 | `{domain}/infrastructure/persistence/XxxRepositoryImpl` |
| S3·SQS·외부 API 구현체 | `{domain}/infrastructure/infra` |

## API 계층

- API 버전은 `api/v1`, `api/v2`처럼 표현 계층에만 적용한다.
- 버전별 Controller와 Request·Response DTO를 해당 버전 아래에 둔다.
- `domain`, `service`, `repository`, `infrastructure`를 API 버전별로 복제하지 않는다.
- 새로운 API 버전은 기능 추가가 아니라 Breaking Change가 있을 때만 만든다.

## Service

- 등록·수정·삭제·상태 변경과 단건·목록·피드 조회는 `XxxService`가 담당한다.
- Service에서 Spring Data JPA, AWS SDK 등 외부 기술에 직접 의존하지 않는다.

## Repository와 Persistence

- `{domain}/repository/XxxRepository`는 애플리케이션이 필요로 하는 저장 기능만 선언하는 포트다.
- `XxxRepository`가 `JpaRepository`를 직접 상속하지 않는다.
- `XxxJpaRepository`는 Spring Data JPA 인터페이스로 둔다.
- `XxxRepositoryImpl`이 도메인 저장소 포트를 JPA에 연결한다.
- JPA 엔티티는 `{domain}/domain`의 도메인 모델이 직접 담당한다.
- 별도의 `XxxJpaEntity`와 `XxxMapper`를 만들지 않는다.

## Infrastructure

- DB, JPA, QueryDSL 코드는 `infrastructure/persistence`에 둔다.
- S3, SQS, CDN, 외부 인증 및 외부 API 구현체는 `infrastructure/infra`에 둔다.
- 외부 연동 포트의 위치가 기존 코드에 있으면 그 구조를 따른다.
- 기존 기준이 없으면 새로운 포트 패키지를 임의로 만들지 않고 사용자에게 확인한다.

## 기존 코드 보호

- 컨벤션 적용만을 목적으로 기존 패키지 전체를 일괄 이동하지 않는다.
- 요청받은 기능에 필요한 새 파일과 직접 관련된 파일에만 적용한다.
- 하나의 클래스가 여러 위치에 해당하면 책임을 기준으로 판단한다.
- 책임이나 도메인 경계가 불분명하면 임의로 배치하지 않고 사용자에게 확인한다.
- 실제 파일 경로와 Java `package` 선언을 일치시킨다.

## 완료 전 확인

- 최상위 패키지가 도메인 단위인지 확인한다.
- API 버전이 `api/` 내부에만 존재하는지 확인한다.
- Request와 Response DTO가 분리됐는지 확인한다.
- 저장소 포트가 Spring Data JPA를 직접 상속하지 않는지 확인한다.
- 외부 기술 구현이 `infrastructure/` 밖으로 노출되지 않았는지 확인한다.
- 관련 없는 기존 패키지를 이동하지 않았는지 확인한다.
