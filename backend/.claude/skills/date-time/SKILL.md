---
name: date-time
description: backend/src/main/java에서 날짜·시간의 입력·조회·검증·변환·응답 처리를 생성·수정·리뷰할 때 사용한다. 테스트 코드와 client 코드에는 사용하지 않는다.
---

# 날짜와 시간 컨벤션

- 클라이언트가 조회 날짜를 `LocalDate`로 전달한다.
- 서버는 전달받은 `LocalDate`를 변환하지 않고 그대로 조회한다.
- Service는 미래 날짜를 검증할 때 KST의 현재 날짜를 사용한다.
- DTO의 시각 값은 `Instant`로 반환하며 서버에서 KST로 변환하지 않는다.
