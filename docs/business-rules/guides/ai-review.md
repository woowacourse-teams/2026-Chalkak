# 게시물 AI 리뷰 요청과 결과 조회

작성자는 본인의 공개된 게시물에 AI 리뷰를 요청하고, 처리가 끝나면 구도 점수·독특한 시각 여부·피드백을 확인한다. 실패하면 정해진 횟수까지 다시 요청할 수 있다. 요청과 조회 API는 [#689](https://github.com/woowacourse-teams/2026-Chalkak/issues/689), 자동 재시도와 재요청은 [#690](https://github.com/woowacourse-teams/2026-Chalkak/issues/690) 범위이며, 정책은 [AIREVIEW-001~006](../rules/ai-review.md), 결과 알림은 [NOTIFICATION-014](../rules/notification.md#notification-014-ai-리뷰-결과-알림), 이용 정지는 [USER-002](../rules/user.md#user-002-이용-정지)를 따른다.

## 요청과 조회 흐름

1. 게시물 상세에 진입하면 먼저 `GET`으로 상태를 확인한다. `NONE`이면 "AI 피드백 받기"를, `PROCESSING`이면 생성 중을, `COMPLETED`이면 결과 보기를, `FAILED`이면 실패 안내를 보여 주고 `canRequestAgain`이 `true`일 때만 "다시 요청" 버튼을 함께 보여 준다.
2. 사용자가 "AI 피드백 받기"나 "다시 요청"을 누를 때만 `POST`로 요청한다. 진행 중이거나 완료된 게시물에 `POST`해도 새로 만들지 않고 현재 상태를 그대로 돌려준다(`PROCESSING`은 `202`, `COMPLETED`는 `200`). 실패한 리뷰에 `POST`하면 같은 리뷰를 다시 대기열에 넣고 `202`와 `PROCESSING`을 돌려준다. 실패 뒤에 도착한 `POST`는 같은 요청의 재전송이어도 새 다시 요청으로 처리해 요청 횟수를 쓰므로, 버튼은 응답을 받을 때까지 한 번만 보내고 실패 응답을 받은 뒤 자동으로 다시 보내지 않는다.
3. 응답의 `status`가 `PROCESSING`이면 `GET`으로 상태를 확인한다. `COMPLETED`이면 결과를, `FAILED`이면 실패 안내를 보여 준다.
4. 이용이 정지된 회원은 `POST`가 `403`이다. 요청 버튼을 숨기거나 안내하고, `GET`은 정지 상태와 관계없이 호출할 수 있다.

## 요청

```http
POST /api/v1/posts/{postId}/ai-review
Authorization: Bearer {accessToken}
```

`postId`는 본인이 작성한 공개 게시물의 UUID다. 요청 본문은 없다. 서버는 요청을 접수만 하고 비동기로 처리하므로 응답에는 보통 결과가 없다.

접수 직후와 처리 중, 실패한 리뷰의 다시 요청 직후에는 `202`와 `PROCESSING`을 돌려준다. 서버가 일시적인 실패를 자동으로 다시 시도하며 기다리는 동안에도 `PROCESSING`이다.

```json
{
  "status": "PROCESSING",
  "requestedAt": "2026-10-20T05:00:00Z",
  "completedAt": null,
  "result": null,
  "canRequestAgain": false
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
    "feedback": {
      "summary": "빛을 따라 시선이 흐르는 안정적인 구도",
      "strength": "빛이 들어오는 방향을 따라 시선이 자연스럽게 이어집니다.",
      "weakness": "수평선이 살짝 기울어 안정감이 조금 떨어집니다.",
      "suggestion": "카메라를 낮추고 수평선을 맞춰 한 번 더 찍어 보세요."
    }
  },
  "canRequestAgain": false
}
```

- `overallScore`와 `criteria[].score`는 0~10, 소수 첫째 자리다. 현재 종합 점수는 구도 점수와 같다.
- `distinctivePerspective`는 독특한 시각이 드러났는지 여부다. 서버가 판정하며 판정에 쓴 원점수는 제공하지 않는다.
- `feedback`은 네 항목을 가진 객체이며 항목은 항상 비어 있지 않다. `summary`는 한 줄 요약(60자 이하)으로 화면의 굵은 제목, `strength`는 좋았던 점, `weakness`는 아쉬운 점, `suggestion`은 다음엔 이렇게(각 150자 이하)다. 저장된 값을 그대로 돌려주므로 다시 조회해도 바뀌지 않는다.
- `criteria`는 지금 구도 한 항목이다. 항목이 늘 수 있으니 `key`·`label`을 목록으로 표시하고 개수를 고정하지 않는다.

실패하면 `200`과 `FAILED`를 돌려주며 `completedAt`은 있고 `result`는 `null`이다. 서버가 일시적인 실패를 자동으로 다시 시도한 뒤에도 실패했거나 다시 시도하지 않는 실패일 때 이 상태가 된다. 실패 사유는 앱에 알려 주지 않는다. 요청 횟수가 남았으면 `canRequestAgain`이 `true`다.

```json
{
  "status": "FAILED",
  "requestedAt": "2026-10-20T05:00:00Z",
  "completedAt": "2026-10-20T05:01:10Z",
  "result": null,
  "canRequestAgain": true
}
```

아직 요청하지 않았으면 `200`과 `NONE`을 돌려주며 시각과 결과가 모두 `null`이다.

```json
{
  "status": "NONE",
  "requestedAt": null,
  "completedAt": null,
  "result": null,
  "canRequestAgain": false
}
```

시각은 모두 UTC `Instant`이고 `requestedAt`·`completedAt`·`result`는 값이 없을 때 필드를 생략하지 않고 `null`로 온다. `requestedAt`은 `NONE`일 때만 `null`이다. `requestedAt`은 최초 요청 시각이라 다시 요청해도 바뀌지 않는다.

`canRequestAgain`은 `FAILED`이고 요청 횟수가 남았을 때만 `true`이고 그 밖의 상태에서는 `false`다. 게시물당 요청은 처음을 포함해 최대 3번이다. 이 값은 횟수만 반영하므로 `true`여도 `POST`가 `400`(기능이 꺼진 상태)이거나 이용이 정지된 회원이면 `403`일 수 있다. 서버 배포 전에는 응답에 이 필드가 없을 수 있으니 없으면 `false`로 처리한다.

## 상태 분기와 폴링

앱은 HTTP 상태 코드가 아니라 응답의 `status`로 분기한다. `2xx`는 모두 정상 응답이며 `202`는 아직 끝나지 않았다는 뜻이다.

| `status` | HTTP | 앱 처리 |
| --- | --- | --- |
| `NONE` | `200`(조회) | "AI 피드백 받기"를 보여 주고 폴링하지 않는다. 사용자가 누르면 `POST`한다. |
| `PROCESSING` | `202`(요청), `200`(조회) | 분석 중 화면을 보여 주고 폴링한다. 대기 중과 처리 중은 구분하지 않는다. |
| `COMPLETED` | `200` | `result`를 표시하고 폴링을 멈춘다. |
| `FAILED` | `200` | 실패 안내를 표시하고 폴링을 멈춘다. `canRequestAgain`이 `true`일 때만 "다시 요청" 버튼을 보여 주고, 누르면 `POST`한다. |

- 폴링 간격은 3초다. 화면을 벗어나면 폴링을 멈춘다.
- 폴링 시간 창은 약 5분이다. 이 시간이 지나도 `PROCESSING`이면 폴링을 멈추고 "잠시 후 다시 확인해 주세요" 같은 안내를 보여 준다. 지연은 실패가 아니다.
- 시간 창은 `POST`를 호출했거나 화면에 들어와 폴링을 시작한 시각부터 잰다. `requestedAt`은 최초 요청 시각이라 다시 요청한 뒤에는 옛날 값이므로 기준으로 쓰지 않는다.
- 사용자가 화면에 다시 들어오면 `GET`부터 시작해 `status`에 맞게 이어간다. 폴링 시간 초과 뒤 `POST`를 다시 호출하지 않는다.

서버는 일시적인 실패를 `FAILED`로 알리기 전에 자동으로 다시 시도하며, 이 동안 응답은 계속 `PROCESSING`이다. 대기 시간은 대략 다음과 같다.

| 상황 | 걸리는 시간 |
| --- | --- |
| 빠른 실패가 3번 이어짐 | 약 1분 |
| 시간 초과가 3번 이어짐(최악) | 약 4분. OpenAI 응답 대기 최대 65초(연결 5초, 응답 60초) × 3번에 재시도 전 대기 약 10초와 약 30초를 더한 값이다. |
| 요청 한도 초과(`429`)에 OpenAI가 대기 시간을 알려 준 경우 | 알려 준 시간이 반영되어 재시도 사이가 최대 60초까지 늘어난다(약 2분 이상). |
| 서버 배포·장애로 처리가 중단됨 | 위 시간에 최대 약 2분이 더해진다. |

서버 배포나 장애가 겹치면 합계가 5분을 넘을 수 있다. 폴링 창이 끝나도 실패가 아니므로 안내만 보여 주고, 사용자가 화면에 다시 들어오면 `GET`부터 이어간다.

재시도 전 대기는 약 10초와 약 30초에 ±20% 범위의 차이가 있고, OpenAI가 더 오래 기다리라고 알려 주면 그보다 짧게 기다리지 않는다(최대 60초). 다시 요청하면 이 자동 시도를 새로 센다. 다시 요청한 뒤에도 폴링 시간 창은 같은 약 5분이다.

## 결과 알림

AI 리뷰가 완료되거나 실패로 끝나면 서버가 알림함에 결과 알림(`AI_REVIEW_COMPLETED`, `AI_REVIEW_FAILED`)을 저장하고, 'AI 리뷰 결과' 수신 설정(`aiReviewPushEnabled`)이 켜져 있으면 푸시도 보낸다. 앱을 떠난 사용자가 결과를 알 수 있게 하기 위한 것이다([NOTIFICATION-014](../rules/notification.md#notification-014-ai-리뷰-결과-알림)). 푸시 필드와 알림함 표시는 [푸시 메시지 연동 계약](notification-push-contract.md)과 [알림함 연동 안내](notification-inbox.md)를 따른다.

- 자동 재시도를 기다리는 동안(`PROCESSING`)에는 알림이 없다. 알림은 최종 상태인 `COMPLETED`·`FAILED`가 될 때 한 번 오고, 실패한 리뷰를 다시 요청해 또 끝나면 회차마다 온다(게시물당 최대 3건). 게시물을 쓸 수 없어 실패한 경우에는 오지 않는다.
- 알림을 누르면 `sourceId`의 게시물 AI 리뷰 화면으로 진입한다. 진입하면 `GET`부터 시작해 `status`에 맞게 이어가며, 결과는 알림이 아니라 이 조회로 가져온다. 알림 본문은 결과 내용이나 실패 사유를 담지 않는다. 실패 알림의 문구는 다시 요청할 수 있는지만 구분하므로 버튼 표시는 `canRequestAgain`을 기준으로 한다.
- 사용자가 이 화면에서 폴링으로 결과를 기다리는 중에도 서버는 알림을 만들고 푸시를 보낸다. 앱은 푸시의 `sourceId`가 지금 보고 있는 게시물의 AI 리뷰 화면이면 포그라운드 푸시 표시를 생략하기를 권장한다. 서버는 조회만으로 알림을 읽음 처리하지 않으므로 알림은 미읽음으로 남는다. AI 리뷰 응답에는 알림 ID가 없다.
- 게시물이 삭제되면 알림도 알림함에서 사라진다. 공개 조건을 벗어나 조회가 `404`이면 위 오류 처리를 따른다.
- 알림 저장이 막혀 서버가 알림 없이 실패로 끝내는 드문 경우가 있으므로 알림이 오지 않아도 `GET`의 `FAILED`로 결과를 확인할 수 있다. 알림이 도착하는 것을 폴링 중단의 조건으로 삼지 않는다.

## 오류

| 응답 | 의미와 앱 처리 |
| --- | --- |
| `400`, `BUSINESS_ERROR` | `POST`에서 지금은 AI 리뷰를 요청할 수 없음(기능이 꺼진 상태, `message`는 "지금은 AI 리뷰를 요청할 수 없습니다."). 앱은 응답의 `message`를 안내한다. 이미 요청한 리뷰가 있으면 이 오류 대신 기존 상태를 돌려주되, 실패한 리뷰를 다시 요청할 때 기능이 꺼져 있으면 이 오류다. 요청이 3번을 넘은 4번째 `POST`도 `400`이며 `message`는 "더 이상 AI 리뷰를 다시 요청할 수 없습니다."다. 이 경우는 메시지만 보여 주면 되고, `canRequestAgain`이 `false`라 버튼을 숨기면 보통 발생하지 않는다. `postId` 형식이 올바르지 않으면 `POST`·`GET` 모두 `400`이며 이때 `message`는 ID 형식 오류다. |
| `401`, `UNAUTHORIZED` | 비로그인·유효하지 않은 인증. 기존 인증 갱신 정책에 따라 처리한다. |
| `403`, `FORBIDDEN` | 이용이 정지된 회원의 `POST`. `GET`에서는 발생하지 않는다. |
| `404`, `BUSINESS_ERROR` | 본인의 공개된 게시물이 아님(없거나 남의 게시물이거나 삭제·탈퇴 등으로 공개 조건 불충족). `GET`·`POST` 모두 게시물 문제일 때만 `404`이며, 아직 요청하지 않은 것은 `404`가 아니라 `200`과 `NONE`이다. |

## 아직 제공하지 않는 것

- 성공한 리뷰의 재요청은 없다. 완료된 리뷰는 다시 만들지 않으므로 `COMPLETED`에는 다시 요청 버튼이 없다.
- 관리자의 AI 리뷰 조회는 [#691](https://github.com/woowacourse-teams/2026-Chalkak/issues/691) 범위다.
- 피드·게시물 상세 응답에는 AI 리뷰가 포함되지 않는다. 상세 화면에서 위 API를 따로 호출한다.
- 사용자 공개 시점은 서버 배포 계획에 따르며, 서버에 쓸 수 있는 프롬프트 버전이 없는 동안에는 `POST`가 `400`이다.
