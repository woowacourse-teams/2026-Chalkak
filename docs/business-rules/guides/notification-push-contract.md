# Android·iOS 푸시 메시지 연동 계약

현재 [알림 규칙](../rules/notification.md)의 NOTIFICATION-002·003·005·008과 서버의 `FcmDevicePushSender` 구현을 기준으로 작성한다. 앱에서 푸시를 해석하고 화면 이동·읽음 처리를 구현할 때 사용하는 문서다.

## 적용 범위와 상태

- 현재 메시지는 게시물 승인·반려만 제공한다. 주제 푸시는 후속 이슈 #493에서 계약을 정하며, 관리자 공지·좋아요는 현재 제공하지 않는다.
- 서버 구현은 [PR #562](https://github.com/woowacourse-teams/2026-Chalkak/pull/562)에 있다. 실제 AWS·Firebase·기기 수신과 서버 배포는 아직 확인하지 않았다.
- 이 안내는 현재 구현을 설명한다. REST API 요청·응답과 기존 서비스 정책을 변경하지 않는다.
- 연동 준비와 토큰 등록은 [푸시 기기 등록 안내](push-device-registration.md), 알림함 조회는 [알림함 안내](notification-inbox.md)를 함께 읽는다.

## 1. 서버가 구성하는 메시지

서버는 **표시용 `notification`과 이동·조회용 `data`를 함께** 보낸다. 아래 JSON은 두 영역의 계약을 설명하는 예시이며, 서버의 전체 FCM HTTP 요청이나 각 플랫폼의 콜백 객체를 그대로 나타내지는 않는다. 수신 기기 토큰과 플랫폼 설정은 생략했다.

### 승인

```json
{
  "notification": {
    "title": "게시물이 승인되었습니다.",
    "body": "내 사진이 피드에 공개되었습니다."
  },
  "data": {
    "eventId": "019a0010-0000-7000-8000-000000000001",
    "notificationId": "019a0010-0000-7000-8000-000000000002",
    "type": "POST_APPROVED",
    "sourceType": "POST",
    "sourceId": "019a0010-0000-7000-8000-000000000003"
  }
}
```

### 반려

```json
{
  "notification": {
    "title": "게시물이 반려되었습니다.",
    "body": "반려 사유를 확인해 주세요."
  },
  "data": {
    "eventId": "019a0010-0000-7000-8000-000000000004",
    "notificationId": "019a0010-0000-7000-8000-000000000005",
    "type": "POST_REJECTED",
    "sourceType": "POST",
    "sourceId": "019a0010-0000-7000-8000-000000000006"
  }
}
```

### 필드 의미

| 필드 | 형식·현재 값 | 앱에서의 용도 |
| --- | --- | --- |
| `notification.title` | 문자열 | OS 알림 또는 앱의 알림 표시 제목 |
| `notification.body` | 문자열 | 표시 문구. 반려 사유 자체가 아님 |
| `data.type` | `POST_APPROVED` / `POST_REJECTED` | 이동할 화면 구분 |
| `data.notificationId` | 알림 UUID 문자열 | 알림 상세 조회·단건 읽음 처리. 알림 목록의 `id`와 같은 값 |
| `data.sourceType` | `POST` | 관련 대상 종류 구분. 현재 두 종류 모두 게시물 |
| `data.sourceId` | 게시물 UUID 문자열 | 승인된 게시물 화면에서 조회할 게시물 식별자 |
| `data.eventId` | 검수 사건 UUID 문자열 | 사건 식별·로그 추적. 알림 ID나 게시물 ID를 대신하지 않음 |

현재 승인·반려에서는 위 `data` 다섯 필드를 모두 제공하며 값은 모두 문자열이다. `eventId`와 `notificationId`는 서로 다른 식별자다. 제목·본문 문자열을 비교해서 알림 종류나 대상을 판단하지 않는다.

## 2. 푸시에 알림함 데이터를 모두 담지 않는다

| 정보 | 전달 방법 |
| --- | --- |
| 푸시에 보여줄 제목·문구 | `notification` |
| 화면 종류·조회할 대상 ID | `data` |
| 알림 목록·썸네일·읽음 상태 | 알림 목록 API |
| 반려 사진 원본·반려 사유 | 알림 상세 API |
| 승인된 게시물 내용·사진 | 기존 게시물 조회 API |

푸시에는 썸네일 URL·원본 사진 URL·반려 사유·회원 ID·FCM 토큰 원문을 사용자 데이터로 넣지 않는다. 서버가 FCM 발송 대상으로 사용하는 토큰은 앱에 보내는 `data`가 아니다.

사진과 사유를 푸시에 복제하기보다 **클릭 시 인증된 API로 최신 정보를 조회**한다. 푸시를 받았다는 사실만으로 현재 계정이 해당 알림에 접근할 권한을 얻는 것은 아니다.

현재 화면 이동·읽음 처리에는 `type`, `notificationId`, `sourceId`를 사용한다. `sourceType`은 대상 종류를 구분하고 `eventId`는 사건을 추적하기 위한 정보다. 다섯 필드가 모두 화면 이동의 필수 값이라는 뜻은 아니며, 현재 구현의 계약은 그대로 유지한다.

## 3. 클릭 시 처리 순서

```text
사용자가 푸시 클릭
  → 앱이 data의 type과 식별자 확인
  → 현재 로그인 계정으로 필요한 API 호출
  → 승인: sourceId로 게시물 화면
  → 반려: notificationId로 알림 상세 화면
  → notificationId로 단건 읽음 API 호출
```

| 종류 | 화면·조회 | 읽음 처리 |
| --- | --- | --- |
| `POST_APPROVED` | `sourceId`로 승인된 게시물 화면을 연다. 알림 상세를 먼저 조회할 필요는 없다. | `PATCH /api/v1/notifications/{notificationId}/read` |
| `POST_REJECTED` | `GET /api/v1/notifications/{notificationId}`로 본인 사진 원본·반려 사유를 조회한다. 새로 올리기 버튼은 없다. | 같은 단건 읽음 API |

- API에 현재 회원 액세스 토큰을 전달한다. 알림 ID 대신 게시물 ID를 읽음 API에 넣지 않는다.
- 상세 조회 자체는 읽음 처리하지 않는다. 단건 읽음 성공은 본문 없는 `204`이며 반복 요청도 최초 읽은 시각을 유지한다.
- 다른 계정의 알림, 삭제된 게시물의 알림, 보관 기한이 지난 알림은 상세·읽음에서 `404`가 될 수 있다. 알림함 안내의 오류 처리를 적용하며 푸시의 이전 정보로 보호된 상세를 대신 보여주지 않는다.
- 푸시 클릭과 알림함 읽음은 별개다. 현재 자체 클릭 보고 API는 없으며 `eventId`를 클릭 보고용 API로 보내는 처리는 추가하지 않는다.

## 4. 앱 상태별 수신 위치

### Android

| 앱 상태 | 현재 notification + data 메시지 처리 |
| --- | --- |
| 포그라운드 | `FirebaseMessagingService.onMessageReceived`에서 제목·본문과 `data`를 받는다. 표시 방식은 앱에서 구현한다. |
| 백그라운드 | SDK가 시스템 알림을 표시하고, 클릭 시 앱을 여는 Activity의 Intent extras에서 `data`를 읽는다. |

현재 서버는 Android `click_action`을 지정하지 않는다. 앱은 시작 Activity에서 전달받은 값을 화면 이동으로 연결해야 한다. 앱의 새 실행과 기존 Activity로 들어오는 Intent를 모두 처리한다. 백그라운드 알림을 `onMessageReceived`에서만 처리하는 구현은 클릭 이동을 빠뜨릴 수 있다. [Firebase Android 수신 안내](https://firebase.google.com/docs/cloud-messaging/android/receive-messages)

### iOS

서버의 FCM 메시지는 APNs를 통해 전달된다. 앱에서 받는 객체가 위 예시의 `notification`·`data` JSON과 동일한 중첩 구조라고 가정하지 않는다. 표시 정보는 APNs의 `aps`에, 사용자 정의 식별자는 알림의 `userInfo`에 전달된다.

- 포그라운드 알림은 `UNUserNotificationCenterDelegate`의 `willPresent`에서 표시 여부를 처리한다.
- 클릭은 같은 delegate의 `didReceive response`에서 `response.notification.request.content.userInfo`를 읽어 처리한다.
- 실행 상태별 화면 준비와 이동 처리는 앱에서 연결한다. iOS APNs·권한 설정은 알림함 안내의 준비 항목을 확인한다.

[Firebase Apple 플랫폼 수신 안내](https://firebase.google.com/docs/cloud-messaging/ios/receive-messages)

## 5. 시간·중복·오류에 대한 주의점

- 서버는 검수 사건 발생 후 **30분 안에만 새 FCM 발송을 시작**한다. SQS 재전달 때도 같은 기한을 확인한다. Android TTL은 메시지 생성 시 남은 기간으로 한 번 계산하고, iOS `apns-expiration`은 원래 기한의 절대 시각으로 설정한다.
- 기한 전에 시작한 Firebase SDK 내부 재시도는 기한 이후에도 계속될 수 있다. Android TTL을 재시도마다 갱신하지 않으므로 실제 만료 시각이 원래 기한보다 늦어질 수 있다. 앱이 `data`에서 만료 시각을 받아 계산하거나 오래된 푸시를 자동으로 제거하는 계약은 현재 없다.
- FCM의 요청 수락은 실제 기기 수신·표시·클릭 성공을 뜻하지 않는다.
- 푸시 발송 기한 30분과 알림함 보관 30일은 다르다. 이미 표시된 푸시를 나중에 누른 경우에는 현재 API의 대상 존재·권한·보관 조건에 따라 처리한다.
- SQS 재전달이나 외부 요청의 결과 불명확으로 중복 푸시가 발생할 수 있다. `eventId` 제공만으로 앱이나 FCM이 자동 중복 제거하는 것은 아니다. 별도 앱 중복 제거 방식은 이번 안내에서 정하지 않는다.
- 누락된 필드·알 수 없는 `type`·로그아웃 후 클릭·계정 전환 시 로그인 및 이동 UX는 앱과 합의해야 한다. 임의의 기본 게시물이나 반려 화면으로 이동시키는 정책을 이 문서에서 확정하지 않는다.

## 6. 앱 연동 확인 항목

1. 승인·반려 각각의 실제 기기 수신과 올바른 화면 이동.
2. 포그라운드·백그라운드와 앱 프로세스가 새로 시작되는 클릭 처리. 수신이 항상 보장된다고 가정하지 않는다.
3. 반려 상세의 사진 원본·사유 표시와 단건 읽음 `204` 처리.
4. 로그아웃·계정 전환·게시물 삭제·알림 보관 기한 경과 시 접근·오류 처리.
5. 중복 수신과 누락·알 수 없는 필드가 화면 이동 오류를 만들지 않는지 확인.

현재 이 문서 추가만으로 Android·iOS 구현, 공통 하네스 재배포 또는 개발·운영 서버 적용이 완료된 것은 아니다.
