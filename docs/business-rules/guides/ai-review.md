# 게시물 AI 리뷰 요청과 결과 조회

작성자는 본인의 공개된 게시물에 AI 리뷰를 한 번 요청하고, 처리가 끝나면 구도 점수·독특한 시각 여부·피드백을 확인한다. 요청과 조회 API는 [#689](https://github.com/woowacourse-teams/2026-Chalkak/issues/689) 범위이며, 정책은 [AIREVIEW-001~006](../rules/ai-review.md)과 [USER-002](../rules/user.md#user-002-이용-정지)를 따른다.

## 요청과 조회 흐름

1. 게시물 상세에 진입하면 먼저 `GET`으로 상태를 확인한다. `NONE`이면 "AI 피드백 받기"를, `PROCESSING`이면 생성 중을, `COMPLETED`이면 결과 보기를, `FAILED`이면 실패 안내를 보여 준다.
2. 사용자가 "AI 피드백 받기"를 누를 때만 `POST`로 요청한다. 이미 요청한 게시물에 `POST`해도 새로 만들지 않고 현재 상태를 그대로 돌려준다. 이미 끝난 리뷰에 `POST`하면 `200`과 그 상태(`COMPLETED`·`FAILED`)를 돌려준다.
3. 응답의 `status`가 `PROCESSING`이면 `GET`으로 상태를 확인한다. `COMPLETED`이면 결과를, `FAILED`이면 실패 안내를 보여 준다.
4. 이용이 정지된 회원은 `POST`가 `403`이다. 요청 버튼을 숨기거나 안내하고, `GET`은 정지 상태와 관계없이 호출할 수 있다.

## 요청

```http
POST /api/v1/posts/{postId}/ai-review
Authorization: Bearer {accessToken}
```

`postId`는 본인이 작성한 공개 게시물의 UUID다. 요청 본문은 없다. 서버는 요청을 접수만 하고 비동기로 처리하므로 응답에는 보통 결과가 없다.

접수 직후와 처리 중에는 `202`와 `PROCESSING`을 돌려준다.

```json
{
  "status": "PROCESSING",
  "requestedAt": "2026-10-20T05:00:00Z",
  "completedAt": null,
  "result": null
}
```

## 조회

```http
GET /api/v1/posts/{postId}/ai-review
Authorization: Bearer {accessToken}
```

요청 전이거나 진행 중이어도 `200`이며 응답 필드는 요청 응답과 같다. 완료되면 `result`에 결과가 담긴다.

```json
{
  "status": "COMPLETED",
  "requestedAt": "2026-10-20T05:00:00Z",
  "completedAt": "2026-10-20T05:00:21Z",
  "result": {
    "overallScore": 7.5,
    "criteria": [
      {
        "key": "COMPOSITION",
        "label": "구도",
        "score": 7.5
      }
    ],
    "distinctivePerspective": true,
    "feedback": "빛이 들어오는 방향을 따라 시선이 자연스럽게 이어지는 구도입니다."
  }
}
```

- `overallScore`와 `criteria[].score`는 0~10, 소수 첫째 자리다. 현재 종합 점수는 구도 점수와 같다.
- `distinctivePerspective`는 독특한 시각이 드러났는지 여부다. 서버가 판정하며 판정에 쓴 원점수는 제공하지 않는다.
- `feedback`은 300자 이하다. 저장된 값을 그대로 돌려주므로 다시 조회해도 바뀌지 않는다.
- `criteria`는 지금 구도 한 항목이다. 항목이 늘 수 있으니 `key`·`label`을 목록으로 표시하고 개수를 고정하지 않는다.

실패하면 `200`과 `FAILED`를 돌려주며 `completedAt`은 있고 `result`는 `null`이다. 실패 사유는 앱에 알려 주지 않는다.

```json
{
  "status": "FAILED",
  "requestedAt": "2026-10-20T05:00:00Z",
  "completedAt": "2026-10-20T05:01:10Z",
  "result": null
}
```

아직 요청하지 않았으면 `200`과 `NONE`을 돌려주며 시각과 결과가 모두 `null`이다.

```json
{
  "status": "NONE",
  "requestedAt": null,
  "completedAt": null,
  "result": null
}
```

시각은 모두 UTC `Instant`이고 `requestedAt`·`completedAt`·`result`는 값이 없을 때 필드를 생략하지 않고 `null`로 온다. `requestedAt`은 `NONE`일 때만 `null`이다.

## 상태 분기와 폴링

앱은 HTTP 상태 코드가 아니라 응답의 `status`로 분기한다. `2xx`는 모두 정상 응답이며 `202`는 아직 끝나지 않았다는 뜻이다.

| `status` | HTTP | 앱 처리 |
| --- | --- | --- |
| `NONE` | `200`(조회) | "AI 피드백 받기"를 보여 주고 폴링하지 않는다. 사용자가 누르면 `POST`한다. |
| `PROCESSING` | `202`(요청), `200`(조회) | 분석 중 화면을 보여 주고 폴링한다. 대기 중과 처리 중은 구분하지 않는다. |
| `COMPLETED` | `200` | `result`를 표시하고 폴링을 멈춘다. |
| `FAILED` | `200` | 실패 안내를 표시하고 폴링을 멈춘다. 지금은 다시 요청할 수 없다. |

- 폴링 간격은 3초다. 화면을 벗어나면 폴링을 멈춘다.
- 약 3분이 지나도 `PROCESSING`이면 폴링을 멈추고 "잠시 후 다시 확인해 주세요" 같은 안내를 보여 준다. 서버 배포 중에는 처리가 이어지기까지 이만큼 걸릴 수 있다. 지연은 실패가 아니다.
- 사용자가 화면에 다시 들어오면 `GET`부터 시작해 `status`에 맞게 이어간다. 폴링 시간 초과 뒤 `POST`를 다시 호출하지 않는다.

## 오류

| 응답 | 의미와 앱 처리 |
| --- | --- |
| `400`, `BUSINESS_ERROR` | `POST`에서 지금은 AI 리뷰를 요청할 수 없음(기능이 꺼진 상태, `message`는 "지금은 AI 리뷰를 요청할 수 없습니다."). 앱은 응답의 `message`를 안내한다. 이미 요청한 리뷰가 있으면 이 오류 대신 기존 상태를 돌려준다. `postId` 형식이 올바르지 않으면 `POST`·`GET` 모두 `400`이며 이때 `message`는 ID 형식 오류다. |
| `401`, `UNAUTHORIZED` | 비로그인·유효하지 않은 인증. 기존 인증 갱신 정책에 따라 처리한다. |
| `403`, `FORBIDDEN` | 이용이 정지된 회원의 `POST`. `GET`에서는 발생하지 않는다. |
| `404`, `BUSINESS_ERROR` | 본인의 공개된 게시물이 아님(없거나 남의 게시물이거나 삭제·탈퇴 등으로 공개 조건 불충족). `GET`·`POST` 모두 게시물 문제일 때만 `404`이며, 아직 요청하지 않은 것은 `404`가 아니라 `200`과 `NONE`이다. |

## 아직 제공하지 않는 것

- 실패한 리뷰의 재요청과 서버의 자동 재시도는 없다. 지금은 실패가 최종이며 재요청 버튼은 만들지 않는다. [#690](https://github.com/woowacourse-teams/2026-Chalkak/issues/690)에서 정하면 실패 상태의 요청이 진행 중(`202`, `PROCESSING`)으로 바뀔 수 있으므로, 앞서 설명한 대로 `status`로 분기하면 앱 변경 없이 대응할 수 있다.
- 관리자의 AI 리뷰 조회는 [#691](https://github.com/woowacourse-teams/2026-Chalkak/issues/691) 범위다.
- 피드·게시물 상세 응답에는 AI 리뷰가 포함되지 않는다. 상세 화면에서 위 API를 따로 호출한다.
- 사용자 공개 시점은 서버 배포 계획에 따르며, 서버에 쓸 수 있는 프롬프트 버전이 없는 동안에는 `POST`가 `400`이다.
