---
name: test-code
description: backend/src/test/java의 Java 테스트 코드를 생성·수정·리뷰할 때 사용한다. 운영 코드와 client 코드에는 사용하지 않는다.
---

# 테스트 코드 컨벤션

## 적용 방법

1. 변경 대상이 `src/test/java`인지 확인한다.
2. 테스트 대상 계층과 테스트 유형을 결정한다.
3. 아래 전략과 규칙을 적용한다.
4. 관련 없는 기존 테스트는 변경하지 않는다.
5. 완료 전 변경한 테스트를 아래 규칙으로 다시 확인한다.

## 기본 스타일

- Google Java Style 기반 팀 형식은 저장소 Spotless 설정을 따른다. `./gradlew spotlessCheck`로 확인하고 형식 수정 후 diff를 점검한다.
- 테스트에는 운영 코드 전용 Checkstyle 규칙을 적용하지 않는다.

## 테스트 유형

- 단위 테스트: Spring Context 없이 `new`로 객체를 생성해 검증한다.
- 슬라이스 테스트: `@DataJpaTest`, `@WebMvcTest` 등 일부 Context만 로딩한다.
- 통합 테스트: `@SpringBootTest`, 실제 Repository, PostgreSQL을 사용한다.

## 계층별 전략

- Domain: Spring과 DB를 사용하지 않는 단위 테스트
- Service: 실제 Repository와 PostgreSQL을 사용하는 통합 테스트
- Repository: 복잡한 Query만 별도 통합 테스트
- Controller: `@WebMvcTest`와 Service Mock
- Infrastructure: Adapter 단위의 통합 테스트
- 접근 정책: `IntegrationTestSupport`와 MockMvc를 사용하는 통합 테스트
- 동시성·트랜잭션: `DatabaseCleaner`로 정리하는 통합 테스트
- API 문서 계약: 생성된 OpenAPI 문서를 조회하는 통합 테스트

## 테스트 케이스

- 각 계층의 테스트 전략 범위에서 적용 가능한 경계값을 검토하고 테스트한다.
- 경계가 존재하면 경계 직전·경계값·경계 직후를 포함한다.
- 각 계층의 필수 검증 항목은 해당 계층의 책임만 다룬다. 하위 계층에서 검증한 규칙을 상위 계층에서 반복하지 않는다.

### Domain

- 도메인 객체와 VO의 비즈니스 규칙을 검증한다.
- Spring Context와 DB를 사용하지 않는다.

필수 검증 항목:

- 생성·변경 검증 조건마다 성공과 실패
- 경계 직전·경계값·경계 직후
- 상태 전이마다 허용과 금지
- 정규화 규칙마다 1개

### Service

- Repository를 Mock하지 않는다.
- 실제 Repository와 PostgreSQL을 사용한다.
- 유스케이스 단위로 작성한다.

필수 검증 항목:

- 유스케이스마다 정상 흐름 1개
- 예외를 던지는 경로마다 1개 (대상 없음, 삭제됨, 권한 없음, 상태상 불가 등)
- 반환값뿐 아니라 DB에 반영된 결과
- 삭제 전파, 알림 발송 같은 부수효과

도메인 규칙의 세부 경계값은 반복하지 않는다.

### Repository

Spring Data JPA 기본 메서드는 별도로 테스트하지 않는다. 다음 항목만 테스트한다.

- 직접 작성한 JPQL 또는 네이티브 쿼리
- QueryDSL 동적 쿼리의 조건 조합
- fetch join과 페이징 조합
- cascade와 orphanRemoval 전파
- 유니크 제약
- 낙관적 락
- soft delete된 행이 조회에서 제외되는지
- 부분 유니크 인덱스의 삭제 후 재생성 허용과 활성 행 중복 거부

조회 전 `em.flush()`와 `em.clear()`를 호출한다.

### Controller

- `@WebMvcTest`와 Service `@MockitoBean`을 사용한다.
- 요청 검증, 상태 코드, 직렬화, 예외 처리를 검증한다.
- 인가는 필터를 끈 `@WebMvcTest`에서 검증하지 않고 접근 정책 테스트에서 검증한다.
- 비즈니스 로직 분기는 검증하지 않는다.

필수 검증 항목:

- 엔드포인트마다 정상 응답 1개 (상태 코드와 응답 필드)
- 요청 필드마다 대표 검증 실패 1개, 직접 작성한 검증은 조건마다 1개
- Service 예외마다 ErrorCode와 상태 코드 변환 1개

### 접근 정책

인증·회원 상태에 따른 접근 허용 여부를 검증한다.

필수 검증 항목:

- 엔드포인트를 추가하면 인증 없는 요청이 해당 엔드포인트의 접근 정책대로 허용 또는 거부되는지
- 엔드포인트를 추가하면 정지·탈퇴 회원의 허용 또는 거부를 기존 접근 정책 테스트에 추가
- 기존 엔드포인트의 인증 요구나 회원 상태별 접근 규칙을 바꾸면 바뀐 조건마다 변경 후의 허용 또는 거부

### 동시성·트랜잭션

