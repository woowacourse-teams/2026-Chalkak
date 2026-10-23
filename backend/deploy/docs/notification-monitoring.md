# 알림 지표·경보 구성

#494의 알림 전용 경보 설정안이다. 저장소 반영만으로 AWS 리소스가 생성되지 않는다.
개발 검증 후 해당 환경에 콘솔로 적용한다. 기존 [모니터링 구축](monitoring.md)의
SNS → Lambda → Slack 경로와 회사 제공 IAM 역할을 사용한다.

## 1. DB 발행 정체 관측

`NotificationPublicationMonitor`가 dev·prod에서 시작 기준 60초 간격(`fixedRate`)으로 PENDING 건수와
가장 오래된 건의 생성 후 경과 시간을 기록한다. Relay 전용 스케줄러와 분리되어
Relay가 꺼졌거나 SQS 응답을 기다리는 중에도 관측한다. 조회는 읽기 전용 트랜잭션이며
쿼리 실행 제한은 5초다. 연결 확보 대기 등까지 포함한 전체 호출의 5초 보장은 아니다.
공용 스케줄러는 4개 스레드를 사용하고 Worker·Relay·주제 발행은 별도 스케줄러를 사용한다.
조회 시간을 매번 다음 간격에 더하지 않지만 공용 작업·DB·로그 전송 지연으로 실제 관측은 늦어질 수 있다.
그래서 DB 대기·관측 누락 경보는 아래의 5분 평가 구간으로 짧은 유입 지연을 견딘다.

- `pendingCount`: 재시도 대기와 기한을 넘겼지만 아직 정리되지 않은 PENDING까지 포함한다.
- `oldestPendingAgeSeconds`: 다음 시도 시각이 아닌 생성 시각 기준 나이. 대기 없음은 0이다.
- 조회 실패: `publication_snapshot_failed`만 남기며 정상 0건을 만들지 않는다.
- 주제·리마인더는 DB 발행 대기가 없으므로 이 수치에 포함하지 않는다. 각 회차 집계로 확인한다.

모니터가 꺼지거나 DB·수집 경로가 실패하면 정상 관측 자체가 사라진다. 이를 감지하는
관측 누락 경보도 함께 설정한다. 앱 중단 여부는 기존 런타임 하트비트와 대조한다.

## 2. CloudWatch 메트릭 필터

로그 그룹 `/chalkak/{env}/notification`에서 다음 필터를 생성한다.
`{env}`는 `dev` 또는 `prod`다. 네임스페이스는 `Chalkak/{env}/Notification`,
**디멘션은 추가하지 않는다**. 사용자·사건 ID를 디멘션으로 넣어 지표 수를 늘리지 않는다.

| 지표 이름 | 필터 패턴 | 지표 값 | 단위 | 기본값 |
| --- | --- | --- | --- | --- |
| `PendingPublicationCount` | `{ $.type = "notification" && $.stage = "publication_snapshot" }` | `$.pendingCount` | Count | 없음 |
| `OldestPendingPublicationAge` | `{ $.type = "notification" && $.stage = "publication_snapshot" }` | `$.oldestPendingAgeSeconds` | Seconds | 없음 |
| `PublicationMonitorHeartbeat` | `{ $.type = "notification" && $.stage = "publication_snapshot" }` | `1` | Count | 없음 |
| `SqsAccepted` | `{ $.type = "notification" && $.stage = "relay" && $.result = "SQS_ACCEPTED" }` | `1` | Count | `0` |
| `PublicationErrors` | `{ $.type = "notification" && (($.stage = "relay" && ($.result = "SQS_FAILED" \|\| $.result = "SQS_UNKNOWN" \|\| $.result = "FAILED")) \|\| $.stage = "relay_transaction_failed" \|\| $.stage = "topic_publication_result" \|\| $.stage = "topic_publication_unknown" \|\| $.stage = "topic_scheduler_failed" \|\| $.stage = "topic_execution_failed" \|\| $.stage = "reminder_publication_result" \|\| $.stage = "reminder_publication_unknown" \|\| $.stage = "reminder_scheduler_failed" \|\| $.stage = "reminder_execution_failed") }` | `1` | Count | `0` |
| `PublicationFailed` | `{ $.type = "notification" && $.stage = "relay" && $.publishStatus = "FAILED" }` | `1` | Count | `0` |
| `PublicationExpired` | `{ $.type = "notification" && $.stage = "relay" && $.publishStatus = "EXPIRED" }` | `1` | Count | `0` |
| `FcmConfigurationErrors` | `{ $.type = "notification" && $.stage = "fcm_result" && ($.errorCode = "THIRD_PARTY_AUTH_ERROR" \|\| $.errorCode = "SENDER_ID_MISMATCH" \|\| $.errorCode = "UNAUTHENTICATED" \|\| $.errorCode = "PERMISSION_DENIED") }` | `1` | Count | `0` |
| `WorkerErrors` | `{ $.type = "notification" && ($.stage = "worker_processing_failed" \|\| $.stage = "worker_poll_failed" \|\| $.stage = "worker_visibility_failed" \|\| $.stage = "worker_device_failed" \|\| $.stage = "worker_invalid_token_cleanup_failed" \|\| ($.stage = "fcm_result" && $.result = "RETRYABLE")) }` | `1` | Count | `0` |
| `NotificationErrors` | `{ $.level = "ERROR" }` | `1` | Count | `0` |

