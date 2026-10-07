# 알림 SQS 준비와 개발 연동 확인

## 현재 적용 상태

#492의 두 번째 커밋은 DB → Relay → SQS까지 구현한다. PushWorker·FCM은 다음 커밋이다. 사용자는 개발 큐 URL을 제공했고 AWS 권한 확인을 완료했다고 알렸다. 실제 AWS 발행과 기기 수신은 아직 검증하지 않았다. 로컬 HTTP 서버 테스트는 실제 AWS 권한·큐 연결 검증을 대신하지 않는다.

커밋·푸시만으로 개발 서버에 배포되지 않는다. 이번 PR의 PushWorker·FCM 구현까지 마친 뒤 개발 서버에 배포하여 DB → SQS → FCM 전체 흐름을 검증한다. 두 번째 커밋 직후 개발 서버 테스트를 필수로 진행하지 않는다.

현재 `NOTIFICATION_RELAY_ENABLED=false`로 두고, PushWorker·FCM 준비 후 개발 연동 시험을 진행할 때 켠다. 이미 저장한 PENDING이 30분을 넘었다면 켠 뒤 발행하지 않고 만료 처리한다. 과거 알림을 시험용으로 일괄 재발행하지 않는다.

## 1. 환경별 Standard 큐와 DLQ

AWS 콘솔의 서울 리전(`ap-northeast-2`)에서 팀 전용 리소스를 준비한다. 기존 이미지 처리 큐를 재사용하지 않는다. 회사 공유 계정에서 생성 권한이 부족하면 [서버 구축 안내](infrastructure-setup.md)의 `#8기-기술-검토` 경로로 요청한다.

| 환경 | 발행·소비 큐 이름(제안) | DLQ 이름(제안) |
| --- | --- | --- |
| dev | `chalkak-dev-push` | `chalkak-dev-push-dlq` |
| prod | `chalkak-prod-push` | `chalkak-prod-push-dlq` |

DLQ를 먼저 만든 뒤 원본 큐에 연결한다. 둘 다 Standard이며 같은 계정·리전을 사용한다.

| 설정 | 원본 큐 | DLQ |
| --- | --- | --- |
| Visibility Timeout | 60초 | 기본 설정 유지 |
| 메시지 보관 기간 | 기본 4일 | 14일(1,209,600초) |
| Receive message wait time | 20초 | 기본 설정 유지 |
| Dead-letter queue | 해당 환경 DLQ, `maxReceiveCount=10` | 해당 원본 큐만 허용 |

`maxReceiveCount`는 최초 수신을 포함한 SQS 메시지 수신 횟수의 한도다. FCM SDK 내부 재시도와 처리 중 Visibility Timeout 연장은 이 횟수를 증가시키지 않는다. PushWorker는 처리 중 숨김 시간을 연장하여 재전달을 줄이고, 일시 실패가 남으면 메시지를 삭제하지 않아 다음 수신에서 재처리한다. 60초마다 정확히 10회 시도하거나 모든 최종 실패가 DLQ에 들어간다는 뜻은 아니다. 원래 30분 발송 기한이 먼저 지나면 Worker의 만료 처리 기준을 따른다.

원본 큐의 Redrive policy는 반복 실패 메시지를 DLQ로 격리하기 위한 설정이다. **DLQ에서 원본 큐로 다시 보내는 자동·수동 Redrive는 하지 않는다.** Standard 메시지의 만료는 최초 큐 입력 시각 기준이므로 DLQ 이동 뒤 14일이 새로 시작되지 않는다. 조사 로그는 #494에서 별도 30일 보관을 구성한다. [AWS DLQ 문서](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-dead-letter-queues.html)

각 원본 큐의 URL·ARN과 DLQ ARN을 기록하고 dev·prod 연결을 구분한다. 메시지의 실제 푸시 기한은 큐 보관 기간과 별개로 사건 발생 후 30분이다.

## 2. 기존 EC2 instance profile의 권한 확인

서버는 AWS SDK `DefaultCredentialsProvider`로 기존 EC2 역할 자격증명을 사용한다. 별도 알림 서버·개인 Access Key 발급은 필요하지 않다. 회사 제공 `ec2-project` 역할을 임의 변경하지 않고 담당자에게 팀 큐 ARN 범위의 권한을 요청한다.

| 목적 | 권한 | 대상 |
| --- | --- | --- |
| Relay 발행 | `sqs:SendMessage` | 해당 환경 원본 큐 ARN |
| 설정 확인 | `sqs:GetQueueAttributes` | 원본 큐·DLQ ARN |
| 다음 단위 PushWorker | `sqs:ReceiveMessage`, `sqs:DeleteMessage`, `sqs:ChangeMessageVisibility` | 해당 환경 원본 큐 ARN |

DLQ 소비·메시지 이동 권한은 일반 애플리케이션에 추가하지 않는다. SQS 관리 암호화(SSE-SQS)를 사용하면 별도 고객 관리 KMS 키 연동을 추가하지 않아도 된다. 기존 조직 정책이 SSE-KMS를 요구한다면 해당 KMS 권한을 함께 확인한다.

