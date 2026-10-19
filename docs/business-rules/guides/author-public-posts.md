# 작성자의 공개 사진 이어보기

로그인한 회원은 작성자 ID로 공개 사진을 기본 20개씩 이어서 조회하고, 사진을 선택하면 기존 게시물 상세를 조회한다. 최근 사진 조회는 [#680](https://github.com/woowacourse-teams/2026-Chalkak/issues/680), 커서 이어보기는 [#681](https://github.com/woowacourse-teams/2026-Chalkak/issues/681) 범위이며, 공개 조건은 [POST-007·POST-012](../rules/post.md)을 따른다.

## 목록 호출과 사진 표시

```http
GET /api/v1/users/{userId}/posts?pageSize=1
Authorization: Bearer {accessToken}
```

`userId`는 조회자와 구분되는 사진 작성자의 UUID다. 로그인한 본인을 지정해도 공개 승인된 사진만 조회한다. 주제 날짜 최신순으로 조회하며 `pageSize`를 생략하면 20개, 지정하면 1~100개를 받는다. 예를 들어 `GET /api/v1/users/{userId}/posts?pageSize=100`으로 최대 100개를 요청한다. 범위 밖이거나 숫자가 아닌 값은 `400`이다.

아래는 `pageSize=1`로 한 장을 받았고, 더 과거의 사진이 있는 경우의 응답 예시다.

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
  ],
  "hasNext": true,
  "nextCursor": "2026-10-19"
}
```

- 사진과 주제 정보를 함께 표시한다. `id`는 회원 ID가 아닌 게시물 ID다.
- 목록에서는 썸네일을 사용하고 `thumbnailImageUrl`이 `null`인 기존 사진은 원본 URL을 사용할 수 있다.
- 사진을 선택하면 `GET /api/v1/posts/{postId}`에 해당 `id`를 사용한다. 사진 제목·좋아요 등의 상세 정보는 이 API에서 받는다.
- 목록의 이미지 URL은 파일을 한꺼번에 내려받으라는 의미가 아니다. 앱에서 화면에 필요한 이미지를 로드한다.

## 다음 묶음과 새로고침

`hasNext`가 `true`이면 응답의 `nextCursor`를 다음 요청의 `cursor`로 그대로 전달한다. 아래 날짜는 예시이며 마지막으로 받은 응답의 값을 사용한다.

```http
GET /api/v1/users/{userId}/posts?cursor=2026-10-19&pageSize=1
Authorization: Bearer {accessToken}
```

- 서버는 커서 주제 날짜보다 과거의 사진만 반환한다. 커서 게시물이 삭제돼도 해당 날짜의 게시물을 다시 찾지 않고 이어서 조회한다.
- 다음 묶음이 없으면 `hasNext=false`, `nextCursor=null`이다. 빈 목록도 동일하며 이때 추가 요청을 멈춘다.
- `pageSize`는 한 번의 조회 개수이며 생략 시 20개, 지정 시 1~100개다. 다음 묶음에서도 같은 크기를 사용하는 것이 일반적인 화면 흐름이다.
- 이미 지난 날짜 구간에 뒤늦게 승인된 사진은 현재 목록 중간에 자동으로 추가되지 않는다. 커서를 제거하고 첫 묶음부터 새로고침·재진입하면 반영된다.
- 아직 조회하지 않은 과거 구간은 요청 시점의 공개 조건을 반영하므로 새로 승인된 사진이 포함되거나 삭제된 사진이 제외될 수 있다. 목록 전체를 고정한 스냅샷은 아니다.

## 빈 목록과 오류

| 응답 | 의미와 앱 처리 |
| --- | --- |
| `200`, `posts: []` | 정상 회원에게 공개 사진이 없음. 빈 화면 안내를 표시한다. |
| `400`, `BUSINESS_ERROR` | 작성자 UUID, `pageSize`, 날짜 `cursor`가 잘못됨. 커서는 `YYYY-MM-DD`이며 빈 값·잘못된 형식·존재하지 않는 날짜는 허용하지 않는다. |
| `401`, `UNAUTHORIZED` | 비로그인·유효하지 않은 인증·탈퇴한 조회자. 기존 인증 갱신 정책에 따라 처리한다. |
| `403`, `FORBIDDEN` | 일반 회원 API에 접근할 수 없는 토큰. 정지 회원이라는 이유로 발생하는 응답은 아니다. |
| `404`, `BUSINESS_ERROR` | 작성자가 없거나 탈퇴함. 더 이상 볼 수 없는 작성자로 안내한다. |

목록 조회 후 사진이 삭제됐다면 상세 조회에서 기존 `404`가 발생할 수 있다. 오류 코드·HTTP 상태와 함께 어떤 요청이 실패했는지 구분한다. 정지된 조회자와 작성자는 탈퇴 여부 및 기존 공개 조건에 따라 조회할 수 있다.

## 후속 연동 범위

피드·상세 응답의 `authorId`와 사인 클릭 연결은 [#682](https://github.com/woowacourse-teams/2026-Chalkak/issues/682) 범위다. 해당 응답 필드를 현재 API가 이미 제공한다고 가정하지 않는다.
