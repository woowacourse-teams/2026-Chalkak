# 작성자의 공개 사진 이어보기

로그인한 회원은 작성자 ID로 공개 사진을 기본 20개씩 이어서 조회하고, 사진을 선택하면 기존 게시물 상세를 조회한다. 최근 사진 조회는 [#680](https://github.com/woowacourse-teams/2026-Chalkak/issues/680), 페이지 번호 이어보기는 [#681](https://github.com/woowacourse-teams/2026-Chalkak/issues/681) 범위이며, 공개 조건은 [POST-007·POST-012](../rules/post.md)을 따른다.

## 목록 호출과 사진 표시

```http
GET /api/v1/users/{userId}/posts?page=1&pageSize=1
Authorization: Bearer {accessToken}
```

`userId`는 조회자와 구분되는 사진 작성자의 UUID다. 로그인한 본인을 지정해도 공개 승인된 사진만 조회한다. 주제 날짜 최신순으로 조회하며 `page`는 1부터 시작하고 생략하면 1페이지다. `pageSize`를 생략하면 20개, 지정하면 1~100개를 받는다. 예를 들어 `GET /api/v1/users/{userId}/posts?page=1&pageSize=100`으로 최대 100개를 요청한다. `page`가 1보다 작거나 `pageSize`가 범위 밖이거나 숫자가 아닌 값은 `400`이다.

아래는 `pageSize=1`로 한 장을 받았고, 더 과거의 사진이 있는 경우의 응답 예시다.

```json
{
  "currentPage": 1,
  "pageSize": 1,
  "hasNext": true,
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

## 다음 페이지와 새로고침

`hasNext`가 `true`이면 같은 `pageSize`와 응답의 `currentPage + 1`을 다음 요청의 `page`로 전달한다.

```http
GET /api/v1/users/{userId}/posts?page=2&pageSize=1
Authorization: Bearer {accessToken}
```

- 다음 페이지가 없으면 `hasNext=false`다. 빈 목록도 동일하며 이때 추가 요청을 멈춘다. `currentPage`·`pageSize`는 빈 목록과 범위를 벗어난 페이지에서도 요청한 값을 유지한다.
- 첫 진입 시 기본값은 `page=1&pageSize=20`이며 `pageSize`는 1~100개로 지정할 수 있다. 목록을 이어 붙이는 동안에는 같은 크기를 사용한다.
- 서버는 각 요청 시점의 승인·삭제 상태를 반영해 페이지를 조회한다. 조회 도중 앞쪽 사진이 승인되거나 삭제되면 같은 사진이 다시 나오거나 아직 보지 않은 사진을 건너뛸 수 있다. 중복·누락 방지나 목록 스냅샷은 제공하지 않는다.
- 새로고침·재진입은 `page=1`부터 목록을 다시 구성한다. 앱에서 중복 표시를 막으려면 게시물 `id`로 중복을 제거할 수 있지만, 건너뛴 사진까지 복구하는 기능은 아니다.
- 커서 입력과 `nextCursor` 응답은 제공하지 않는다.

## 빈 목록과 오류

| 응답 | 의미와 앱 처리 |
| --- | --- |
| `200`, `posts: []` | 공개 사진이 없거나 요청한 페이지에 남은 사진이 없음. 첫 페이지라면 빈 화면 안내를, 이어보기라면 목록 종료를 처리한다. |
| `400`, `BUSINESS_ERROR` | 작성자 UUID, `page`, `pageSize`가 잘못됨. 페이지 번호는 1 이상, 조회 크기는 1~100인지 확인한다. |
| `401`, `UNAUTHORIZED` | 비로그인·유효하지 않은 인증·탈퇴한 조회자. 기존 인증 갱신 정책에 따라 처리한다. |
| `403`, `FORBIDDEN` | 일반 회원 API에 접근할 수 없는 토큰. 정지 회원이라는 이유로 발생하는 응답은 아니다. |
| `404`, `BUSINESS_ERROR` | 작성자가 없거나 탈퇴함. 더 이상 볼 수 없는 작성자로 안내한다. |

목록 조회 후 사진이 삭제됐다면 상세 조회에서 기존 `404`가 발생할 수 있다. 오류 코드·HTTP 상태와 함께 어떤 요청이 실패했는지 구분한다. 정지된 조회자와 작성자는 탈퇴 여부 및 기존 공개 조건에 따라 조회할 수 있다.

## 후속 연동 범위

피드·상세 응답의 `authorId`와 사인 클릭 연결은 [#682](https://github.com/woowacourse-teams/2026-Chalkak/issues/682) 범위다. 해당 응답 필드를 현재 API가 이미 제공한다고 가정하지 않는다.