서버의 outbound HTTPS 접근도 필요하다. 로컬 S3 익명 접근 설정은 SQS 인증에 사용할 수 없다. 공유 계정의 개인 CLI 자격증명을 만들거나 채팅/Git에 자격증명을 올리지 않는다.

## 3. 환경변수 반영

개발·운영 서버 `/etc/chalkak/application.env`에 다음 키를 추가한다. 값은 해당 환경의 실제 큐를 사용한다.

- `NOTIFICATION_RELAY_ENABLED`: 준비 전 `false`. PushWorker·FCM 준비 후 개발 연동 시험에서 `true`.
- `NOTIFICATION_SQS_QUEUE_URL`: 해당 환경 원본 Standard 큐 URL.
- 기존 `AWS_REGION`: `ap-northeast-2` 유지.

예제는 [dev](../examples/application.dev.env.example)·[prod](../examples/application.prod.env.example)에 있다. CD는 파일 내용을 자동 수정하지 않는다. 사람이 반영한 뒤 `sudo systemctl restart chalkak-backend.service`를 실행한다.

정상 작업 확인 간격은 처리 완료 후 1초, 한 번에 최대 100건이다. 100건은 한 번에 조회하는 알림 수이며 각 알림은 별도 트랜잭션에서 순차 발행한다. SDK 호출의 전체 제한은 5초이며 SDK 내부 자동 재시도는 끈다. 일시 실패·수락 불명은 DB의 `next_attempt_at`에 1분 뒤를 기록하며, 기한을 넘기지 않는다. 네트워크 장애가 있으면 한 회차 실행 시간이 늘 수 있으므로 1초 이내 전달을 보장한다는 뜻은 아니다.

## 4. 실제 AWS 개발 연동 확인 — 이번 PR 구현 완료 후 수행

1. 개발 큐 URL·ARN, 원본 큐 Redrive policy/Visibility, DLQ 보관 기간 및 EC2 역할 권한을 콘솔에서 확인한다.
2. 개발 환경에 테스트 회원·게시물로 승인 또는 반려 알림을 생성한다. 승인·반려 푸시 설정은 켜 둔다. 테스트 알림 ID·사건 ID를 기록한다.
3. 개발 Relay를 켜고 해당 알림이 `PENDING → PUBLISHED`로 바뀌며 `sqs_published_at`이 남는지 확인한다.
4. `type=notification`, `stage=relay`, `result=SQS_ACCEPTED` 로그의 `eventId`, `notificationId`, `sqsMessageId`를 확인한다. 뒤이어 `stage=relay_transaction_committed`가 있어야 DB 반영까지 끝난 것이다.
5. Worker가 메시지를 수신하여 최신 조건을 확인하고 FCM에 요청하는지 확인한다. 서버의 FCM 수락과 Android·iOS 테스트 기기의 실제 수신을 별도로 확인한다. 성공 시 SQS 메시지를 삭제하는지도 확인한다. Worker가 소비 중인 큐를 콘솔에서 반복 Poll하면 수신 횟수가 증가하므로 처리 추적은 로그를 우선한다.
6. 제어된 일시 오류로 SQS 재수신, 처리 중 숨김 시간 연장, 최대 수신 한도 초과 후 DLQ 이동을 검증한다. 설정은 60초·10회이고, 발송 기한은 사건 발생 후 30분이다. 기한이 먼저 지난 경우와 반복 실패로 DLQ에 격리된 경우를 구분하여 기록한다. 자동·수동 Redrive는 수행하지 않는다.
7. 실패는 `errorCode`와 상태로 확인하고 권한·URL을 수정한다. 영구 오류의 `FAILED`를 자동 PENDING으로 되돌리는 기능은 없다. 시험 결과와 운영 활성화 여부는 구분한다.

실제 AWS 성공 결과는 이 절차를 실행한 뒤 기록한다. 현재 개발 큐 URL과 사용자의 권한 확인 완료 보고만 확보한 상태이며, 실제 발행·Worker 처리·FCM 수신은 미실행이다. Worker·FCM 관련 항목은 다음 구현 단위의 검증 계획이며 현재 완료된 기능이 아니다.

## 로그 조회

기존 CloudWatch 수집 로그에서 다음처럼 조회할 수 있다. 별도 운영 조회 API는 만들지 않는다.

```sql
fields @timestamp, stage, eventId, notificationId, sqsMessageId, result, errorCode, nextAttemptAt
| filter type = "notification" and notificationId = "확인할 알림 UUID"
| sort @timestamp asc
```

`SQS_ACCEPTED`만 있고 DB 트랜잭션이 실패하면 다음 실행에서 같은 사건이 중복 발행될 수 있다. `PUBLISHED`는 SQS 수락 상태이지 FCM 요청 수락·기기 수신·클릭을 뜻하지 않는다. DB 발행 횟수·오류 이력 컬럼이나 별도 Outbox·push_attempts는 추가하지 않는다. 경보와 로그 보관 설정 완성은 #494 범위다.
