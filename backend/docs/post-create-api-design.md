# 게시물 생성 API 설계

- 상태: Implemented and verified locally
- 관련 이슈: [#117 게시물 저장 API 구현](https://github.com/woowacourse-teams/2026-Chalkak/issues/117)
- 구현 브랜치: `be/feature/#117-post-create-api`

> 아래 HTTP 예시는 구현된 API 계약이다. 컨트롤러, 서비스, 영속성 계층의
> 테스트와 전체 Gradle 검사를 통해 로컬에서 검증했다.

## 1. 목적

인증된 사용자가 주제와 S3에 올린 사진을 연결해 게시물을 생성한다.
클라이언트는 서버가 발급한 `photoUploadId`만 전달하며, bucket이나 storage
key는 지정하지 않는다. 서버는 staging 객체를 확인하고 final key를 직접
유도하여 `Photo`와 `Post`를 하나의 트랜잭션으로 저장한다.

## 2. API 계약

### 2.1 요청

```http
POST /api/v1/posts
X-User-Id: {userId}
Content-Type: application/json

{
  "topicId": "0198f6c1-62ba-7d30-8b12-0f733b6570b2",
  "photoUploadId": "0198f6c1-62ba-7d30-8b12-0f733b6570d4",
  "title": "오늘의 기록"
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `topicId` | UUID | 예 | 게시물을 등록할 주제 ID |
| `photoUploadId` | UUID | 예 | staging에 업로드한 포스트 이미지 ID |
| `title` | String | 아니요 | 최대 10자. 생략, `null`, 빈 문자열, 공백 문자열은 제목 없음으로 처리 |

작성자 ID, bucket, prefix, staging key, final key는 본문으로 받지 않는다.
`title`은 API 계층에서 최대 길이를 검증하고 도메인에서도 같은 불변식을
방어한다. 빈 문자열과 공백 문자열은 `null`로 정규화하여 저장한다.

길이는 UTF-16 code unit이 아니라 **code point로 센다.** 이모지 한 글자는
code unit 두 칸을 쓰므로 `String.length()`로 재면 사용자가 입력한 글자 수보다
길게 계산된다. `posts.title`도 문자 수를 세는 `VARCHAR(10)`이므로 이 기준이
DB 컬럼과 일치한다. 따라서 `📸` 10개짜리 제목은 허용한다. 두 계층이 어긋나지
않도록 `Post.MAX_TITLE_LENGTH` 상수를 공유한다.

### 2.2 성공 응답

```http
201 Created
Content-Type: application/json

{
  "postId": "0198f6c1-62ba-7d30-8b12-0f733b6570d5",
  "moderationStatus": "VALIDATING"
}
```

새 게시물은 이미지 처리와 검수가 끝나지 않은 `VALIDATING` 상태로 시작한다.
별도 조회 URI 계약이 없으므로 `Location` header는 반환하지 않는다.

### 2.3 실패 응답

오류 본문은 공통 `ErrorResponse`의 `errorCode`, `message` 형식을 사용한다.
`message`는 클라이언트가 사용자에게 그대로 보여주는 문구이므로 제약에 작성한
문구를 필드명 접두사 없이 전달한다. 분기는 `errorCode`로 한다.

| HTTP 상태 | 조건 | `errorCode` |
| --- | --- | --- |
| `400 Bad Request` | 본문 형식 오류, 10자 초과 제목, 참여 기간이 아닌 주제 | `BUSINESS_ERROR` |
| `400 Bad Request` | 같은 사용자의 활성 주제 게시물 중복, 이미 사용된 업로드 중복 | `BUSINESS_ERROR` |
| `401 Unauthorized` | `X-User-Id`가 없거나 UUID 형식이 아님 | `UNAUTHORIZED` |
| `404 Not Found` | 작성 가능한 회원, 주제 또는 staging 업로드를 찾을 수 없음 | `BUSINESS_ERROR` |

현재 공통 예외 계약에 없는 `409 Conflict`나 `422 Unprocessable Entity`는
이 API에서 새로 도입하지 않는다.

## 3. 작성 조건

### 3.1 사용자

작성자는 soft-delete되지 않았고 상태가 `ACTIVE`여야 한다. `BANNED` 상태이거나
삭제된 사용자는 게시물을 만들 수 없다.

### 3.2 주제

주제는 soft-delete되지 않았고 요청 처리 시점의 단계가 `OPEN`이어야 한다.
참여 가능 구간은 다음 반개구간을 사용한다.

```text
startsAt <= now < endsAt
```

따라서 시작 시각에는 작성할 수 있고 종료 시각부터는 작성할 수 없다.

## 4. S3 키 계약

`photoUploadId`가 `0198f6c1-62ba-7d30-8b12-0f733b6570d4`일 때 키는 다음
문법으로 유도한다.

```text
staging         : chalkak/staging/{environment}/posts/{photoUploadId}.webp
final original  : chalkak/posts/{environment}/original/{photoUploadId}.webp
future thumbnail: chalkak/posts/{environment}/thumbnail/{photoUploadId}.webp
```

- 포스트 이미지는 WebP 전용이므로 확장자는 `.webp`다.
- root prefix는 `S3_PREFIX`이며 기본값은 `chalkak`이다.
- `{environment}`는 백엔드의 `chalkak.image.environment` 설정을 사용한다.
- 서버는 staging key로 `HeadObject`를 호출해 업로드 완료 여부를 확인한다.
- `Photo.originalStorageKey`에는 서버가 유도한 final original key를 저장한다.
- 아직 처리되지 않은 thumbnail key는 `null`, metadata는 빈 객체로 시작한다.
- 클라이언트가 임의의 storage key나 environment를 주입할 수 없다.

이미지 변환, thumbnail 생성, staging 삭제와 처리 완료 콜백은 이 이슈의
범위가 아니며 `post-image-pipeline-design.md`에서 이어진다.

## 5. 저장과 중복 처리

서비스는 다음 작업을 하나의 쓰기 트랜잭션에서 수행한다.

```text
1. 사용자 조회와 활성 여부 검증 (`User.isActive()`)
2. 삭제되지 않은 주제 조회와 OPEN 단계 검증
3. 활성 사용자·주제 게시물 중복 사전 검사
4. staging 객체 존재 확인
5. final original key 유도와 `photos` 원본 키 중복 사전 검사
6. Photo와 VALIDATING Post 생성
7. Post 저장과 Photo cascade 저장
8. postId와 moderationStatus 반환
```

중간 단계가 실패하면 `Photo`와 `Post`를 모두 rollback하여 orphan `Photo`가
남지 않게 한다. 사전 중복 검사는 명확한 오류 응답을 위한 것이며 동시 요청을
완전히 막지 못한다. 최종 일관성은 DB의 활성 `(user_id, topic_id)` 유니크
인덱스와 photo/storage key 유니크 제약으로 보장하고, 알려진 unique violation은
공통 `400 BUSINESS_ERROR`로 변환한다.

## 6. 조회 가시성

기존 게시물 목록과 상세 조회는 soft-delete되지 않은 `APPROVED` 게시물만
반환한다. 생성 직후의 `VALIDATING` 게시물은 final 객체가 준비되지 않았으므로
공개 조회에 노출하지 않는다. 후속 이미지 처리와 검수 흐름이 상태를 변경한
뒤에만 공개할 수 있다.

## 7. 인증과 Swagger

게시물 생성은 임시 `X-User-Id` 인증을 사용하는 동안 `!prod` 프로필에서만
등록한다. 생성 전용 컨트롤러를 기존 공개 GET 컨트롤러와 분리하여 운영
환경에서도 게시물 목록·상세 조회가 사라지지 않게 한다. Spring Security를
도입하면 임시 프로필 제한을 제거한다.

Swagger는 `springdoc-openapi` 3.1.0과 `*ApiDocs` interface 패턴을 따른다.

- 생성 API는 `PostCreationApiDocs`에 201, 400, 401, 404 응답을 기술한다.
- `X-User-Id`는 `userIdHeader` security scheme으로 표시한다.
- `@LoginUser`로 주입하는 서버 내부 인증 파라미터는 Swagger에서 숨긴다.
- 생성 API는 로컬·개발 환경의 `user-api` 그룹에서 확인한다.
- `prod`에서는 생성 컨트롤러가 등록되지 않으며 Swagger UI와 API JSON도
  비활성화된다.

## 8. 보안 제한

이 문서를 쓸 당시에는 업로드 발급 이력이 없어 `photoUploadId`를 소유자가
연결되지 않은 **unowned bearer capability**로 취급했다. `post_image_uploads`
claim 테이블이 들어오면서 이 제한은 해소되었다. 서버가 `user_id`를 대조하고
`claimed_at`을 원자적으로 소비한다. 자세한 계약은
`post-image-pipeline-design.md`를 따른다.

## 9. 제외 범위

이 이슈의 제외 범위였던 presigned URL 발급, 업로드 claim 테이블, 이미지
변환 Lambda, 처리 완료 콜백은 `post-image-pipeline-design.md`에서 다룬다.
moderation 완료 처리와 클라이언트 구현은 여전히 후속 범위다.
