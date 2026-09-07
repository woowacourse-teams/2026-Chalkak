---
name: request-dto
description: backend/src/main/java의 Request DTO를 생성·수정·리뷰할 때 사용한다. Response DTO와 테스트 코드에는 사용하지 않는다.
---

# Request DTO 컨벤션

- Request DTO는 기본적으로 Java `record`로 작성한다.
- `record` 이외의 구현이 필요한 경우, 이유를 설명하고 개발자에게 확인한 뒤 적용한다.

- 요청 패키지의 `*Request` record 여부는 `./gradlew architectureTest`로 검사한다. 개발자 확인 없이 예외 목록을 추가하거나 검사를 끄지 않는다.
