# 알림함 조회·보관 연동 안내

현재 정책은 [알림함 규칙](../rules/notification.md)의 NOTIFICATION-001~004 및 NOTIFICATION-007~008을 따른다.

## 조회와 읽음 처리

- 알림 목록 조회: `GET /api/v1/notifications`
- 알림 상세 조회: `GET /api/v1/notifications/{notificationId}`
- 미읽음 여부 조회: `GET /api/v1/notifications/unread-status`
- 단건 읽음 처리: `PATCH /api/v1/notifications/{notificationId}/read`
- 전체 읽음 처리: `PATCH /api/v1/notifications/read-all`

회원 액세스 토큰으로 본인 알림만 접근한다. 목록·상세 조회만으로 읽음 처리하지 않는다. 읽음 처리 성공은 본문 없는 204이며 반복 요청은 최초 읽은 시각을 유지한다.

## 수신 설정과 알림함

승인·반려 푸시 설정이 꺼져 있어도 검수 결과는 알림함에 저장한다. 이후 설정을 켜도 꺼져 있던 동안 생성된 알림과 발행 상태 도입 전의 기존 알림을 소급 발송하지 않는다. 주제 푸시 설정은 승인·반려 발행 필요 여부에 영향을 주지 않는다.

현재 [이슈 #492](https://github.com/woowacourse-teams/2026-Chalkak/issues/492)에서 서버 DB의 초기 발행 상태·Relay·PushWorker·FCM 연동 코드를 작성했다. 실제 AWS·Firebase 연동 확인과 운영 배포는 아직 완료하지 않았다. 이 단계로 앱의 API 요청·응답 형식이 바뀌지는 않는다.

## 알림 클릭 시 이동 화면

목록의 각 알림에 `sourceType`과 `sourceId`를 추가한다. 현재 승인·반려는 둘 다 `sourceType = POST`, `sourceId = 관련 게시물 UUID`이다. 대상이 없는 알림을 제공하게 되면 두 값이 함께 null일 수 있으므로 앱은 알 수 없는 종류나 대상이 없는 경우를 처리할 수 있어야 한다. 현재 관리자 공지·좋아요는 제공하지 않는다.

| 알림 종류 | 앱 이동 | 사용할 식별자 |
| --- | --- | --- |
| `POST_APPROVED` | 승인된 게시물 화면 | `sourceId`로 게시물 조회 |
| `POST_REJECTED` | 본인 사진 원본과 반려 사유를 보여주는 알림 상세 화면. 새로 올리기 버튼 없음 | 목록의 `id`로 알림 상세 조회 |

목록의 `id`는 알림 ID이며 `sourceId`는 게시물 ID다. 읽음 처리는 두 경우 모두 알림 ID를 사용한다. 기존 승인 알림 상세 API도 유지하지만, 승인 알림 클릭 시 이동을 위해 상세 API를 먼저 조회할 필요는 없다. 실제 화면 이동은 Android·iOS에서 구현해야 한다.

## 30일 보관 정책 적용 시 앱 영향

일반·정지 회원의 알림은 사건 발생 시각부터 30일 보관한다. 현재 승인·반려는 검수 확정 시각을 응답의 `createdAt`으로 제공한다. 정확한 30일 경계에는 표시하며, 경계를 넘으면 목록·상세·미읽음·읽음 대상에서 제외한다. 최근에 읽었어도 보관 기간을 연장하지 않는다.

기존 앱이 캐시한 알림이나 오래된 ID로 상세·단건 읽음을 요청하면 삭제된 게시물의 알림과 마찬가지로 404가 올 수 있다. 앱은 찾을 수 없는 알림을 안내하고 목록·미읽음 상태를 다시 조회한다. 정리 작업을 아직 실행하지 않아 DB에 행이 남아 있더라도 조회·읽음 처리할 수 없다.

탈퇴 회원의 인증된 알림 접근은 즉시 차단한다. 남은 기록은 탈퇴 후 30일 보관하는 서버 정리 예외이며 앱 조회 허용 기간을 뜻하지 않는다. 비로그인 공개 피드 정책은 별개다.

기존 요청·응답 필드와 API URI는 유지하고 목록 응답에 관련 대상 두 필드를 추가한다. 관련 구현은 [이슈 #541](https://github.com/woowacourse-teams/2026-Chalkak/issues/541), [PR #548](https://github.com/woowacourse-teams/2026-Chalkak/pull/548)에서 확인한다. 운영 서버 적용 여부는 별도로 확인한다.

### SQS 전달 단계의 적용 상태

서버가 SQS 수락을 확인해도 앱의 수신 성공을 뜻하지 않는다. 이 단계에서 앱의 요청 형식·화면 이동 계약은 바뀌지 않는다. DB → SQS 일시 실패는 1분 뒤 재시도하고 사건 후 30분부터는 새 발행을 하지 않는다(NOTIFICATION-007). 큐 URL·권한 확인은 사용자 보고이며 실제 AWS·Firebase·기기 수신과 서버 배포는 미확인이다. 실제 푸시가 동작한다고 안내하지 않는다.

## Android·iOS 푸시 연동 준비

실제 메시지 예시·필드 의미·플랫폼별 수신과 클릭 처리는 [푸시 메시지 연동 계약](notification-push-contract.md)을 참고한다.

1. 앱의 Firebase 프로젝트와 서버 발송 프로젝트가 일치하는지 확인한다. 서버용 서비스 계정 인증은 앱의 `google-services.json` 또는 `GoogleService-Info.plist`와 다르다. 비밀 서버 키는 앱·Git·채팅에 넣지 않는다.
2. 로그인 후 FCM 토큰을 [기기 등록 안내](push-device-registration.md)에 따라 등록한다. 토큰 변경·앱 재시작 시에도 갱신한다. OS 알림 권한과 포그라운드 표시 처리는 각 앱이 구현한다.
3. iOS는 Firebase 프로젝트의 APNs 인증 키 또는 인증서, Push Notifications capability·앱 권한·APNs/FCM 토큰 연결을 확인한다.
4. 푸시는 `notification` 제목·본문과 `data`의 `eventId`, `notificationId`, `type`, `sourceType`, `sourceId`를 제공한다. 반려 사유·사진 URL·회원 ID·FCM 토큰 원문은 data에 넣지 않는다. 수신자가 바뀐 계정이면 해당 회원의 API로 접근할 수 없는 알림을 임의로 보여주지 않는다.
5. 승인은 `sourceId`로 게시물 화면, 반려는 `notificationId`로 알림 상세 화면을 연다. 읽음 처리는 기존 API를 사용하고 클릭 보고 API는 추가하지 않는다. 알림이 삭제됐거나 게시물이 없으면 기존 404 처리를 적용한다.
6. 개발 서버에서 두 플랫폼의 백그라운드·포그라운드 수신, 로그아웃·수신 설정·만료를 실기기로 확인한다. 현재는 연동 코드 작성 상태이며 앱 적용·실기기 수신·서버 배포는 미확인이다.
