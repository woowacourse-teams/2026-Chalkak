# 작성자의 공개 사진 이어보기

피드·상세에서 사인을 선택하면 해당 게시물의 `authorId`로 작성자의 공개 사진 목록에 진입한다. 로그인한 회원은 공개 사진을 기본 20개씩 이어서 조회하고, 사진을 선택하면 기존 게시물 상세를 조회한다. 최근 사진 조회는 [#680](https://github.com/woowacourse-teams/2026-Chalkak/issues/680), 페이지 번호 이어보기는 [#681](https://github.com/woowacourse-teams/2026-Chalkak/issues/681) 범위다. 피드·상세의 작성자 ID 제공은 [#682](https://github.com/woowacourse-teams/2026-Chalkak/issues/682) 범위이며, 공개 조건은 [POST-007·POST-012](../rules/post.md)을 따른다.

## 사인 선택부터 작성자 목록 진입까지

1. `GET /api/v1/posts?topicDate=2026-10-19`로 피드를 조회하거나, 로그인한 상태에서 `GET /api/v1/posts/{postId}`로 상세를 조회한다.
2. 사인을 선택한 게시물의 `authorId`를 보관한다. 피드는 `posts[].authorId`, 상세는 최상위 `authorId`이며 사진 작성자의 UUID다. 로그인한 조회자의 ID, 게시물 `id`, 사인 이미지 URL과 구분한다.
3. 비로그인 상태라면 먼저 로그인한 뒤 보관한 `authorId`로 목록을 연다. 피드는 비로그인도 `authorId`를 받지만 작성자 사진 목록과 상세는 로그인해야 한다. 목록 요청이 `401`이면 기존 인증 갱신·로그인 흐름을 따른다.
4. `GET /api/v1/users/{authorId}/posts?page=1&pageSize=20`에 회원 access token을 전달해 목록을 조회한다. 이 URL의 `{authorId}` 자리는 API 정의의 `{userId}`이며, 선택한 사진 작성자의 ID로 채운다.
5. 목록에서 사진을 선택하면 해당 항목의 `id`로 `GET /api/v1/posts/{postId}`를 호출한다. 다음 페이지는 아래 이어보기 계약을 사용한다.

아래는 연결에 필요한 필드만 발췌한 예시다. 기존 피드·상세 필드와 피드의 페이지·정렬·randomSeed 계약은 그대로 유지한다.

```json
{
  "posts": [
    {
      "id": "0198f6c1-62ba-7d30-8b12-0f733b6570d4",
      "authorId": "0198f6c1-62ba-7d30-8b12-0f733b6570a1",
      "signatureOriginalImageUrl": "https://cdn.example.com/signatures/current.png"
    }
  ]
}
```

`authorId`는 피드의 로그인 여부와 최신·인기·랜덤 정렬에 관계없이 제공한다. 서로 다른 작성자가 같은 사인 이미지를 사용해도 ID로 구분하며 사인 교체 후에도 같은 ID를 사용한다. 진입 게시물만 삭제됐다면 저장한 작성자 ID로 목록을 조회할 수 있지만, 작성자가 탈퇴하면 `404`다. 목록을 조회할 때 공개 사진이 남아 있지 않으면 `200`과 빈 목록을 받는다. 이메일 등 추가 회원 정보나 별도 프로필 응답은 제공하지 않는다.

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
      "likeCount": 2,
      "isLiked": true,
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
- `likeCount`는 피드와 같은 현재 전체 좋아요 수이며, `isLiked`는 인증된 조회자 본인의 좋아요 여부다. 작성자의 좋아요 여부와 구분한다. 좋아요가 없으면 `0`·`false`이며 본인 사진의 좋아요도 같은 기준이다. 목록은 기존처럼 로그인 필수다.
- 카드에서 좋아요를 등록·취소할 때는 기존 `PUT /api/v1/posts/{postId}/likes`·`DELETE /api/v1/posts/{postId}/likes`를 사용한다. 기존 LIKE-001~003 자격·대상·중복 규칙을 따르며 정지 회원의 조회 허용이 좋아요 등록 허용을 뜻하지 않는다. 상세에서 돌아오거나 새로고침하면 목록의 개수·내 상태를 동기화한다.
- 사진을 선택하면 `GET /api/v1/posts/{postId}`에 해당 `id`를 사용한다. 사진 제목 등의 상세 정보는 이 API에서 받는다.
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

## 연동 적용 범위

백엔드는 작성자 ID와 공개 사진 목록·상세 API를 제공한다. 사인 터치 영역, 로그인 후 선택한 작성자로 복귀, 목록 화면과 스크롤·중복 표시 처리는 앱에서 연결한다. 이 설명서와 코드의 갱신이 운영 서버 배포 완료를 뜻하지 않으므로 실제 서버 적용 버전을 확인한 뒤 연동한다.

좋아요 필드 추가는 [#816](https://github.com/woowacourse-teams/2026-Chalkak/issues/816) 범위이며 Android [#813](https://github.com/woowacourse-teams/2026-Chalkak/issues/813)·iOS [#814](https://github.com/woowacourse-teams/2026-Chalkak/issues/814)에서 카드 표시·토글 및 상세와의 상태 동기화를 연결한다. 필드 추가는 기존 v1의 하위 호환 확장이며 운영 서버 반영 여부는 별도로 확인한다.