`NotificationErrors`는 알림 전용 그룹의 모든 ERROR 로그를 집계한다. 단계 이름이나 오류 코드를
나열하지 않아 `worker_invalid_message`, `fcm_result=PERMANENT_FAILURE`와 새로 추가되는
알림 처리 오류도 감지한다. `type`·`stage`가 빠진 ERROR도 포함한다. 이 필터는
`/chalkak/{env}/notification`에만 만들며 다른 로그 그룹에는 그대로 적용하지 않는다.
현재 파일 로그는 Logstash JSON 형식이므로 최상위 `level` 필드를 사용한다.
[Spring Boot Logstash 형식](https://docs.spring.io/spring-boot/reference/features/logging.html#features.logging.structured.logstash)

정상 수락·조건 미충족 생략은 INFO, 일시 오류와 무효 토큰은 WARN이므로 공통 ERROR 지표에는
포함하지 않는다. 일시 오류는 기존 `WorkerErrors`로 확인한다. `UNREGISTERED`의
`INVALID_TOKEN` 결과를 발송 형식 오류인 `PERMANENT_FAILURE`와 같은 오류로 취급하지 않는다.

`SqsAccepted`는 승인·반려 Relay의 SQS 수락 횟수다. 주제·리마인더 수락 건수는
`topic_publication_summary.acceptedCount`·`reminder_publication_summary.acceptedCount`로 조회한다. FCM 결과·SQS 수신 횟수·기기 처리 집계는
[로그 조회](notification-sqs-setup.md#로그-조회)로 조사하며 HTTP 요청 횟수로 해석하지 않는다.

오류 지표는 로그 발생 건수이므로 동일 사건의 재시도도 센다. DB 상태 로그는 커밋 전일 수 있어
`PublicationFailed`·`PublicationExpired`는 상태 전환 시도 관측값이다. 실제 반영은
`relay_transaction_committed` 또는 DB로 확인한다. `PublicationErrors`에 커밋 실패도 포함한다.
리마인더 단계(`reminder_*`)는 #726에서 추가했다. 이미 `PublicationErrors` 필터를 만든 환경은
저장소 반영만으로 바뀌지 않으므로, 콘솔에서 위 패턴으로 교체하고 패턴 테스트를 다시 한다.
스냅샷 지표에 기본값 0을 넣으면 관측 실패를 정상으로 오인할 수 있으므로 비워 둔다.
필터 기본값 0도 로그 유입이 전혀 없으면 생성되지 않는다.
[메트릭 필터 공식 설명](https://docs.aws.amazon.com/AmazonCloudWatch/latest/logs/MonitoringLogData.html)

## 3. 경보 설정표

다음 수치는 최초 운영값이다. 발송 기한 30분 전에 확인할 여유를 두고, 단발 일시 오류는
즉시 호출하지 않도록 정했다. 실제 개발 검증에서 유입 간격·오탐을 확인해 조정한다.
모든 경보의 ALARM·OK 동작은 `chalkak-{env}-alarms` SNS 토픽으로 연결한다.
계획된 중지·검증 중에는 해당 경보의 동작을 끄고 완료 후 다시 켠다.

| 이름 (`chalkak-{env}-push-` 접두사) | 네임스페이스 / 지표 | 통계 | 기간 | 조건 | 평가 N/M | 누락 데이터 | 먼저 확인할 것 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `db-backlog` | 위 전용 / OldestPendingPublicationAge | Maximum | 300초 | ≥ 300초 | 1/1 | notBreaching | Relay 활성화·DB PENDING·발행 오류. 큐가 비어 있어도 발생 가능 |
| `monitor-missing` | 위 전용 / PublicationMonitorHeartbeat | Sum | 300초 | < 1 | 2/2 | breaching | publication_snapshot_failed, 앱 하트비트, Agent 수집. 앱 중단으로 단정하지 않음 |
| `publication-errors` | 위 전용 / PublicationErrors | Sum | 60초 | ≥ 1 | 3/5 | notBreaching | Relay·주제·리마인더의 errorCode와 SQS 수락 불명 확인 |
| `publication-failed` | 위 전용 / PublicationFailed | Sum | 300초 | ≥ 1 | 1/1 | notBreaching | 권한·큐 URL·환경 설정, DB 커밋 여부 확인 |
| `publication-expired` | 위 전용 / PublicationExpired | Sum | 300초 | ≥ 1 | 1/1 | notBreaching | 발행 전 기한 초과, DB 정체와 커밋 여부 확인 |
| `fcm-configuration` | 위 전용 / FcmConfigurationErrors | Sum | 300초 | ≥ 1 | 1/1 | notBreaching | Firebase 프로젝트·서버 권한·APNs 설정. 키 원문을 공유하지 않음 |
| `worker-errors` | 위 전용 / WorkerErrors | Sum | 60초 | ≥ 1 | 3/5 | notBreaching | Worker 오류·FCM 일시 장애·수신 횟수·부분 성공 여부 |
| `notification-errors` | 위 전용 / NotificationErrors | Sum | 300초 | ≥ 1 | 1/1 | notBreaching | level=ERROR 로그의 stage·result·errorCode. 잘못된 메시지·FCM 형식 오류·새 단계 확인 |
| `queue-age` | AWS/SQS / ApproximateAgeOfOldestMessage | Maximum | 60초 | ≥ 300초 | 3/3 | notBreaching | Worker 실행·큐 적체·FCM 응답 지연 |
| `dlq` | AWS/SQS / ApproximateNumberOfMessagesVisible | Maximum | 60초 | ≥ 1 | 1/1 | notBreaching | 사건/큐 메시지 ID로 로그 조사. Redrive 하지 않음 |

`db-backlog`는 5분 구간에서 관측한 가장 오래된 대기가 5분 이상이면 경보를 발생시킨다.
`monitor-missing`은 5분 구간에 정상 스냅샷이 하나도 없는 상태가 두 구간 연속이면 발생한다.
정상 관측은 60초마다 유지하며 경보의 평가 구간만 넓힌다. 짧은 지연으로 비는 1분 구간을
곧바로 장애로 해석하지 않는 대신, 감지·복구 판단이 이전보다 늦어질 수 있다.

`notification-errors`는 5분 평가 구간에 ERROR가 한 건 이상 있으면 경보를 발생시킨다.
재시도하지 않고 완료하는 형식 오류도 놓치지 않도록 한 건부터 조사 대상으로 삼는다.
5분은 평가 구간이며, 매 로그마다 Slack 메시지를 보내거나 반드시 5분 뒤에 보내는 설정은 아니다.
이미 ALARM인 동안 추가 ERROR가 나도 상태가 바뀌지 않으면 새 통보를 보내지 않는다.
WARN 일시 오류를 반복 발생 조건으로 확인하는 기존 `worker-errors` 경보도 유지한다.

공통 ERROR 경보와 기존 단계별 경보는 같은 로그를 집계할 수 있다. 예를 들어 FCM 인증 오류는
`fcm-configuration`과 `notification-errors`를 모두 발생시킬 수 있으므로 Slack에 여러 통보가
올 수 있다. 사건·알림·SQS 메시지 ID를 대조해 같은 장애인지 확인한다. 지표 건수를 더해
고유 실패 건수로 해석하지 않으며, 이번 구성에는 경보 간 중복 통보 억제를 추가하지 않는다.

SQS 경보만 `QueueName` 디멘션을 지정한다. `queue-age`는 `chalkak-{env}-push`,
`dlq`는 `chalkak-{env}-push-dlq`다. DLQ의 `NumberOfMessagesSent`는 자동 이동 건수를
포함하지 않아 존재 감시에 사용하지 않는다. Standard 큐의 오래된 메시지 지표는 반복 실패
메시지가 제외될 수 있으므로 Worker 오류·DLQ 경보를 함께 본다.
[AWS SQS 지표](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-available-cloudwatch-metrics.html)

SNS 알림은 경보 상태 전환에 연결하고, 매 폴링/실패 로그에서 Slack을 직접 호출하지 않는다.
유지되는 ALARM의 반복 통보 자동화는 추가하지 않는다. 장애 하나가 DB·큐·FCM 경보를
동시에 발생시킬 수 있으며 단계별 증거로 확인한다.
[CloudWatch 경보](https://docs.aws.amazon.com/AmazonCloudWatch/latest/monitoring/CloudWatch_Alarms.html)

## 4. 적용·검증 순서

1. 전용 로그 그룹 30일 보관·Agent 수집을 먼저 적용하고 모니터 코드 배포 후 정상 스냅샷을 확인한다.
2. 표의 각 필터를 콘솔의 패턴 테스트에서 정상·오류 JSON으로 확인한 다음 저장한다.
   테스트 패턴 통과만으로 지표가 생기지는 않으며, 필터 생성 전 로그도 소급 집계되지 않는다.
   `NotificationErrors`는 아래 예시를 각각 한 줄 JSON으로 넣어 일치 여부를 확인한다.

   | 패턴 테스트 입력 | 기대 결과 |
   | --- | --- |
   | `{"level":"ERROR","type":"notification","stage":"worker_invalid_message"}` | 일치 |
   | `{"level":"ERROR","type":"notification","stage":"fcm_result","result":"PERMANENT_FAILURE","errorCode":"INVALID_ARGUMENT"}` | 일치 |
   | `{"level":"ERROR","stage":"new_notification_stage"}` | 일치: 미리 나열하지 않은 단계·type 누락 |
   | `{"level":"WARN","type":"notification","stage":"fcm_result","result":"INVALID_TOKEN","errorCode":"UNREGISTERED"}` | 불일치 |
   | `{"level":"WARN","type":"notification","stage":"fcm_result","result":"RETRYABLE"}` | 불일치: 기존 WorkerErrors에서 집계 |
   | `{"level":"INFO","type":"notification","stage":"worker_skipped"}` | 불일치 |

3. 개발 환경에서 실제 필터와 일치하는 로그가 유입된 뒤 지표를 선택해 경보를 생성한다.
   운영에 가짜 발송 오류를 넣지 않는다. 미발생 지표는 미구성 항목으로 명시해 담당자와 적용한다.
4. 개발 환경에서 통제한 발행 장애·Worker 오류·DLQ 격리로 ALARM과 복구 OK의 Slack 도착을 확인한다.
   잘못된 메시지와 FCM `INVALID_ARGUMENT` 로그가 공통 ERROR 지표에 집계되는지도 확인한다.
   WARN 무효 토큰·INFO 생략 로그가 공통 ERROR 지표를 올리지 않는지 확인하고,
   단계별 경보와 동시에 발생했을 때의 중복 통보도 기록한다.
   이 단계는 실제 발송에 영향을 주므로 팀과 테스트 시각·대상을 정하고 실행한다.
5. AWS 콘솔의 환경별 지표·경보와 수신 결과를 기록한 뒤 운영에 같은 구성을 적용한다.

사건별 로그 조회·DLQ 조사·개발 검증은 [알림 실패 조사](notification-investigation.md)를 따른다.
현재 문서는 구성 정의이며 AWS 생성·패턴 테스트·실제 경보 전달 검증은 별도 실행 사항이다.
전용 지표는 환경별 10개, 경보는 환경별 10개가 추가되며 AWS 사용량 과금 대상이다.
