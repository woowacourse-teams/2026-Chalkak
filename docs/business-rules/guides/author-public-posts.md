# 작성자의 최근 공개 사진 조회

로그인한 회원은 작성자 ID로 최근 공개 사진을 기본 20개 조회하고, 사진을 선택하면 기존 게시물 상세를 조회한다. 현재 제공 범위는 [#680](https://github.com/woowacourse-teams/2026-Chalkak/issues/680)이며, 공개 조건은 [POST-007·POST-012](../rules/post.md)을 따른다.

## 목록 호출과 사진 표시

```http
GET /api/v1/users/{userId}/posts
Authorization: Bearer {accessToken}
```

`userId`는 조회자와 구분되는 사진 작성자의 UUID다. 로그인한 본인을 지정해도 공개 승인된 사진만 조회한다. 주제 날짜 최신순으로 조회하며 `pageSize`를 생략하면 20개, 지정하면 1~100개를 받는다. 예를 들어 `GET /api/v1/users/{userId}/posts?pageSize=100`으로 최대 100개를 요청한다. 범위 밖이거나 숫자가 아닌 값은 `400`이다.

```json
{
  "posts": [
    {
      "id": "0198f6c1-62ba-7d30-8b12-0f733b6570d4",
      "originalImageUrl": "https://cdn.example.com/posts/original.webp",
      "thumbnailImageUrl": "https://cdn.example.com/posts/thumbnail.webp",
      "topic": {
        "id": "0198f6c1-62ba-7d30-8b12-0f733b6570b2",
        "title": "오늘의 하늘",
        "topicDate": "2026-10-19"
      }
    }
  ]
}
```

- 사진과 주제 정보를 함께 표시한다. `id`는 회원 ID가 아닌 게시물 ID다.
- 목록에서는 썸네일을 사용하고 `thumbnailImageUrl`이 `null`인 기존 사진은 원본 URL을 사용할 수 있다.
- 사진을 선택하면 `GET /api/v1/posts/{postId}`에 해당 `id`를 사용한다. 사진 제목·좋아요 등의 상세 정보는 이 API에서 받는다.
- 목록의 이미지 URL은 파일을 한꺼번에 내려받으라는 의미가 아니다. 앱에서 화면에 필요한 이미지를 로드한다.

## 빈 목록과 오류

| 응답 | 의미와 앱 처리 |
| --- | --- |
| `200`, `posts: []` | 정상 회원에게 공개 사진이 없음. 빈 화면 안내를 표시한다. |
| `400`, `BUSINESS_ERROR` | 작성자 UUID 형식 또는 `pageSize`가 잘못됨. 호출 값을 확인한다. |
| `401`, `UNAUTHORIZED` | 비로그인·유효하지 않은 인증·탈퇴한 조회자. 기존 인증 갱신 정책에 따라 처리한다. |
| `403`, `FORBIDDEN` | 일반 회원 API에 접근할 수 없는 토큰. 정지 회원이라는 이유로 발생하는 응답은 아니다. |
| `404`, `BUSINESS_ERROR` | 작성자가 없거나 탈퇴함. 더 이상 볼 수 없는 작성자로 안내한다. |

목록 조회 후 사진이 삭제됐다면 상세 조회에서 기존 `404`가 발생할 수 있다. 오류 코드·HTTP 상태와 함께 어떤 요청이 실패했는지 구분한다. 정지된 조회자와 작성자는 탈퇴 여부 및 기존 공개 조건에 따라 조회할 수 있다.

## 후속 연동 범위

현재 요청에는 커서 입력이 없고 응답에는 `hasNext`·`nextCursor`가 없다. `pageSize`는 한 번의 조회 개수이며 다음 구간을 지정하지 않는다. 더 과거의 사진을 이어 받는 계약은 [#681](https://github.com/woowacourse-teams/2026-Chalkak/issues/681)에서 추가한다.

피드·상세 응답의 `authorId`와 사인 클릭 연결은 [#682](https://github.com/woowacourse-teams/2026-Chalkak/issues/682) 범위다. 해당 응답 필드를 현재 API가 이미 제공한다고 가정하지 않는다.
