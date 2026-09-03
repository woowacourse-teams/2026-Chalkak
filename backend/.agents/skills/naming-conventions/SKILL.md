---
name: naming-conventions
description: API JSON 필드·Parameter·URI, Java 변수·필드·매개변수·메서드, DB 식별자 또는 ErrorCode 이름을 생성·변경·리뷰할 때 사용한다. 테스트 클래스·메서드와 패키지 이름에는 사용하지 않는다.
---

# 네이밍 컨벤션

## 이름 규칙

| 대상 | 형식 | 예시 |
| --- | --- | --- |
| Request·Response JSON 필드 | `lowerCamelCase` | `originalImageUrl` |
| Path Variable 이름과 URI 자리표시자 | `lowerCamelCase` | `postId`, `{postId}` |
| Query Parameter | `lowerCamelCase` | `pageSize` |
| Multipart·Form 필드 | `lowerCamelCase` | `originalImage` |
| Java 변수·필드·매개변수 | `lowerCamelCase` | `originalStorageKey` |
| URI의 고정 경로 | 소문자 `kebab-case` | `/api/v1/photo-albums` |
| DB 테이블·컬럼·인덱스·제약조건 | `snake_case` | `original_storage_key` |
| `ErrorCode` enum 상수 | `UPPER_SNAKE_CASE` | `BUSINESS_ERROR` |

## 메서드 네이밍

- 메서드명은 동사로 시작한다.
- 동일한 의미에는 동일한 동사를 사용한다.

### 조회

- `getXxx`: 대상이 반드시 존재하며, 없으면 예외를 발생시킨다.
- `findXxx`: 대상이 없을 수 있으며 `Optional` 또는 nullable을 반환한다.
- 목록 조회도 단건 조회와 같은 의미 기준을 사용한다.

### 생성

- 신규 객체나 리소스 생성은 `createXxx`를 사용한다.
- `buildXxx`는 Builder 또는 객체 조립 의미가 명확할 때만 사용한다.
- `generateXxx`는 랜덤값, 토큰, 코드 등의 값 생성에 사용한다.

### 수정

- 일반적인 상태 수정은 `updateXxx`를 사용한다.
- 도메인 상태 전환에는 의미가 드러나는 구체적인 동사를 사용할 수 있다.
- 단순 수정 의미의 `changeXxx`, `modifyXxx`는 사용하지 않는다.

### 삭제

- 삭제는 `deleteXxx`를 사용한다.
- `removeXxx`는 컬렉션 요소 제거처럼 삭제와 의미가 다를 때만 사용한다.

### 검증

- 입력값, 상태, 비즈니스 규칙 검증은 `validateXxx`를 사용한다.
- `verifyXxx`는 인증이나 진위 확인에만 사용한다.

### 변환

- 인스턴스를 다른 타입으로 변환할 때는 `toXxx()`를 사용한다.
- 다른 타입으로부터 생성하는 정적 메서드는 `fromXxx()`를 사용한다.
- 양쪽 타입에 변환 책임을 두지 않을 때는 별도 Converter의 `convert()`를 사용한다.

### Boolean 반환

- 긍정형으로 작성한다.
- 상태 여부는 `isXxx`를 사용한다.
- 보유 또는 포함 여부는 `hasXxx`를 사용한다.
- 행위 가능 여부는 `canXxx`를 사용한다.
- 저장소 존재 여부는 `existsXxx`를 사용한다.

## API 직렬화

- 새 API는 Jackson의 기본 `lowerCamelCase` 직렬화를 사용한다.
- 새 API에 `snake_case` 변환 설정이나 불필요한 `@JsonProperty`를 추가하지 않는다.
- 레거시 API나 외부 시스템 계약을 유지할 때만 명시적 이름 매핑을 사용한다.

## 기존 이름 보호

- 이 규칙은 새로 만들거나 직접 변경하는 이름에 적용한다. 관련 없는 기존 이름을 일괄 변경하지 않는다.
- 기존 JSON 필드, Parameter, URI 또는 응답에 노출되는 에러 코드 이름은 컨벤션 적용만을 위해 변경하지 않는다.
- 외부 API에 노출된 이름을 변경해야 한다면 Breaking Change 여부를 확인하고 `api-versioning` 컨벤션을 적용한다.
- 기존 DB 식별자는 컨벤션 적용만을 위해 변경하지 않는다.

## 완료 전 확인

- 변경한 이름마다 대상에 맞는 형식을 적용했는지 확인한다.
- JSON 필드명과 Parameter 이름에 `snake_case`를 사용하지 않았는지 확인한다.
- URI 고정 경로와 Path Variable 자리표시자의 형식을 혼동하지 않았는지 확인한다.
- 새 `ErrorCode` enum 상수가 `UPPER_SNAKE_CASE`인지 확인한다.
- 관련 없는 기존 이름을 함께 변경하지 않았는지 확인한다.
- 외부 API 이름 변경에 `api-versioning` 컨벤션을 적용했는지 확인한다.
