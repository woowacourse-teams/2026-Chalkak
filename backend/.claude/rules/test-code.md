---
paths:
  - "src/test/java/**/*.java"
---

# 테스트 코드 컨벤션

## 적용 범위

- 새로 작성하거나 수정한 테스트에만 적용한다.
- 관련 없는 기존 테스트는 변경하지 않는다.
- 완료 전 변경한 테스트를 아래 규칙으로 다시 확인한다.

## 테스트 유형

- 단위 테스트: Spring Context 없이 `new`로 생성
- 슬라이스 테스트: 일부 Spring Context만 로딩
- 통합 테스트: `@SpringBootTest`, 실제 Repository, PostgreSQL
- E2E 테스트: `RANDOM_PORT`, 실제 HTTP 호출

## 계층별 전략

- Domain: Spring과 DB 없는 단위 테스트
- Service: 실제 Repository와 PostgreSQL을 사용하는 통합 테스트
- Repository: 복잡한 Query만 통합 테스트
- Controller: `@WebMvcTest`와 Service `@MockBean`
- Infrastructure: Adapter 단위 통합 테스트
- E2E: 도메인마다 1~3개의 핵심 성공 경로

## Repository 테스트 대상

Spring Data JPA 기본 메서드는 테스트하지 않는다. 다음 항목만 테스트한다.

- 직접 작성한 JPQL 또는 네이티브 쿼리
- QueryDSL 동적 쿼리 조건 조합
- fetch join과 페이징 조합
- cascade와 orphanRemoval
- 유니크 제약
- 낙관적 락

조회 전 `em.flush()`와 `em.clear()`를 호출한다.

## Controller

- 요청 검증, 상태 코드, 직렬화, 예외 처리, 인가를 검증한다.
- 비즈니스 로직 분기는 검증하지 않는다.

## 외부 시스템

- 외부 시스템은 Port 인터페이스로 감싼다.
- 반환값이나 예외 주입은 Mock을 사용한다.
- 호출 간 상태가 이어지거나 설정이 반복되면 Fake를 사용한다.
- Fake는 테스트 소스에 두고 `Fake` 접두사를 사용한다.
- `verify`는 호출 자체가 요구사항일 때만 사용한다.
- 그 외에는 결과를 `assertThat`으로 검증한다.
- 실제 연동은 Infrastructure 통합 테스트에서 검증한다.

## DB 환경과 격리

- 통합 테스트는 PostgreSQL을 사용하고 H2를 사용하지 않는다.
- Testcontainers PostgreSQL 컨테이너는 `static`으로 공유한다.
- 일반적인 Service 통합 테스트는 `@Transactional`과 Rollback을 사용한다.

다음 테스트는 Transactional Rollback 대신 `DatabaseCleaner`를 사용한다.

- `@TransactionalEventListener(AFTER_COMMIT)`
- `REQUIRES_NEW`
- `@Async`
- 스케줄러 또는 별도 스레드의 DB 접근
- `RANDOM_PORT`와 실제 HTTP 호출
- 트랜잭션 종료 이후 동작 검증

추가 규칙:

- 도메인 이벤트나 별도 스레드가 추가되면 TRUNCATE 방식으로 전환한다.
- `DatabaseCleaner`는 `TRUNCATE ... RESTART IDENTITY CASCADE`를 사용한다.
- 정리는 `@BeforeEach`에서 수행한다.
- `deleteAll()`을 사용하지 않는다.
- DB 초기화를 위해 `@DirtiesContext`를 사용하지 않는다.

## 통합 테스트 상위 클래스

- 통합 테스트는 `IntegrationTestSupport`를 상속한다.
- 공통 Context 설정, 프로파일, `@MockBean`, DB 정리를 상위 클래스에서 관리한다.
- 개별 테스트 클래스에 별도 Context 설정을 추가하지 않는다.

## 네이밍과 구조

- 테스트 클래스는 영문 이름과 `Test` 접미사를 사용한다.
- 테스트 유형별 접미사는 사용하지 않는다.
- 테스트 메서드는 `메서드명_상황_결과` 형식의 영문으로 작성한다.
- `@DisplayName`에는 같은 의미를 한글로 작성한다.
- 메서드명과 `@DisplayName`은 함께 변경한다.
- `JavaCompile`과 `Test`에 UTF-8 인코딩을 설정한다.
- 테스트 패키지는 운영 코드 패키지 구조를 미러링한다.
- Given/When/Then 주석을 사용한다.
- 필요한 경우 `When & Then`으로 묶는다.

## 테스트 제외 대상

- getter와 setter
- `toString()`
- Service를 그대로 호출하는 로직 없는 Controller
- 라이브러리 자체 동작
- 로직 없는 DTO 변환
- 단순 JPA 기본 동작
