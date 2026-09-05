---
name: swagger-docs
description: backend에서 springdoc-openapi 설정, Swagger/OpenAPI 어노테이션, API 문서 인터페이스 또는 생성된 OpenAPI 계약을 생성·수정·리뷰할 때 사용한다. 실제 API 동작만 변경하고 문서에는 영향이 없으면 사용하지 않는다.
---

# Swagger 문서화 컨벤션

## 기본 원칙

- `org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.0`을 사용하며, 명시적인 요청 없이 버전을 변경하지 않는다.
- springdoc의 자동 추론을 우선하고 코드만으로 알 수 없는 계약만 보완한다.
- Swagger는 개별 API의 요청·응답 계약을 설명한다. 여러 호출의 순서나 상태 전이는 별도 흐름 문서에서 관리한다.
- 문서와 설정만 변경하는 작업에는 `tdd-workflow`를 적용하지 않는다. 실제 API 동작도 변경한다면 그 동작 변경에만 TDD를 적용한다.

## 작업 흐름

1. 대상 Controller, DTO, 예외와 기존 OpenAPI 설정을 읽어 실제 API 계약을 확인한다.
2. 문서·설정만 바뀌는지 실제 API 동작도 바뀌는지 구분하여 TDD 적용 여부를 결정한다.
3. 문서 인터페이스와 필요한 DTO에만 최소한의 Swagger 어노테이션을 작성한다.
4. 사용자·운영자·내부 API 그룹과 프로필별 활성화 범위를 유지한다.
5. 컴파일한 뒤 생성된 OpenAPI 문서가 실제 계약과 일치하는지 검증한다.

## 코드 배치

- 엔드포인트 문서는 `{domain}/api/v{n}/docs/{Domain}ApiDocs.java` 인터페이스에 둔다.
- Controller는 대응하는 문서 인터페이스를 구현한다.
- 요청 매핑과 실제 요청 처리 어노테이션은 Controller에 유지하고, Swagger 어노테이션은 문서 인터페이스에 둔다.
- DTO의 `@Schema`는 DTO에서 분리할 수 없으므로 필요한 필드에만 직접 작성한다.
- 공통 OpenAPI 정보, 보안 스키마, API 그룹 설정은 기존 `config/OpenApiConfig.java`에서 관리한다.

## 작성 기준

- 모든 엔드포인트에 짧고 행위가 분명한 `@Operation(summary)`를 작성한다.
- 다음 정보처럼 자동 추론할 수 없거나 실제 사용에 필요한 내용만 설명한다.
  - nullable 여부와 코드에서 적용되는 기본값
  - 이름만으로 알기 어려운 필드 의미
  - `randomSeed` 재사용 같은 호출 규칙
  - 해당 API에서 발생하는 비즈니스 오류
  - 계약 이해에 필요한 현실적인 예시
- 타입, Bean Validation 제약, 자동 노출되는 enum 값, 반환 타입으로 충분히 알 수 있는 성공 응답은 중복 작성하지 않는다.
- 의미가 명확한 필드에 설명을 반복하지 않는다.
- 오류 응답을 문서화할 때 실제 발생 가능한 상태 코드와 `ErrorResponse` 스키마가 구현과 일치하는지 확인한다.
- 예시에 토큰, 사용자 정보 또는 운영 데이터를 넣지 않는다.

## API 그룹과 환경

- 사용자 그룹은 `/api/v1/**`를 포함하고 `/api/v1/admin/**`를 제외한다.
- 운영자 그룹은 `/api/v1/admin/**`만 포함한다.
- 기존 내부 API 그룹이 있으면 사용자·운영자 그룹에 섞이지 않도록 유지한다.
- `local`, `dev`에서는 Swagger UI와 OpenAPI 문서를 활성화한다.
- `prod`에서는 `springdoc.api-docs.enabled`와 `springdoc.swagger-ui.enabled`를 모두 `false`로 유지한다.
- Swagger 접근을 위해 운영 보안 설정이나 인증 예외 범위를 넓히지 않는다.

## 검증

- 문서 전용 변경을 Red-Green-Refactor로 진행하거나 라이브러리 자체 동작을 단위 테스트하지 않는다.
- 컴파일로 Controller와 문서 인터페이스의 메서드 계약이 일치하는지 확인한다.
- 실행 가능한 비운영 프로필에서 `/v3/api-docs`와 변경한 그룹 문서가 생성되는지 확인한다.
- 생성된 스키마에서 경로, 파라미터, 필수·nullable, 기본값, 응답과 오류 계약이 실제 코드와 일치하는지 확인한다.
- 사용자 그룹에 운영자 API가 포함되지 않고 운영 환경에서 UI와 문서가 비활성화되는지 확인한다.
- 회귀 위험이 커서 생성 결과 테스트가 필요하다면 검증 테스트로 작성하되 TDD 작업 순서를 강제하지 않는다.

## 공식 근거

- [springdoc-openapi 공식 문서](https://springdoc.org/)
- [springdoc-openapi 설정 속성](https://springdoc.org/properties)
