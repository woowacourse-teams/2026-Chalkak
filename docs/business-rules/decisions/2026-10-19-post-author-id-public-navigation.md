# SIGN-005·POST-008 작성자 ID로 사인과 공개 사진 목록 연결

- 결정일: 2026-10-19
- 상태: 확정
- 변경 전: 피드·상세는 현재 사인 이미지를 제공하지만 작성자를 식별해 다른 공개 사진으로 이동할 ID는 제공하지 않았다. SIGN-005는 작성자 식별·프로필 이동 정보를 제공하지 않는 정책이었다.
- 변경 후: 피드의 각 게시물과 게시물 상세에 실제 작성자의 회원 ID인 `authorId`를 추가한다. 피드는 로그인 여부와 정렬 방식에 관계없이 항상 제공한다. 앱은 사인 선택 시 이 ID를 `GET /api/v1/users/{userId}/posts`에 사용하며 목록의 사진을 선택하면 해당 게시물 ID로 기존 상세를 조회한다. 이메일 등 추가 회원 정보는 노출하지 않는다.
- 변경 이유: 사인 이미지 URL은 교체될 수 있고 진입 게시물은 삭제될 수 있다. 기존 게시물의 작성자 관계에 있는 고정 회원 ID를 사용하면 이 변화와 무관하게 같은 작성자의 공개 사진 목록으로 연결할 수 있다. 진입 게시물 ID로 작성자를 다시 찾는 방식보다 앱의 연결 기준이 명확하다. 로그인한 피드에서만 ID를 제공하는 대안과 비교해, 항상 제공하는 일정한 응답을 사용자가 선택했다. 비로그인 사용자도 여러 게시물이 같은 작성자의 것인지 식별할 수 있게 되는 정책 변경임을 합의했다.
- 영향 범위: `GET /api/v1/posts`의 `posts[].authorId`, `GET /api/v1/posts/{postId}`의 `authorId`, 관련 테스트·Swagger·규칙·연동 안내. 기존 필드와 조회 조건·정렬·페이지·randomSeed·좋아요·본인 여부, 공개 범위·오류를 유지하는 응답 필드 추가이므로 v1을 유지한다. Android·iOS 화면 구현과 새 프로필 API는 포함하지 않는다.
- 관련 규칙: SIGN-005, POST-007, POST-008, POST-012
- 관련 이슈·PR: [상위 #679](https://github.com/woowacourse-teams/2026-Chalkak/issues/679), [작성자 ID #682](https://github.com/woowacourse-teams/2026-Chalkak/issues/682), 선행 [#680](https://github.com/woowacourse-teams/2026-Chalkak/issues/680)·[#681](https://github.com/woowacourse-teams/2026-Chalkak/issues/681)

피드는 비로그인으로 볼 수 있지만 작성자별 공개 사진 목록과 상세 조회는 로그인해야 한다. 공개 여부는 각 요청 시점의 승인·삭제·탈퇴 조건으로 판단한다. 작성자가 없거나 탈퇴하면 목록은 404이며, 진입 게시물만 삭제된 경우 작성자가 활성 상태라면 저장한 authorId로 목록 조회를 계속할 수 있다. 공개 사진이 하나도 남지 않으면 빈 목록을 반환한다.

## 2026-10-20 리뷰에서 확인한 시간 정보 노출

[PR #714 리뷰](https://github.com/woowacourse-teams/2026-Chalkak/pull/714#discussion_r4323304349)에서 UUIDv7 회원 ID에는 생성 시각 정보가 포함된다는 점을 확인했다. `User.id`는 데이터베이스의 `uuidv7()` 기본값으로 생성되므로 이 형식의 `authorId`를 받으면 ID 생성 시각을 추출해 가입 시점을 추정할 수 있다. 추출 시각이 정확한 가입 완료 시각과 일치함을 보장하지는 않는다. 이 영향은 10월 19일의 최초 합의에는 포함되지 않았으며, 리뷰 설명 후 사용자가 10월 20일에 추가로 허용했다.

현재 ID를 유지하고 노출 가능성을 명시하는 방법, 비로그인 피드에서 ID를 제외하는 방법, 공개용 랜덤 UUID를 따로 두는 방법을 비교했다. 사용자는 현재의 작성자 ID와 비로그인 피드의 일정한 응답을 유지하면서 시간 정보 노출 가능성을 SIGN-005에 명시하는 안을 선택했다. 공개용 식별자나 마이그레이션은 추가하지 않는다. 생성 시각 노출을 숨긴 식별자가 필요해지면 이 선택을 다시 검토한다.

[상세 응답 문서 리뷰](https://github.com/woowacourse-teams/2026-Chalkak/pull/714#discussion_r4323304356)도 반영해 POST-008의 적용 범위와 예외에 상세의 `authorId` 제공을 명시했다. 이미 합의·구현한 응답의 문서 누락 보완이며, 상세 조회의 로그인 조건과 API 동작은 변경하지 않는다.

근거: [PostgreSQL UUID 함수](https://www.postgresql.org/docs/18/functions-uuid.html), [UUIDv7 형식(RFC 9562)](https://www.rfc-editor.org/rfc/rfc9562.html#section-5.7).
