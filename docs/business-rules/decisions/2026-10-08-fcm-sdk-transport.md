# NOTIFICATION-008 FCM SDK 전송 사용과 발송 기한의 경계

- 결정일: 2026-10-08
- 상태: 확정
- 변경 전: SDK 내부 HTTP 재시도마다 원래 발송 기한을 검사하고 Android TTL을 다시 계산하여 기한 이후 새 HTTP 요청을 막았다.
- 변경 후: Firebase SDK 기본 전송을 사용한다. Worker의 새 발송 시작과 SQS 재전달은 사건 발생 후 30분 안에만 허용한다. Android TTL은 메시지 생성 시 한 번 계산하고 iOS는 원래 기한의 절대 만료 시각을 사용한다. 기한 전에 시작한 SDK 내부 재시도는 기한 이후에도 계속될 수 있으며 Android 만료 시각이 뒤로 밀리는 것을 허용한다.
- 변경 이유: 승인·반려 결과는 알림함에서도 확인할 수 있다. SDK 재시도 중 기한을 엄격히 지키기 위해 HTTP 전송·요청 JSON 수정·ThreadLocal 기한 전달을 유지하는 부담보다, 합의한 시간 경계의 예외를 받아들이고 발송 구조를 단순하게 유지하는 편이 적절하다고 판단했다.
- 영향 범위: 승인·반려 FCM 발송과 시간 경계 테스트, Android·iOS 푸시 연동 안내. REST 요청·응답, 푸시 메시지 필드, SQS 숨김 연장·수신 횟수·오류 분류는 유지한다.
- 관련 규칙: NOTIFICATION-008
- 관련 이슈·PR: [#492](https://github.com/woowacourse-teams/2026-Chalkak/issues/492), [#562](https://github.com/woowacourse-teams/2026-Chalkak/pull/562)

SQS 재전달 시 기한 검사는 이미 실행 중인 SDK 내부 재시도를 중단하지 않는다. 수락 후 오프라인 기기 전달은 설정한 TTL·APNs 만료에 따라 FCM/APNs가 처리한다. 기기 수신·표시 시각은 보장하지 않는다.

예를 들어 09:30 만료인 알림을 09:29에 시작하여 Android TTL을 1분으로 설정한 뒤 SDK 재시도가 09:30:20에 수락되면, Android 만료가 원래 기한 이후로 밀릴 수 있다. 이번 변경은 이 예외를 허용하며, SDK 재시도의 최대 지연을 임의의 고정 시간으로 보장하지 않는다.

실제 AWS·Firebase·Android·iOS 연동과 서버 배포는 별도로 검증한다.
