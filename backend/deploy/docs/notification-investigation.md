# 알림 실패 조사와 개발 검증

이 문서는 승인·반려·주제·관리자 공지 알림의 실패 단계를 확인하는 순서다. 지표·경보 설정은
[알림 모니터링](notification-monitoring.md), 큐·서버 준비는 [SQS 연동 안내](notification-sqs-setup.md)를 따른다.
좋아요·클릭 보고 API는 현재 구현 범위에 포함하지 않는다.

## 1. 조사 범위와 식별자 확보

1. 개발/운영 환경, 사건 발생 시각, 알림 종류를 확인한다. 시간을 알고 있으면 조회 기간을 좁힌다.
2. CloudWatch 서울 리전 → 로그 → Logs Insights에서 `/chalkak/{env}/notification`을 선택한다.
   로그 분리 배포 전 사건은 `/chalkak/{env}/application`도 함께 선택한다.
3. 승인·반려와 회원 한 명의 공지는 `notificationId`, 주제는 `eventId + userId`를 확보한다.
   공지 전체의 발송 결과는 공지 ID가 `eventId`이므로 `eventId = "공지 UUID"`로 조회한다.
   주제 사건 ID를 모르면 `sourceId`(주제 ID)와 회원 ID로 수신 로그를 찾는다.
4. `worker_received`에서 `sqsMessageId`를 확인하여 같은 메시지의 재수신·기기별 결과를 연결한다.

```sql
fields @timestamp, stage, notificationId, eventId, userId, sourceId, sqsMessageId, receiveCount, deviceId, result, reason, errorCode
| filter type = "notification" and notificationId = "알림 UUID"
| sort @timestamp asc
```

주제는 필터를 `eventId = "사건 UUID" and userId = "회원 UUID"`로 바꾼다.
정상 주제 발행은 회차별 집계만 남기므로 회원별 발행 성공 로그가 없는 것만으로 누락을 단정하지 않는다.

```sql
fields @timestamp, stage, receiveCount, deviceId, result, reason, errorCode, retryable, acceptedCount, retryableCount, skippedCount
| filter type = "notification" and sqsMessageId = "SQS 메시지 ID"
| sort @timestamp asc
```