필수 검증 항목:

- 락, 유니크 제약, 조건부 UPDATE 같은 동시성 방어를 추가하면 방어마다 경쟁 시나리오 1개
- `AFTER_COMMIT`, `REQUIRES_NEW`를 사용하면 커밋 경로와 롤백 경로 각 1개

동시성 방어가 없는 코드에 경쟁 시나리오 테스트를 추가하지 않는다.

### API 문서 계약

필수 검증 항목:

- 엔드포인트를 추가·변경하면 경로, 파라미터 형식, 보안, 응답 코드 목록

### Infrastructure

필수 검증 항목:

- Adapter가 직접 판정하는 검증 조건마다 1개
- 외부 오류를 도메인 예외로 변환하는 경로마다 1개

### Architecture

- 아키텍처 규칙을 추가하면 통과하는 fixture와 위반하는 fixture를 각 1개 작성한다.

## 외부 시스템

- S3, Lambda, OAuth, Push, 외부 API는 Port 인터페이스로 감싼다.
- 실제 외부 시스템은 일반 테스트 경계 밖에 둔다.
- 반환값만 필요하거나 예외를 주입할 때는 Mock을 사용한다.
- 호출 간 상태가 이어지거나 설정이 반복되면 Fake를 사용한다.
- Fake는 테스트 소스에 두고 `Fake` 접두사를 사용한다.
- `verify`는 호출 자체가 요구사항일 때만 사용한다.
- 그 외에는 결과를 `assertThat`으로 검증한다.
- Adapter 실제 연동은 Infrastructure 통합 테스트에서 검증한다.

## DB 환경

- 통합 테스트는 PostgreSQL을 사용한다.
- H2를 PostgreSQL 대체재로 사용하지 않는다.
- 로컬은 `compose.yaml`의 `postgres-test` 컨테이너를 사용한다.
- CI는 워크플로의 PostgreSQL 서비스 컨테이너를 사용한다.
- 접속 정보는 `application-test.yml`의 `TEST_POSTGRES_*` 환경변수로 바꾼다.
- 테스트 코드에서 DB 컨테이너를 직접 띄우지 않는다.

## DB 격리

일반적인 Service 통합 테스트는 각 테스트 클래스에 `@Transactional`을 선언해 Rollback을 사용한다.

다음 테스트는 `@Transactional`을 사용하지 않고 `DatabaseCleaner`로 정리한다.

- `@TransactionalEventListener(AFTER_COMMIT)`
- `REQUIRES_NEW`
- `@Async`
- 스케줄러 또는 별도 스레드의 DB 접근
- `RANDOM_PORT`와 실제 HTTP 호출
- 트랜잭션 종료 이후 동작 검증

추가 규칙:

- 기존 Transactional 테스트에 도메인 이벤트나 별도 스레드가 추가되면 TRUNCATE 방식으로 전환한다.
- `DatabaseCleaner`는 `TRUNCATE ... RESTART IDENTITY CASCADE`를 사용한다.
- `DatabaseCleaner`는 프로젝트 초기에 작성한다.
- DB 정리는 `@BeforeEach`에서 수행한다.
- DB 정리에 `deleteAll()`을 사용하지 않는다.
- DB 초기화를 목적으로 `@DirtiesContext`를 사용하지 않는다.
- ApplicationContext 자체가 오염된 경우에만 `@DirtiesContext`를 사용한다.

## 통합 테스트 상위 클래스

- 통합 테스트는 `IntegrationTestSupport`를 상속한다.
- Context 설정, 프로파일, 공통 `@MockitoBean`을 상위 클래스에서 통일한다.
- 상위 클래스에 `@Transactional`을 선언하지 않는다. 롤백으로 격리할 수 없는 테스트도 같은 상위 클래스를 상속할 수 있어야 하므로, 격리 방식은 각 테스트 클래스가 선언한다.
- Context 캐싱을 유지하기 위해 개별 테스트 클래스에 별도 Context 설정을 추가하지 않는다.

## 네이밍

- 테스트 클래스는 영문 이름과 `Test` 접미사를 사용한다.
- 테스트 유형에 따라 클래스 접미사를 구분하지 않는다.
- 테스트 메서드는 `메서드명_상황_결과` 형식의 영문으로 작성한다.
- `@DisplayName`에는 같은 의미를 한글로 작성한다.
- 메서드명을 변경하면 `@DisplayName`도 함께 변경한다.
- `build.gradle`의 `JavaCompile`과 `Test`에 UTF-8 인코딩을 설정한다.

## 구조

- 테스트 패키지는 운영 코드 패키지 구조를 미러링한다.
- Given/When/Then 주석을 사용한다.
- When과 Then을 나누기 어려우면 `When & Then`으로 묶는다.

## 테스트 제외 대상

다음 대상은 테스트하지 않는다.

- getter와 setter
- `toString()`
- Service를 그대로 호출하는 로직 없는 Controller
- 라이브러리 자체 동작
- 로직 없는 DTO 변환
- `save`, `findById`, `delete` 등 단순 JPA 기본 동작
