---
name: naming-conventions
description: API JSON 필드·Parameter·URI, Java 변수·필드·매개변수, DB 식별자 또는 ErrorCode 이름을 생성·변경·리뷰할 때 사용한다. 테스트 클래스·메서드와 패키지 이름에는 사용하지 않는다.
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
