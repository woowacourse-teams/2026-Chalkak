# Backend Instructions

- 작업 범위는 현재 `backend/` 디렉터리로 제한한다.
- `../client/**`는 참조를 위해 읽을 수 있지만 수정하지 않는다.
- `src/main/java/**/*.java` 작업에는 `$main-code`를 사용한다.
- `src/main/java`의 동작을 추가·변경하거나 버그를 수정할 때는 `$tdd-workflow`를 사용한다.
- springdoc-openapi 설정, Swagger/OpenAPI 어노테이션, API 문서 인터페이스 또는 생성된 OpenAPI 계약을 생성·수정·리뷰할 때는 `$swagger-docs`를 사용한다.
- Swagger/OpenAPI 문서와 설정만 변경하는 작업에는 `$tdd-workflow`를 사용하지 않는다. 실제 API 동작도 변경하면 그 동작 변경에만 `$tdd-workflow`를 사용한다.
- `src/main/java`에서 클래스·패키지를 생성·이동하거나 배치 위치를 검토할 때는 `$package-structure`도 함께 사용한다.
- API Endpoint, Controller, Request·Response 계약을 추가·변경·삭제하거나 API 버전을 검토할 때는 `$api-versioning`도 함께 사용한다.
- API JSON 필드·Parameter·URI, Java 변수·필드·매개변수, DB 식별자 또는 ErrorCode 이름을 생성·변경·리뷰할 때는 `$naming-conventions`도 함께 사용한다. 테스트 클래스·메서드와 패키지 이름에는 사용하지 않는다.
- 예외를 정의·발생·변환하거나 예외 클래스, 에러 코드, 예외 응답, 예외 처리기를 변경할 때는 `$exception-handling`도 함께 사용한다.
- `src/test/java/**/*.java` 작업에는 `$test-code`를 사용한다.
- 운영 코드와 테스트 코드를 함께 변경하면 해당하는 모든 Skill을 사용한다.
- 불분명하거나 충돌하는 규칙은 임의로 해석하지 않고 사용자에게 확인한다.
- 명시적인 요청 없이 컨벤션 자체를 변경하지 않는다.