결과가 없으면 시간 범위·환경·배포 버전·Agent 수집부터 확인한다. 로그가 없다는 사실만으로
미발송을 확정하지 않는다. 알림 로그 보관은 30일이며, 로그 분리 전 기록은 기존 그룹의 기간을 따른다.
[Logs Insights 공식 안내](https://docs.aws.amazon.com/AmazonCloudWatch/latest/logs/AnalyzingLogData.html)

### 처리 지연 조사 — SQS 계측

아래 필드는 계측 배포 이후 기록에만 존재한다. 과거 발송의 지연을 소급해서 분해하지 않는다.
경과 시간은 `System.nanoTime()`으로 측정하고 밀리초 정수로 기록한다. CloudWatch 수집 시각의
차이를 계산하지 않으므로 Agent의 로그 일괄 수집 간격에 영향을 받지 않는다.

`SqsPushService`는 측정 시작·수신 시각과 처리 결과를 전달하고, `SqsPushTimingLogger`가
시간 계산과 로그 출력을 담당한다. 로그 객체는 메시지별 상태를 저장하지 않는다.

| 필드 / 로그 단계 | 측정 범위와 해석 |
| --- | --- |
| `sqsReceiveDurationMs` / `worker_sqs_receive`, `worker_poll_failed` | 수신 API 호출 시작부터 응답 또는 예외까지. `received=false`는 빈 응답이며 정상 롱 폴링 대기 최대 20초를 포함한다. 실패 시 1초 쉬는 시간은 제외한다 |
| `queueAgeMs` / `worker_attempt_completed` | SQS `SentTimestamp`부터 Worker 처리 진입 시각까지. **`receiveCount=1`**에서 최초 큐 대기를 조사한다. 재수신에서는 이전 처리·숨김·재시도 대기도 포함한다. 서버 시계 차이의 영향을 받으며, 입력 시각 누락·잘못된 값·미래 값은 `null`로 남긴다 |
| `sqsDeleteDurationMs` / `worker_message_deleted`, `worker_sqs_delete_failed` | 삭제 API 호출 시작부터 응답 또는 예외까지. 실패 시간은 INFO로 추가하고 기존 `worker_processing_failed` ERROR는 한 번만 남긴다 |
| `processingDurationMs` / `worker_attempt_completed` | 이번 한 건의 처리 진입부터 숨김 연장 정리와 삭제 또는 재전달 대기 설정을 마친 시점까지. 예외도 기록한다. 수신 API 호출·큐 대기·다음 재수신까지의 대기는 제외한다 |

`worker_attempt_completed`는 **처리 시도 종료**이며 발송 성공을 뜻하지 않는다. `outcome`은
`DELETED`, `INVALID_MESSAGE_DELETED`, `RETRY_PENDING`, `FAILED`다. `DELETED`에는 정상 생략과
영구 실패도 포함되므로 `worker_summary`·`worker_skipped`·`fcm_result`로 실제 결과를 확인한다.
삭제 실패는 응답 확인 실패일 수도 있어 실제 삭제 여부를 확정하지 않는다.

한 건의 로그는 `sqsMessageId + receiveCount`로 연결한다. 사건 ID·알림 ID는 기존
`worker_received`에서 해당 큐 식별자를 찾아 연결한다. 입력 메타데이터가 없는 메시지는 일부
필드가 비어 있을 수 있으므로 이를 0초 지연이나 최초 수신으로 간주하지 않는다.

```sql
fields @timestamp, stage, sqsMessageId, receiveCount, received, outcome, queueAgeMs, sqsReceiveDurationMs, sqsDeleteDurationMs, processingDurationMs, errorCode
| filter type = "notification" and sqsMessageId = "SQS 메시지 ID"
| sort @timestamp asc
```

최초 수신의 큐 대기와 처리 시간을 따로 집계한다. `count(queueAgeMs)`로 입력 시각이 유효한
표본 수도 확인한다. 관리자 공지 한 회차만 조사하려면 먼저 `eventId = "공지 UUID"`의
`worker_received`에서 확보한 큐 ID들로 `sqsMessageId in [...]` 필터를 추가한다.

```sql
filter type = "notification" and stage = "worker_attempt_completed" and receiveCount = "1"
| stats count(*) as attempts, count(queueAgeMs) as ageSamples,
    avg(queueAgeMs) as queueAvgMs, pct(queueAgeMs, 95) as queueP95Ms,
    avg(processingDurationMs) as processingAvgMs, pct(processingDurationMs, 95) as processingP95Ms
    by bin(1m)
```

수신 API 시간은 빈 응답과 실제 수신을 나눠 본다. 정상 20초 롱 폴링 대기를 메시지가 생긴 뒤
20초 더 기다린 것으로 해석하지 않는다. 수신 실패는 `worker_poll_failed`로 별도 조사한다.

```sql
filter type = "notification" and stage = "worker_sqs_receive"
| stats count(*) as requests, avg(sqsReceiveDurationMs) as avgMs,
    pct(sqsReceiveDurationMs, 95) as p95Ms by received
```

이번 SQS 계측만으로는 처리 시간 안의 DB와 FCM을 분리할 수 없다. 후속 DB·FCM 계측 후
내부 소요 시간을 비교한다. 수신 요청 시간·큐 대기·처리 시간은 서로 겹칠 수 있으므로 세 값을
단순히 더하지 않는다. 숨김 자동 연장은 별도 스레드에서 동작하며 처리 종료가 연장 완료를
기다린 시간은 전체 처리 시간에 포함된다. 수신·삭제·요약 로그가 늘어나는 만큼 수집·보관 비용도 증가한다.

## 2. 마지막으로 확인된 단계에서 다음 단계를 조사

| 확인된 기록 | 해석과 다음 확인 |
| --- | --- |
| DB `NOT_REQUIRED` | 생성 당시 승인·반려 수신 설정이 꺼져 발행 불필요. 알림함 기록은 남음 |
| 공지 `notice_distributed` 없음 / `notice_distribution_failed` | 공지가 알림함에 배포되지 않음. 배포 설정 `NOTIFICATION_NOTICE_DISTRIBUTION_ENABLED`와 `errorCode` 확인. 실패한 공지는 미배포로 남아 다음 분에 다시 배포 |
| DB `PENDING`, Relay 결과 없음 | `publication_snapshot`의 건수·나이, Relay 활성화·배포·실행 지연 확인 |
| `relay`의 `SQS_FAILED` | 재시도 가능한 거절. `errorCode`, `nextAttemptAt` 확인 |
| `SQS_UNKNOWN` 또는 주제 `UNKNOWN` | 수락 응답을 확인하지 못함. 이미 큐에 들어갔을 수도 있어 중복 가능성 확인 |
| `SQS_ACCEPTED` | SQS 수락. `relay_transaction_committed`로 DB 저장 여부 확인. 주제는 DB 발행 상태가 없음 |
| DB `FAILED` / `EXPIRED` | 영구 발행 오류 / 원래 기한 초과. 자동으로 PENDING으로 되돌리지 않음 |
| SQS 수락 후 `worker_received` 없음 | Worker 활성화·같은 환경 큐 URL·큐 적체·배포/수집 상태 확인 |
| `worker_skipped` | `reason` 확인. 읽음·참여·수신 설정·기한·로그인, 삭제된 게시물·공지(`NOTIFICATION_UNAVAILABLE`) 등의 정상 생략일 수 있음 |
| `fcm_result=ACCEPTED` | FCM이 요청 수락. 기기 수신·표시·클릭과 구분하고 앱 권한·실행 상태도 확인 |
| `worker_retry_pending` | 처리 중 연장을 종료하고 SQS 재전달 대기. SDK 반복과 별개이며 기한을 늘리지 않음 |
| `worker_message_deleted` | 현재 SQS 작업 완료. 수락·정상 생략·영구 실패 중 무엇인지 앞선 로그로 확인 |
| `worker_processing_failed` | 전체 처리 예외. 메시지를 유지하며 재전달 또는 DLQ 이동 가능 |

`worker_summary`는 기기별 처리 결과를 모은 값이다. 하나라도 수락했으면 다른 기기 실패가 있어도
전체 메시지를 완료한다. 전부 실패하고 일시 오류가 있으면 재처리한다. 수신 횟수는
`receiveCount`로 보며 SDK 내부 HTTP 재시도 횟수나 회원별 고유 발송 건수로 해석하지 않는다.
FCM 수락 후 SQS 삭제 실패나 수락 불명 재발행은 같은 기기의 중복 발송으로 이어질 수 있다.

## 3. 오류 코드에 따른 조치

| 오류 | 확인·처리 |
| --- | --- |
| SQS 권한·큐 설정 오류 | 해당 환경 큐 URL·리전·기존 EC2 역할 권한 확인. IAM 변경은 회사 담당자에게 요청 |
| FCM `UNAVAILABLE`, `INTERNAL`, `QUOTA_EXCEEDED` 등 일시 오류 | 재수신·Retry-After·원래 기한 확인. 부분 수락이 있었다면 전체 재시도하지 않음 |
| `UNREGISTERED` | 발송한 토큰이 여전히 같은 기기에 연결됐으면 기기 행 삭제. 정리 실패는 로그로 조사 |
| `INVALID_ARGUMENT` | 토큰 외에 메시지 형식 문제일 수 있음. 자동 기기 삭제 없이 발송 계약 확인 |
| `SENDER_ID_MISMATCH` | 앱 토큰 발급 프로젝트와 서버 프로젝트 일치 여부 확인 |
| `THIRD_PARTY_AUTH_ERROR` | iOS APNs 등의 인증 설정 확인 |
| `UNAUTHENTICATED`, `PERMISSION_DENIED` | 서버 인증·발송 권한 확인. 자격증명 원문을 로그·채팅에 복사하지 않음 |

프로젝트·인증·형식 오류는 유효한 기기를 지우지 않기 위해 보존한다. 설정을 고쳐도 이미 완료된
메시지가 자동 재발송되는 것은 아니다. [FCM 공식 오류 설명](https://firebase.google.com/docs/cloud-messaging/error-codes)

## 4. DLQ는 조사하고 원본 큐로 돌려보내지 않음

1. DLQ 경보의 환경·큐 이름을 확인한다. CloudWatch에는 `ApproximateNumberOfMessagesVisible`로 존재를 확인한다.
2. SQS 콘솔에서 해당 DLQ의 메시지를 조회해 메시지 ID와 본문의 사건·알림 식별자, 발생·만료 시각을 확인한다.
3. 위 식별자로 30일 조사 로그를 검색한다. DLQ 이동 시 큐 메시지 ID만으로 원본 로그가 연결된다고
   가정하지 말고 본문의 사건·알림 식별자도 사용한다.
4. 마지막 오류·수신 횟수·기한·부분 수락·배포 버전을 조사 기록에 남긴다.
   원문 메시지 덤프·토큰·키·제목·본문·반려 사유·사진 URL을 기록하지 않는다.
5. **DLQ의 재드라이브 시작을 누르지 않고**, 원본 큐로 복사하거나 자동 재처리하지 않는다.
   최종 실패는 조사 후 보관 기간에 따라 만료시킨다. 재발송 요청은 새 작업을 만들지 별도 정책 판단이 필요하다.

DLQ는 최대 14일로 설정하지만 Standard 메시지는 최초 원본 큐 입력 시각이 유지된다.
DLQ 도착 후 14일을 보장하지 않는다. 기한이 먼저 지나 Worker가 삭제하거나 영구 실패로 완료한
메시지는 DLQ에 없을 수 있다. 조사 기록 30일은 원본 메시지 30일 보관을 뜻하지 않으며 S3 보관은 추가하지 않는다.
[AWS DLQ 보관 설명](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-dead-letter-queues.html)

## 5. 배포 후 개발 검증 — 실행 전 팀과 시각·대상 합의

먼저 전용 그룹·Agent·메트릭 필터·경보를 구성한다. 정상 기기를 사용하는 검증과 실패 검증을
구분하고 운영 큐·실제 회원의 작업을 시험 대상으로 쓰지 않는다. 외부 장애를 만드는 방법·영향 범위는
검증 시점에 결정하며, 아래 표의 예상 결과만으로 검증 완료 표시를 하지 않는다.

| 검증 대상 | 개발 환경의 실행·확인 | 기대 결과 |
| --- | --- | --- |
| 정상 승인·반려 / 주제 / 공지 | 테스트 회원으로 실제 사건 생성(공지는 즉시 예약), 앱 기기에서 수신 확인 | 사건 → 큐 수신 → FCM 결과 → 삭제 로그 연결. 기기 수신은 별도 기록 |
| DB 발행 정체 | Relay를 잠시 끄고 테스트 알림 생성, 복구 후 다시 켬 | PENDING 나이 증가·db-backlog 경보·복구. 30분을 넘긴 건은 만료 |
| 발행 거절·수락 불명 | 승인된 개발 장애 조건으로 발행 요청·응답 경계를 실패시킴 | 오류 코드·DB 재시도·publication-errors 경보. 수락 불명 중복 가능성 확인 |
| FCM 일시 오류 | 통제된 개발 발송 장애를 재현, 해제 | 메시지 유지·재수신·조건 재검증·Worker 오류 경보. 수락한 기기의 결과 유지 |
| 관측/수집 누락 | 테스트 중 관측 또는 Agent 수집을 중단하고 복구 | monitor-missing 경보·OK. 기존 앱 하트비트와 구분 |
| DLQ 연결 | 격리된 개발 시험 큐·DLQ에서 삭제하지 않는 수신을 반복 | 수신 한도 10·자동 DLQ 격리·존재 경보 확인. 재드라이브하지 않음 |

DLQ 연결 시험만으로 실제 Worker의 FCM 오류 재처리까지 검증했다고 표시하지 않는다.
잘못된 JSON·만료·영구 FCM 오류는 Worker가 삭제하므로 DLQ 시험 입력으로 쓰지 않는다.
원본 큐 콘솔 Poll은 수신 횟수를 소비한다. 일반 조사는 로그를 우선하고, 의도한 수신 반복 시험은
격리 큐에서만 한다. 시험 큐 경보의 QueueName도 해당 시험 큐로 연결해 검증한 범위를 기록한다.

각 검증의 날짜·배포 SHA·대상 환경/큐·사건 식별자·경보 ALARM/OK 시각·Slack 도착 여부를
팀 운영 기록에 남긴다. 키·토큰 원문은 남기지 않는다. 실험 후 변경한 플래그·Agent·경보 동작을
원래 값으로 복구하고 남은 PENDING·큐 작업을 확인한다. 현재 #494의 실제 AWS 장애·경보·DLQ 검증은 미실행이다.
