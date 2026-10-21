# AIREVIEW-003, AIREVIEW-005 AI 리뷰 조회의 요청 전 상태

- 결정일: 2026-10-21
- 상태: 확정
- 변경 전: 본인의 공개된 게시물이라도 AI 리뷰를 요청한 적이 없으면 조회가 `404`(찾을 수 없음)였다. 앱은 `404`를 "아직 요청하지 않은 정상 흐름"으로 다뤄야 했다.
- 변경 후: 본인의 공개된 게시물에 요청한 적이 없으면 조회가 `200`과 요청 전 상태(`NONE`)를 응답한다. 이때 `requestedAt`, `completedAt`, `result`는 `null`이다. `404`는 게시물이 없거나 공개 조건([POST-007](../rules/post.md#post-007-공개-범위))을 충족하지 않거나 작성자가 아닐 때만 응답한다. 앱에 알리는 상태는 요청 전, 진행 중, 완료, 실패 네 가지가 된다([AIREVIEW-003](../rules/ai-review.md#aireview-003-공개-범위), [AIREVIEW-005](../rules/ai-review.md#aireview-005-결과-표시)). 요청(`POST`)은 바뀌지 않는다.
- 변경 이유:
  - 앱은 게시물 상세에 들어올 때 "AI 피드백 받기"를 보여 줄지, 생성 중·결과 보기·실패 안내를 보여 줄지 정해야 한다. 요청하지 않은 것은 정상 흐름이라 오류 응답으로 알릴 이유가 없다.
  - `404`가 "게시물 문제"와 "아직 요청하지 않음" 두 뜻을 함께 가져 `message`로만 구분해야 했다. 요청 전을 `200`으로 분리해 `404`의 뜻을 게시물 문제 하나로 모은다.
  - 작성자가 아니거나 공개 조건을 충족하지 않는 경우는 리뷰 존재 여부를 숨기려고 이전처럼 `404`를 유지한다.
- 영향 범위: `GET /api/v1/posts/{postId}/ai-review`의 응답. 앱은 `404` 대신 `status`가 `NONE`인 응답으로 요청 버튼을 보여 주고, `requestedAt`이 `null`일 수 있음을 처리해야 한다([앱 연동 안내](../guides/ai-review.md)). 정지 회원의 조회 허용([USER-002](../rules/user.md#user-002-이용-정지))은 그대로다. 아직 앱에 공개하지 않은 API라 새 API 버전은 만들지 않고 `v1`에 반영한다. api-versioning 기준으로 기존 상태 코드 변경은 Breaking Change지만, 새 버전은 기존 앱의 정상 동작을 보장해야 할 때만 추가한다. 이 API는 공개 전이라 이 응답에 의존하는 기존 앱이 없다.
- 관련 규칙: AIREVIEW-003, AIREVIEW-005, POST-007, USER-002
- 관련 이슈·PR: [#721](https://github.com/woowacourse-teams/2026-Chalkak/issues/721), 선행 구현은 [#716](https://github.com/woowacourse-teams/2026-Chalkak/pull/716)
