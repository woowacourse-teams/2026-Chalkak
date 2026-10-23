# AI 리뷰 지표·경보 구성

#692의 AI 리뷰 운영 모니터링 구성이다. 저장소 반영만으로 AWS 리소스가 생성되지 않는다. prod 릴리스 뒤 아래 절차로 사람이 적용한다. 기존 [모니터링 구축](monitoring.md)의 SNS → Lambda → Slack 경로와 회사 제공 IAM 역할을 사용하고, 알림 도메인의 [알림 지표·경보 구성](notification-monitoring.md)과 같은 방식(로그 → 메트릭 필터 → 경보)이다.

- 운영 메트릭 필터와 경보는 **prod에만** 만든다. dev는 `test-metric-filter`로 패턴을 검증하고 저장 쿼리를 dev 로그 그룹에서 실행해 확인한다. dev 대시보드 구역은 없다.
- 별도 대기열 스냅샷 로그는 없다. 대기 시간 초과와 워커 정지는 기존 건별 로그(`queueWaitMs`, `review_requested`, 처리 로그)로 잡는다.
- 임계값은 최초 운영값이다. **2주 운영한 뒤** 오탐·놓침을 보고 조정한다(9절).
- 정의 기준은 이 문서의 표와 `backend/deploy/monitoring/dashboard.json`이다. 콘솔에서 바꾸면 같은 PR에서 이 문서를 고친다. 로그 필드의 의미는 [로깅·모니터링 설계](../../docs/monitoring/design.md)의 `ai_review` 행과 4.8절을 따른다.

## 1. 로그 신호

AI 리뷰 로그는 로거 `chalkak.aireview`가 남기며 별도 appender가 없어 `/chalkak/{env}/application` 그룹으로 간다. logstash 형식이라 모든 필드(`type`, `event`, `level`, `errorCode`, `queueWaitMs` 등)가 JSON 최상위 키다. 로그 한 줄 예시다.

```json
{"@timestamp":"2026-10-22T18:56:18.358845+09:00","@version":"1","message":"AI 리뷰 실패","logger_name":"chalkak.aireview","thread_name":"main","level":"WARN","level_value":30000,"type":"ai_review","event":"review_failed","queueWaitMs":1200,"totalMs":5000,"errorCode":"TIMEOUT"}
```

| `event` | 레벨 | 쓰는 곳 |
| --- | --- | --- |
| `review_requested` (`requestCount`, `created`) | INFO | `AiReviewRequested`, 재요청 수(S5) |
| `review_completed` (`queueWaitMs`, `totalMs`, `latencyMs`, 토큰) | INFO | `AiReviewProcessed`, `AiReviewQueueWaitMs`, 처리 시간 위젯 |
| `review_failed` (같은 필드 + `errorCode`) | WARN | `AiReviewFinalFailures`, `AiReviewProcessed`, `AiReviewQueueWaitMs`, `AiReviewLeaseRecovery`(`LEASE_EXHAUSTED`) |
| `review_retry_scheduled` (`errorCode`, `retryInMs`, `retryAfterUsed`, 토큰) | WARN | `AiReviewProcessed`, 사유별 실패, 재시도 비용(S3) |
| `review_abandoned` (`reason`) | WARN | `AiReviewProcessed`, `AiReviewLeaseRecovery`(`SHUTDOWN` 제외) |
| `review_stale_update` | WARN | 조사 쿼리(S4)만 |
| `result_notification_failed`, `worker_poll_failed` (`errorCode`) | ERROR | `AiReviewErrors` |

- `queueWaitMs`는 요청(재요청이면 재요청 시각)부터 워커가 처음 가져갈 때까지다. 자동 재시도 대기와 임대 회수 대기는 들어가지 않는다. `totalMs`는 요청부터 완료까지로 재시도 대기를 포함한다. `latencyMs`는 마지막 OpenAI 호출 한 번이다.
- 재시도를 예약한 시도에는 종료 로그가 없고 `review_retry_scheduled`가 남는다. 종료 로그(`review_completed`·`review_failed`)는 회차마다 한 번이다.
- `POST_UNAVAILABLE`은 작성자가 게시물을 지우는 등 시스템 문제가 아닌 실패라 실패 급증 경보에서 뺀다.
- 프롬프트·피드백·이미지 주소는 로그에 없다. 팀 계정은 제외하지 않는다(장애·비용 관측 지표).

## 2. 메트릭 필터

네임스페이스는 `Chalkak/prod`, 디멘션은 없다. 필터는 만든 뒤에 들어오는 로그만 센다.

### 일별 집계 4개 (기존 집계 로그)

`AiReviewUsers`, `AiReviewRequests`, `AiReviewCompleted`, `AiReviewFailed`는 `type=aggregation` 로그의 같은 이름 키(`aiReviewUsers` 등)를 읽는다. 사용자 지표 필터와 같은 방식이라 패턴·생성 CLI·기본값 없음 이유는 [모니터링 구축 4단계](monitoring.md#4-메트릭-필터)에 있다. 이 문서에서 다시 정의하지 않는다. 이름이 비슷한 운영 지표 `AiReviewRequested`(요청 로그 건수)와 혼동하지 않는다.

### 운영 6개 (로그 그룹 `/chalkak/prod/application`)

| 필터·지표 이름 | 패턴 | 값 | 기본값 | 단위 |
| --- | --- | --- | --- | --- |
| `AiReviewFinalFailures` | `{ $.type = "ai_review" && $.event = "review_failed" && $.errorCode != "POST_UNAVAILABLE" }` | `1` | `0` | Count |
| `AiReviewQueueWaitMs` | `{ $.type = "ai_review" && ($.event = "review_completed" \|\| $.event = "review_failed") }` | `$.queueWaitMs` | 없음 | Milliseconds |
| `AiReviewLeaseRecovery` | `{ $.type = "ai_review" && (($.event = "review_abandoned" && $.reason != "SHUTDOWN") \|\| ($.event = "review_failed" && $.errorCode = "LEASE_EXHAUSTED")) }` | `1` | `0` | Count |
| `AiReviewErrors` | `{ $.type = "ai_review" && $.level = "ERROR" }` | `1` | `0` | Count |
| `AiReviewRequested` | `{ $.type = "ai_review" && $.event = "review_requested" }` | `1` | `0` | Count |
| `AiReviewProcessed` | `{ $.type = "ai_review" && ($.event = "review_completed" \|\| $.event = "review_failed" \|\| $.event = "review_retry_scheduled" \|\| $.event = "review_abandoned") }` | `1` | `0` | Count |

표의 `\|\|`는 마크다운 표 때문에 이스케이프한 것이다. 실제 패턴에는 `||`를 쓴다(CLI 블록 참고).

- `AiReviewQueueWaitMs`에는 기본값을 두지 않는다. 기본값 0을 두면 다른 로그 줄마다 0 샘플이 들어가 통계가 오염된다(`LatencyMs`와 같은 이유). 경보는 Maximum만 쓰지만 대시보드 그래프도 깨지지 않게 한다.
- 카운트 지표의 기본값 0은 기존 `Server5xx`와 같다. 앱이 살아 있으면 1분마다 `type=runtime` 로그가 들어오므로 0이 계속 발행된다. 이것이 워커 정지 수식이 "앱은 살아 있음"을 전제로 동작하는 근거다.
- `AiReviewProcessed`에는 `review_abandoned`를 넣는다. 배포 재시작 때 `SHUTDOWN` 중단 뒤 임대 만료(120초)까지 처리 로그가 없어 생기는 워커 정지 오탐을 막는다. 진짜 정지에서는 중단 로그도 나오지 않으므로 감지를 잃지 않는다.
- `AiReviewFinalFailures`와 `AiReviewLeaseRecovery`는 `LEASE_EXHAUSTED` 최종 실패를 둘 다 센다. 같은 원인으로 경보 두 개가 함께 울릴 수 있고, 중복 통보 억제는 넣지 않는다.
- `AiReviewErrors`는 `type=ai_review`로 한정한다. 앱 전체 ERROR가 아니라 AI 리뷰의 `worker_poll_failed`, `result_notification_failed`만 센다.
- `!=`는 필드가 있는 줄에서만 비교한다고 가정한다. 코드상 `review_failed`와 `review_abandoned`에는 `errorCode`·`reason`이 항상 있어 문제 없지만, 적용 전 `test-metric-filter`로 확인한다(6-1).

## 3. 경보

모두 prod 토픽 `chalkak-prod-alarms`로 ALARM과 OK를 보낸다. 누락 데이터는 모두 `notBreaching`이라 지표가 생기기 전에 CLI로 만들어도 ALARM이 되지 않는다.

| 이름 | 지표 (`Chalkak/prod`) | 통계 | 기간 | 조건 | 평가 (N of M) | 누락 데이터 |
| --- | --- | --- | --- | --- | --- | --- |
| `chalkak-prod-ai-review-failures` | `AiReviewFinalFailures` | Sum | 900초 | `>= 3` | 1 of 1 | `notBreaching` |
| `chalkak-prod-ai-review-queue-wait` | `AiReviewQueueWaitMs` | Maximum | 300초 | `>= 300000` (5분, ms) | 1 of 1 | `notBreaching` |
| `chalkak-prod-ai-review-lease-recovery` | `AiReviewLeaseRecovery` | Sum | 300초 | `>= 1` | 1 of 1 | `notBreaching` |
| `chalkak-prod-ai-review-errors` | `AiReviewErrors` | Sum | 300초 | `>= 1` | 1 of 1 | `notBreaching` |
| `chalkak-prod-ai-review-worker-stalled` | metric math `e1` (아래) | Sum (각 지표) | 900초 | `e1 >= 1` | 2 of 2 | `notBreaching` |

### 워커 정지 경보의 수식

```text
req  = Chalkak/prod AiReviewRequested, Sum, 900초 (ReturnData=false)
proc = Chalkak/prod AiReviewProcessed, Sum, 900초 (ReturnData=false)
e1   = IF(FILL(req, 0) >= 1 AND FILL(proc, 0) == 0, 1, 0)   (ReturnData=true)
조건: e1 >= 1, 2 of 2(연속한 두 15분 구간), 누락 데이터 notBreaching
```

평가할 때마다 가장 최근 15분 구간 두 개가 **둘 다** `요청 >= 1 AND 처리 로그 == 0`일 때만 울린다. CloudWatch는 매분 가장 최근 구간을 다시 평가하므로 한 구간 조건만으로는 요청 직후 처리 로그가 남기 전의 평가에서 바로 울린다. 그래서 두 구간 연속 조건을 쓴다(근거는 아래 한계 절).

metric math 경보는 식에 들어간 지표 수(2개)만큼 과금된다.

### 경보 설명 (Slack "먼저 확인할 것")

설명 문구는 CLI의 `--alarm-description`에 그대로 쓴다.

- `failures`: `AI 리뷰가 15분 안에 3건 이상 최종 실패했습니다(게시물 삭제 제외). 먼저 저장 쿼리 "chalkak/ai-review/사유별 실패"로 errorCode를 보세요. QUOTA_EXCEEDED면 OpenAI 프로젝트 예산·결제를, RATE_LIMITED·TIMEOUT·UPSTREAM_ERROR가 몰렸으면 OpenAI 상태 페이지를, PROMPT_VERSION_UNAVAILABLE이면 최신 프롬프트 버전 모델을 확인하세요.`
- `queue-wait`: `처리를 끝낸 AI 리뷰 중 처음 가져가기까지 5분 넘게 기다린 건이 있습니다(재시도 대기 제외). 대시보드 AI 리뷰 구역의 요청·처리 건수와 OpenAI 응답 시간을, 로그에서 worker_poll_failed·review_abandoned를 확인하세요. 아직 처리되지 않은 대기는 이 경보가 재지 못합니다.`
- `lease-recovery`: `AI 리뷰 처리가 중단돼 임대 만료 뒤 다시 처리되거나 시도 상한(LEASE_EXHAUSTED)으로 실패했습니다. 저장 쿼리 "chalkak/ai-review/이상 이벤트"에서 reason과 errorCode, 같은 시각의 type=error 로그를 확인하세요.`
- `errors`: `AI 리뷰 ERROR 로그가 발생했습니다. event가 worker_poll_failed면 DB 연결을, result_notification_failed면 알림 로그 그룹의 같은 시각 오류를 확인하세요.`
- `worker-stalled`: `연속한 두 15분 구간(30분) 동안 AI 리뷰 요청은 있었지만 처리 로그(완료·실패·재시도·중단)가 없습니다. 앱은 살아 있는데 워커만 멈췄을 수 있습니다. 먼저 chalkak-prod-app-heartbeat가 OK인지 보고, journalctl -u chalkak-backend 에서 ai-review-poll 스레드 오류와 worker_poll_failed를 확인하세요. 요청이 드문 시간대라면 최근 30분의 요청이 실제로 처리됐는지(처리 로그가 뒤늦게 나왔는지) 먼저 보세요.`

### 임계값 근거와 한계

임계값은 사용량이 적은 출시 초기 기준의 초안이다.

| 경보 | 근거 | 못 잡는 경우 | 오탐이 생길 수 있는 경우 |
| --- | --- | --- | --- |
| `failures` | 최종 실패는 자동 3회 시도 뒤에만 남아 1건은 개별 사진 문제일 수 있다. 3건이면 OpenAI 장애, `QUOTA_EXCEEDED`(예산 소진), 잘못된 프롬프트 버전 같은 계통 문제다. 사용량이 적어 비율보다 건수가 안정적이다 | 15분에 2건씩 계속 실패하는 느린 악화. 재시도로 결국 성공하는 건(`review_retry_scheduled`만 늘어남)은 세지 않는다. 사유별 급증은 대시보드와 S1로 본다 | `LEASE_EXHAUSTED` 3건이 몰리면 `lease-recovery`와 함께 울린다. 한 사용자가 여러 게시물을 연속 재요청해 실패하면 3건이 한 사람 몫일 수 있다 |
| `queue-wait` | 평소 폴링은 1초, 동시 10건·OpenAI 60초 제한이면 몰려도 수십 초다. 5분이면 사용자가 앱에서 "처리 중"을 오래 본다 | **처리를 끝낸 건만 잰다.** 아직 가져가지 못한 대기는 못 재고, 워커가 완전히 멈추면 끝내 울리지 않는다(`worker-stalled`가 보완). 밀린 건이 끝난 뒤에야 울려 경보 지연은 대기 + 처리 시간(최악 약 4~5분)이다 | 재시도·회수 대기는 `queueWaitMs`에 들어가지 않아 재시도가 많아도 오탐이 없다. 배포 재시작 직후 몰린 건이 늦게 가져가지면 1회 울릴 수 있다 |
| `lease-recovery` | 종료가 아닌 중단과 소진 실패는 평소 0이어야 한다. 배포 재시작의 `SHUTDOWN`은 빼서 배포마다 울리지 않게 한다 | 로그 없이 프로세스가 죽은 경우(비정상 종료). 임대 만료 뒤 재가져가기 순간에는 로그가 없다. 이 경우는 `chalkak-prod-app-heartbeat`가 담당한다 | `LEASE_EXHAUSTED`는 `failures`와 겹쳐 중복 통보된다 |
| `errors` | ERROR는 1건부터 조사한다(`notification-errors`와 같은 원칙). DB 장애로 폴링 실패가 1초마다 나도 상태 전환 1번만 통보된다 | 로그 수집 실패(Agent 중지)로 ERROR가 안 들어오는 경우. WARN 수준 이상 징후(`review_stale_update`)는 세지 않는다 | `result_notification_failed` 한 건(그 회차 알림 하나가 빠짐, 결과는 확정됨)도 울린다. DB 일시 장애는 `worker_poll_failed`로 울린다 |
| `worker-stalled` | 요청이 있는데 처리 로그가 연속한 두 15분 구간(30분)에 없으면 폴러 스레드 정지·세마포어 자리 누수·워커 비활성 배포 같은 "앱은 살아 있는데 워커만 멈춤"이다 | ① 요청이 드물어 한 구간에만 요청이 있으면 잡지 못한다(`queue-wait` 경보와 하트비트가 일부 보완). ② 정지 직전에 끝난 건의 처리 로그가 두 구간 중 하나에 있으면 그 구간은 정상으로 보인다. ③ 처리 로그가 조금이라도 나오는 부분 정지는 `queue-wait`가 늦게 잡는다. ④ 앱 중단은 하트비트가 담당. ⑤ 요청이 없으면 울리지 않는다(기다리는 사용자가 없음) | 연속한 두 15분 구간이 모두 "요청은 있는데 처리 로그 0"이어야 하므로 정상 요청 1건이나 처리 직전 평가만으로는 울리지 않는다. 남는 경우는 요청 트랜잭션이 롤백돼 행 없이 요청 로그만 남는 때(드묾, 받아들임)와 두 구간 내내 처리가 멈춘 정상 점검 정도다 |

`worker-stalled`의 평가 조건과 한계다.

- CloudWatch는 매분 직전 기간을 다시 평가한다. 그래서 한 구간 조건만으로는 처리 이력이 없던 상태에서 정상 요청 1건이 들어오면, 처리 로그가 남기 전의 평가에서 바로 울린다. `Period=900`은 요청 뒤 15분을 기다려 주는 유예가 아니다.
- 그래서 두 구간 연속 조건(`--evaluation-periods 2 --datapoints-to-alarm 2`)으로 바꿨다. 정상 요청 1건이나 처리 직전 평가만으로는 울리지 않고, 오탐이 나려면 연속한 두 15분 구간이 모두 "요청은 있는데 처리 로그 0"이어야 한다.
- 감지 지연: 정지 뒤 요청이 두 구간(최대 30분)에 걸쳐 계속 들어와야 잡히므로 약 15~30분 걸린다.
- 놓치는 경우: 요청이 드물어 한 구간에만 요청이 있으면 잡지 못한다. 이때는 `queue-wait` 경보와 하트비트가 일부를 보완한다.
- 근거: [CloudWatch 경보 평가](https://docs.aws.amazon.com/AmazonCloudWatch/latest/monitoring/alarm-evaluation.html)

두 대기 관련 경보의 역할 분담이다.

| 상황 | `queue-wait` | `worker-stalled` | 담당 |
| --- | --- | --- | --- |
| 몰림으로 대기가 길어짐(워커는 돎) | 밀린 건이 끝날 때 울림 | 처리 로그가 있어 안 울림 | `queue-wait` |
| 워커 완전 정지, 앱은 살아 있음 | 안 울림 | 정지 뒤 요청이 두 15분 구간에 걸쳐 계속 들어오면 약 15~30분 안에 울림 | `worker-stalled` |
| 워커가 아주 느려짐 | 늦게 끝난 건에서 울림 | 안 울림 | `queue-wait` (늦게) |
| 앱 중단 | 안 울림 | 안 울림 (로그 없음 → 누락 → `notBreaching`) | `chalkak-prod-app-heartbeat` |

누락 데이터를 `notBreaching`으로 둔 이유: 앱이 살아 있으면 두 지표 모두 0이 발행되어 데이터가 비지 않는다. 데이터가 비는 것은 앱 중단·Agent 중지뿐이고, 그건 하트비트 경보의 몫이라 이 경보는 OK로 둔다.

## 4. Logs Insights 저장 쿼리

저장 쿼리 5개는 prod 로그 그룹 `/chalkak/prod/application` 기준으로 만든다. 이름은 `chalkak/ai-review/<이름>`이다. dev에서 확인할 때는 콘솔에서 저장 쿼리를 불러온 뒤 로그 그룹을 `/chalkak/dev/application`으로 바꿔 실행한다. 조회 기간은 짧게(예: 1시간~1일) 잡는다.

### S1. 사유별 실패

```text
filter type = "ai_review" and event in ["review_failed", "review_retry_scheduled"]
| stats count(*) as n by event, errorCode
| sort n desc
```

`review_failed`는 최종 실패, `review_retry_scheduled`는 자동 재시도 예약이다. 대시보드의 같은 이름 위젯과 같은 쿼리다.

### S2. OpenAI 응답 시간 (모든 호출)

```text
filter type = "ai_review" and event in ["review_completed", "review_failed", "review_retry_scheduled"] and ispresent(latencyMs)
| stats count(*) as calls, pct(latencyMs, 50) as p50, pct(latencyMs, 95) as p95, max(latencyMs) as maxMs by model, promptVersion
```

`review_completed`·`review_failed`의 `latencyMs`는 마지막 시도만이라 재시도 예약 행까지 합쳐야 모든 호출이 된다(중복 없음).

### S3. 재시도 비용 (토큰)

```text
filter type = "ai_review" and event in ["review_completed", "review_failed", "review_retry_scheduled"]
| stats count(*) as calls, sum(inputTokens) as input, sum(cachedInputTokens) as cached,
        sum(outputTokens) as output, sum(reasoningTokens) as reasoning by event
```

`review_retry_scheduled` 행이 재시도로 쓴 토큰이고, 나머지 두 행은 회차의 마지막 시도다. 토큰은 응답을 받은 경우에만 있다.

### S4. 이상 이벤트

```text
filter type = "ai_review" and event in ["review_abandoned", "review_stale_update", "result_notification_failed", "worker_poll_failed"]
| fields @timestamp, event, reason, errorCode, aiReviewId, postId, attempt, requestId
| sort @timestamp desc
| limit 50
```

`lease-recovery`·`errors` 경보를 받았을 때 먼저 본다. `review_stale_update`는 경보가 없고 이 쿼리로만 본다.

### S5. 요청·재요청 수

```text
filter type = "ai_review" and event = "review_requested"
| stats count(*) as n by created, bin(1d)
```

`created=true`가 새 행(최초 요청), `false`가 실패한 리뷰의 재요청이다. 일별 집계의 `aiReviewRequests`는 최초 요청만 세므로 재요청은 이 쿼리로 본다.

- `bin(1d)`는 UTC 경계(KST 09:00)다. KST 일별 정확값은 DB 집계로 본다.
- `created`는 JSON 불리언(`true`/`false`)이다. Logs Insights에서 어떻게 묶여 표시되는지는 dev에서 한 번 확인한다.

## 5. 대시보드

`backend/deploy/monitoring/dashboard.json`의 "prod AI 리뷰" 구역이다. 적용은 [모니터링 구축 9단계](monitoring.md#9-대시보드)대로 `DASHBOARD-chalkak`의 소스에 붙여 넣는다.

| 위젯 | 종류 | 내용 |
| --- | --- | --- |
| 구역 제목 | text | 일별 집계는 내부 계정 제외, 실시간 로그 위젯은 제외하지 않는다는 안내 |
| prod AI 리뷰 알람 | alarm | 이 문서의 경보 5개 |
| 일별 사용 | metric | `AiReviewUsers`, `AiReviewRequests`, `AiReviewCompleted`, `AiReviewFailed` (86400초, Maximum) |
| 요청 / 처리 건수 | metric | `AiReviewRequested`, `AiReviewProcessed` (300초, Sum) |
| 대기 시간 최댓값 | metric | `AiReviewQueueWaitMs` (300초, Maximum), 5분 기준선 표시. 처리를 끝낸 건 기준 |
| 처리 시간 p50 / p95 | log | `queueWaitMs`, `totalMs`, `latencyMs` 백분위 (1시간) |
| 처리 결과 추이 | log | 완료·실패·재시도 예약 건수 (1시간) |
| 사유별 실패 | log | S1과 같은 쿼리 (표) |

- 새 알람은 알람 위젯에 이름으로 들어 있으므로 알람을 추가하면 위젯에도 수동으로 추가한다.
- 로그 위젯 3개는 대시보드를 열 때마다 prod 응용 로그 그룹을 조회한다. 기본 조회 기간(24시간)을 유지한다.
- 일별 집계 위젯은 로그를 조회하지 않아 기간을 늘려도 비용이 늘지 않는다.

## 6. 적용 절차 (CLI)

공유 계정은 개인 access key를 발급할 수 없어 로컬 PC에서는 CLI를 쓰지 않는다. SSM으로 **dev EC2에 접속해 인스턴스 역할(`ec2-project`) 권한으로** 실행한다. CloudWatch API는 리전 단위라 dev EC2에서 prod 로그 그룹·경보를 만들 수 있다. prod EC2에는 디스크 때문에 AWS CLI가 없다. 권한이 없으면 7절의 콘솔 절차로 같은 값을 만든다.

### 적용 순서

1. prod 릴리스: 일별 집계 4키와 AI 리뷰 로그가 prod에 나오는 버전이다.
2. 6-1 패턴 검증: 리소스를 만들지 않는다. 기대와 다르면 3으로 가지 않는다.
3. 6-2 메트릭 필터 10개(집계 4 + 운영 6). 집계 필터 4개는 [모니터링 구축 4단계](monitoring.md#cli로-만들기-선택) CLI에 들어 있다. 첫 01:00(KST) 집계 전에 만들거나 관리자 API로 전날을 다시 집계한다.
4. 6-3 경보 5개. 모두 `notBreaching`이라 지표가 생기기 전에 만들어도 ALARM이 되지 않는다.
5. 6-4 저장 쿼리 5개.
6. 대시보드 반영(5절).
7. 6-5 전달 확인 후 6-6 체크리스트.

### 공통 변수

아래 셸 블록은 bash 문법(`local`, 배열)이다. SSM Session Manager의 기본 셸이 sh일 수 있으므로 먼저 `bash`를 실행해 전환한다.

```bash
# dev EC2(SSM)에서 실행한다. 인스턴스 역할 권한으로 prod 리소스를 만든다.
REGION=ap-northeast-2
LOG_GROUP=/chalkak/prod/application
NS=Chalkak/prod
TOPIC_ARN=arn:aws:sns:ap-northeast-2:843255971531:chalkak-prod-alarms
TAGS="Key=Service,Value=techcourse Key=Role,Value=techcourse-etc Key=ProjectTeam,Value=chalkak"
```

### 6-1. 패턴 검증

`test-metric-filter`는 리소스를 만들지 않고 샘플 로그 줄이 패턴에 일치하는지만 본다. 응답의 `eventNumber`는 0부터 세서 표(1부터)와 어긋나므로, 각 샘플 JSON 맨 앞에 `"sample":N` 필드를 넣고 출력은 일치한 줄의 `sample` 번호로 읽는다(필드는 어떤 패턴의 일치 여부도 바꾸지 않는다). 샘플은 앱이 실제로 내는 필드 이름으로 만든 줄이다(`level` 포함). 공유 계정은 삭제가 제한되므로 잘못 만들지 않게 반드시 먼저 한다.

```bash
# 샘플 로그 14줄. 각 줄의 "sample" 값이 아래 기대 결과의 줄 번호(1부터)다. 줄 순서와 번호를 같게 유지한다.
SAMPLES=(
  '{"sample":1,"level":"INFO","type":"ai_review","event":"review_requested","requestCount":1,"created":true}'
  '{"sample":2,"level":"INFO","type":"ai_review","event":"review_requested","requestCount":2,"created":false}'
  '{"sample":3,"level":"INFO","type":"ai_review","event":"review_completed","queueWaitMs":1200,"totalMs":9000,"latencyMs":7000}'
  '{"sample":4,"level":"WARN","type":"ai_review","event":"review_failed","queueWaitMs":1500,"totalMs":70000,"errorCode":"TIMEOUT"}'
  '{"sample":5,"level":"WARN","type":"ai_review","event":"review_failed","queueWaitMs":800,"totalMs":900,"errorCode":"POST_UNAVAILABLE"}'
  '{"sample":6,"level":"WARN","type":"ai_review","event":"review_failed","queueWaitMs":2000,"totalMs":125000,"errorCode":"LEASE_EXHAUSTED"}'
  '{"sample":7,"level":"WARN","type":"ai_review","event":"review_retry_scheduled","errorCode":"RATE_LIMITED","retryInMs":10000,"retryAfterUsed":false}'
  '{"sample":8,"level":"WARN","type":"ai_review","event":"review_abandoned","reason":"SHUTDOWN"}'
  '{"sample":9,"level":"WARN","type":"ai_review","event":"review_abandoned","reason":"DB_ERROR","errorCode":"DataAccessResourceFailureException"}'
  '{"sample":10,"level":"ERROR","type":"ai_review","event":"worker_poll_failed","errorCode":"DataAccessResourceFailureException"}'
  '{"sample":11,"level":"ERROR","type":"ai_review","event":"result_notification_failed","errorCode":"DataAccessResourceFailureException"}'
  '{"sample":12,"level":"WARN","type":"ai_review","event":"review_stale_update"}'
  '{"sample":13,"level":"INFO","type":"runtime","threads":80}'
  '{"sample":14,"level":"INFO","type":"aggregation","metricDate":"2026-10-21","dau":12,"aiReviewUsers":5,"aiReviewRequests":7,"aiReviewCompleted":6,"aiReviewFailed":1}'
)

# 패턴을 넣으면 일치한 줄의 "sample" 번호를 공백으로 구분해 한 줄로 출력한다.
# 응답의 eventNumber는 0부터 세므로 쓰지 않는다.
tf() {
  aws logs test-metric-filter --region "$REGION" --filter-pattern "$1" \
    --log-event-messages "${SAMPLES[@]}" --query 'matches[].eventMessage' --output text \
    | grep -o '"sample":[0-9]*' | cut -d: -f2 | tr '\n' ' '; echo
}

tf '{ $.type = "ai_review" && $.event = "review_failed" && $.errorCode != "POST_UNAVAILABLE" }'
tf '{ $.type = "ai_review" && ($.event = "review_completed" || $.event = "review_failed") }'
tf '{ $.type = "ai_review" && (($.event = "review_abandoned" && $.reason != "SHUTDOWN") || ($.event = "review_failed" && $.errorCode = "LEASE_EXHAUSTED")) }'
tf '{ $.type = "ai_review" && $.level = "ERROR" }'
tf '{ $.type = "ai_review" && $.event = "review_requested" }'
tf '{ $.type = "ai_review" && ($.event = "review_completed" || $.event = "review_failed" || $.event = "review_retry_scheduled" || $.event = "review_abandoned") }'
tf '{ $.type = "aggregation" && $.aiReviewUsers >= 0 }'
```

| 필터 | 기대 `sample` 번호 | 확인 포인트 |
| --- | --- | --- |
| `AiReviewFinalFailures` | `4 6` | 5(`POST_UNAVAILABLE`)는 빠진다 |
| `AiReviewQueueWaitMs` | `3 4 5 6` | 7(재시도 예약)은 빠진다 |
| `AiReviewLeaseRecovery` | `6 9` | 8(`SHUTDOWN`)은 빠진다 |
| `AiReviewErrors` | `10 11` | WARN·다른 `type`은 빠진다 |
| `AiReviewRequested` | `1 2` | 재요청(`created:false`)도 센다 |
| `AiReviewProcessed` | `3 4 5 6 7 8 9` | 12(`review_stale_update`)는 빠진다 |
| 집계 4개 (`aiReviewUsers`, `aiReviewRequests`, `aiReviewCompleted`, `aiReviewFailed`를 각각) | `14` | 다른 줄은 빠진다 |

기대와 다르면 만들지 않고 패턴을 고친다. 이어서 dev 로그 그룹의 실제 줄로도 한 번 확인한다. Logs Insights에서 `/chalkak/dev/application`에 `filter type = "ai_review" | limit 20`을 실행해 줄을 복사해 `SAMPLES`에 넣어 보면(`sample` 번호는 새 줄에도 붙인다) JSON 최상위 키와 불리언 표기가 샘플과 같은지 볼 수 있다.

### 6-2. 메트릭 필터 생성

```bash
# mf 이름 패턴 값 기본값(없으면 -) 단위
mf() {
  local tr="metricName=$1,metricNamespace=$NS,metricValue=$3,unit=$5"
  [ "$4" != "-" ] && tr="$tr,defaultValue=$4"
  aws logs put-metric-filter --region "$REGION" --log-group-name "$LOG_GROUP" \
    --filter-name "$1" --filter-pattern "$2" --metric-transformations "$tr"
}

mf AiReviewFinalFailures '{ $.type = "ai_review" && $.event = "review_failed" && $.errorCode != "POST_UNAVAILABLE" }' 1 0 Count
mf AiReviewQueueWaitMs   '{ $.type = "ai_review" && ($.event = "review_completed" || $.event = "review_failed") }' '$.queueWaitMs' - Milliseconds
mf AiReviewLeaseRecovery '{ $.type = "ai_review" && (($.event = "review_abandoned" && $.reason != "SHUTDOWN") || ($.event = "review_failed" && $.errorCode = "LEASE_EXHAUSTED")) }' 1 0 Count
mf AiReviewErrors        '{ $.type = "ai_review" && $.level = "ERROR" }' 1 0 Count
mf AiReviewRequested     '{ $.type = "ai_review" && $.event = "review_requested" }' 1 0 Count
mf AiReviewProcessed     '{ $.type = "ai_review" && ($.event = "review_completed" || $.event = "review_failed" || $.event = "review_retry_scheduled" || $.event = "review_abandoned") }' 1 0 Count
```

확인한다. 운영 6개와 집계 4개 이름이 나와야 한다.

```bash
aws logs describe-metric-filters --region "$REGION" --log-group-name "$LOG_GROUP" \
  --filter-name-prefix AiReview --query 'metricFilters[].filterName' --output text
```

### 6-3. 경보 생성

`put-metric-alarm`은 지표가 아직 없어도 만들 수 있다. 같은 이름으로 다시 실행하면 값을 덮어쓴다.

```bash
# al 이름 지표 통계 기간 임계값 설명
al() {
  aws cloudwatch put-metric-alarm --region "$REGION" \
    --alarm-name "chalkak-prod-ai-review-$1" --alarm-description "$6" \
    --namespace "$NS" --metric-name "$2" --statistic "$3" --period "$4" \
    --evaluation-periods 1 --datapoints-to-alarm 1 \
    --threshold "$5" --comparison-operator GreaterThanOrEqualToThreshold \
    --treat-missing-data notBreaching \
    --alarm-actions "$TOPIC_ARN" --ok-actions "$TOPIC_ARN" --tags $TAGS
}

al failures       AiReviewFinalFailures  Sum     900 3      'AI 리뷰가 15분 안에 3건 이상 최종 실패했습니다(게시물 삭제 제외). 먼저 저장 쿼리 "chalkak/ai-review/사유별 실패"로 errorCode를 보세요. QUOTA_EXCEEDED면 OpenAI 프로젝트 예산·결제를, RATE_LIMITED·TIMEOUT·UPSTREAM_ERROR가 몰렸으면 OpenAI 상태 페이지를, PROMPT_VERSION_UNAVAILABLE이면 최신 프롬프트 버전 모델을 확인하세요.'
al queue-wait     AiReviewQueueWaitMs    Maximum 300 300000 '처리를 끝낸 AI 리뷰 중 처음 가져가기까지 5분 넘게 기다린 건이 있습니다(재시도 대기 제외). 대시보드 AI 리뷰 구역의 요청·처리 건수와 OpenAI 응답 시간을, 로그에서 worker_poll_failed·review_abandoned를 확인하세요. 아직 처리되지 않은 대기는 이 경보가 재지 못합니다.'
al lease-recovery AiReviewLeaseRecovery  Sum     300 1      'AI 리뷰 처리가 중단돼 임대 만료 뒤 다시 처리되거나 시도 상한(LEASE_EXHAUSTED)으로 실패했습니다. 저장 쿼리 "chalkak/ai-review/이상 이벤트"에서 reason과 errorCode, 같은 시각의 type=error 로그를 확인하세요.'
al errors         AiReviewErrors         Sum     300 1      'AI 리뷰 ERROR 로그가 발생했습니다. event가 worker_poll_failed면 DB 연결을, result_notification_failed면 알림 로그 그룹의 같은 시각 오류를 확인하세요.'

# 워커 정지 (metric math)
aws cloudwatch put-metric-alarm --region "$REGION" \
  --alarm-name chalkak-prod-ai-review-worker-stalled \
  --alarm-description '연속한 두 15분 구간(30분) 동안 AI 리뷰 요청은 있었지만 처리 로그(완료·실패·재시도·중단)가 없습니다. 앱은 살아 있는데 워커만 멈췄을 수 있습니다. 먼저 chalkak-prod-app-heartbeat가 OK인지 보고, journalctl -u chalkak-backend 에서 ai-review-poll 스레드 오류와 worker_poll_failed를 확인하세요. 요청이 드문 시간대라면 최근 30분의 요청이 실제로 처리됐는지(처리 로그가 뒤늦게 나왔는지) 먼저 보세요.' \
  --evaluation-periods 2 --datapoints-to-alarm 2 \
  --threshold 1 --comparison-operator GreaterThanOrEqualToThreshold \
  --treat-missing-data notBreaching \
  --metrics '[
    {"Id":"req","ReturnData":false,"MetricStat":{"Metric":{"Namespace":"Chalkak/prod","MetricName":"AiReviewRequested"},"Period":900,"Stat":"Sum"}},
    {"Id":"proc","ReturnData":false,"MetricStat":{"Metric":{"Namespace":"Chalkak/prod","MetricName":"AiReviewProcessed"},"Period":900,"Stat":"Sum"}},
    {"Id":"e1","ReturnData":true,"Label":"처리 없는 요청 구간","Expression":"IF(FILL(req, 0) >= 1 AND FILL(proc, 0) == 0, 1, 0)"}
  ]' \
  --alarm-actions "$TOPIC_ARN" --ok-actions "$TOPIC_ARN" --tags $TAGS
```

확인한다. 5개 모두 나와야 하고, 지표가 생기기 전에는 `INSUFFICIENT_DATA`일 수 있다.

```bash
aws cloudwatch describe-alarms --region "$REGION" --alarm-name-prefix chalkak-prod-ai-review- \
  --query 'MetricAlarms[].[AlarmName,StateValue,TreatMissingData]' --output text
```

### 6-4. 저장 쿼리 생성

같은 이름으로 다시 만들면 중복될 수 있으니 먼저 목록을 확인한다.

```bash
aws logs describe-query-definitions --region "$REGION" --query-definition-name-prefix chalkak/ai-review/ \
  --query 'queryDefinitions[].[name,queryDefinitionId]' --output text

# sq 이름 쿼리
sq() {
  aws logs put-query-definition --region "$REGION" --name "chalkak/ai-review/$1" \
    --log-group-names "$LOG_GROUP" --query-string "$2"
}

sq '사유별 실패' 'filter type = "ai_review" and event in ["review_failed", "review_retry_scheduled"]
| stats count(*) as n by event, errorCode
| sort n desc'
sq 'OpenAI 응답 시간' 'filter type = "ai_review" and event in ["review_completed", "review_failed", "review_retry_scheduled"] and ispresent(latencyMs)
| stats count(*) as calls, pct(latencyMs, 50) as p50, pct(latencyMs, 95) as p95, max(latencyMs) as maxMs by model, promptVersion'
sq '재시도 비용' 'filter type = "ai_review" and event in ["review_completed", "review_failed", "review_retry_scheduled"]
| stats count(*) as calls, sum(inputTokens) as input, sum(cachedInputTokens) as cached,
        sum(outputTokens) as output, sum(reasoningTokens) as reasoning by event'
sq '이상 이벤트' 'filter type = "ai_review" and event in ["review_abandoned", "review_stale_update", "result_notification_failed", "worker_poll_failed"]
| fields @timestamp, event, reason, errorCode, aiReviewId, postId, attempt, requestId
| sort @timestamp desc
| limit 50'
sq '요청·재요청 수' 'filter type = "ai_review" and event = "review_requested"
| stats count(*) as n by created, bin(1d)'
```

### 6-5. 전달 확인

상태를 잠시 ALARM으로 바꾸면 SNS → Lambda → Slack으로 prod 채널에 메시지가 가고, 다음 평가에서 실제 상태로 돌아온다. prod 채널 참여자에게 미리 알린다. 한 경보만 확인해도 경로는 같다.

```bash
aws cloudwatch set-alarm-state --region "$REGION" \
  --alarm-name chalkak-prod-ai-review-failures --state-value ALARM --state-reason "Slack 전달 확인"
```

`set-alarm-state` 권한이 없으면 [모니터링 구축 10단계](monitoring.md#10-알람-전달-종단-확인)의 임시 알람 방식을 쓴다.

### 6-6. 적용 후 체크리스트

- [ ] 6-1의 기대 `sample` 번호가 모두 맞았다.
- [ ] 운영 필터 6개와 집계 필터 4개(`AiReview` 접두사)가 prod 로그 그룹에 있다.
- [ ] 콘솔 `CloudWatch > 지표 > Chalkak/prod`에서 `AiReviewRequested`, `AiReviewProcessed`가 보인다(앱이 살아 있으면 기본값 0으로 발행). `AiReviewQueueWaitMs`는 첫 AI 리뷰 처리 뒤에 생긴다.
- [ ] 경보 5개가 있고(`worker-stalled`는 평가가 2 of 2) 누락 데이터 처리가 `notBreaching`이며 알림 대상이 `chalkak-prod-alarms`다.
- [ ] `worker-stalled` 그래프가 콘솔에서 보인다. 두 지표가 비어 있을 때 `FILL`이 0을 채우는지 빈 결과를 내는지 확인한다(어느 쪽이든 식이 1이 되지는 않는다).
- [ ] 저장 쿼리 5개를 dev 로그 그룹에서 실행해 결과가 나온다. S5의 `created` 표시와 `bin(1d)` 경계를 확인한다.
- [ ] 대시보드 AI 리뷰 구역에 데이터가 표시된다.
- [ ] 6-5로 Slack 도착을 확인했다.
- [ ] 첫 01:00(KST) 집계 뒤 `AiReviewUsers` 등 집계 지표 4개가 생긴다.

## 7. IAM 권한이 없을 때 (콘솔 절차)

기존 절차로 만들어 온 리소스(경보·대시보드·메트릭 필터·저장 쿼리) 기준으로 `PutMetricAlarm`, `PutDashboard`, `PutMetricFilter`, `PutQueryDefinition`은 쓸 수 있을 것으로 보이며, 적용 시 확인한다. `logs:TestMetricFilter`, `cloudwatch:SetAlarmState`, 경보 태그(`cloudwatch:TagResource`)는 확인하지 못했다. `AccessDenied`가 나는 단계만 아래로 대신한다. 값은 이 문서의 표와 같다.

| 막힌 단계 | 콘솔 대체 |
| --- | --- |
| `test-metric-filter` | CloudWatch > 로그 그룹 `/chalkak/prod/application` > 지표 필터 생성 화면의 **패턴 테스트**에 샘플 줄을 하나씩 붙여 넣어(`sample` 필드 포함) 일치 여부를 6-1 표의 `sample` 번호와 비교. 저장은 하지 않는다 |
| `put-metric-filter` | 같은 화면에서 패턴·필터 이름·네임스페이스 `Chalkak/prod`·지표 이름·값·기본값·단위를 2절 표대로 입력. 태그는 지표 필터에 붙일 수 없다 |
| `put-metric-alarm` (태그 권한 때문이면 `--tags` 줄만 빼고 다시 실행해 본다) | CloudWatch > 경보 > 경보 생성. 단일 지표 경보는 [모니터링 구축 8단계](monitoring.md#8-알람) 흐름 그대로, 통계·기간·조건·누락 데이터는 3절 표대로. 알림은 ALARM과 OK 모두 `chalkak-prod-alarms`. 태그 3개는 입력란이 있으면 붙인다 |
| 워커 정지 경보(metric math) | 지표 선택에서 `AiReviewRequested`(ID `req`), `AiReviewProcessed`(ID `proc`)를 모두 Sum·15분으로 추가하고 **수식 추가**에 `IF(FILL(req, 0) >= 1 AND FILL(proc, 0) == 0, 1, 0)`을 입력한 뒤 `req`·`proc`의 그래프 표시를 끄고 수식만 남긴다. 조건은 `>= 1`, **2 of 2**(경보 조건의 "M 중 N" 입력란에 2와 2), 누락 데이터 `notBreaching`. 지표가 한 번도 발행되지 않았으면 콘솔에서는 선택할 수 없어 필터가 지표를 발행한 뒤 만든다 |
| `put-query-definition` | Logs Insights에서 로그 그룹을 고르고 4절 쿼리를 붙여 넣은 뒤 **저장**, 이름은 `chalkak/ai-review/<이름>` |
| `set-alarm-state` | 모니터링 구축 10단계의 임시 알람 |

권한 문제가 계속되면 `#8기-기술-검토`에 문의한다. 공용 역할은 직접 수정하지 않는다.

## 8. 되돌리기와 삭제 제약

공유 계정은 삭제 권한이 제한되므로 **만들기 전에 검증하는 것**(6-1)이 가장 중요하다. 같은 이름으로 다시 실행하면 값이 덮어써지므로 잘못된 값은 대개 삭제 없이 고칠 수 있다.

| 대상 | 고치기 | 지우기 |
| --- | --- | --- |
| 메트릭 필터 | 같은 필터 이름으로 `put-metric-filter`를 다시 실행하면 패턴·값이 바뀐다. 지표 이름을 바꾸면 이전 지표가 남는다 | `aws logs delete-metric-filter --region "$REGION" --log-group-name "$LOG_GROUP" --filter-name <이름>`. 이미 발행된 지표 데이터는 만료될 때까지 남는다 |
| 경보 | 같은 이름으로 `put-metric-alarm`을 다시 실행 | `aws cloudwatch delete-alarms --region "$REGION" --alarm-names <이름...>`. 막히면 `aws cloudwatch disable-alarm-actions --alarm-names <이름...>`로 알림만 끄고 `#8기-기술-검토`에 삭제를 요청 |
| 저장 쿼리 | `put-query-definition`에 `--query-definition-id`를 주면 갱신 | `aws logs delete-query-definition --region "$REGION" --query-definition-id <ID>` |
| 대시보드 | `dashboard.json`을 다시 붙여 넣기 | 이 구역만 지우려면 소스에서 AI 리뷰 위젯을 빼고 저장 |

- 롤백 순서는 의존하는 것부터다. 경보 → 메트릭 필터 → 저장 쿼리 → 대시보드 위젯. 로그 그룹은 건드리지 않는다.
- 배포 점검이나 계획된 중지 때 소음을 막으려면 삭제 대신 해당 경보의 알림만 끈다(`disable-alarm-actions`, 끝나면 `enable-alarm-actions`).
- 일별 집계 필터 4개의 삭제는 [모니터링 구축 되돌리기](monitoring.md#되돌리기)를 따른다.

## 9. 2주 운영 후 조정

임계값은 초안이다. 운영 2주 뒤 다음을 보고 조정한다.

- `worker-stalled` 오탐 빈도: 오탐이 있으면 원인을 로그로 확인한다. 반대로 요청이 드문 시간대의 정지를 놓친 사례가 있으면 구간 길이(15분)와 연속 조건(2 of 2)을 다시 판단한다.
- `failures`: 실제 최종 실패율과 사유 분포를 보고 3건/15분이 너무 민감한지, 비율 기준이 필요한지 판단한다.
- `queue-wait`: 실제 `queueWaitMs`의 p95·최댓값 분포를 보고 5분이 적절한지 본다. 배포 직후 오탐이 있으면 기록한다.
- `lease-recovery`, `errors`: 평소 0인지 확인한다. 반복되는 원인이 있으면 코드 문제로 이슈를 만든다.
- 조정은 이 문서의 3절 표와 설명, 설계 문서를 같은 PR에서 고치고 콘솔·CLI에 반영한다.

## 10. 알람 대응

| 알람 | 먼저 확인할 것 |
| --- | --- |
| `chalkak-prod-ai-review-failures` | 저장 쿼리 "사유별 실패"의 `errorCode`. `QUOTA_EXCEEDED`는 OpenAI 프로젝트 예산·결제, `RATE_LIMITED`·`TIMEOUT`·`UPSTREAM_ERROR`는 OpenAI 상태, `PROMPT_VERSION_UNAVAILABLE`은 최신 프롬프트 버전 모델 |
| `chalkak-prod-ai-review-queue-wait` | 대시보드 AI 리뷰 구역의 요청/처리 건수·처리 시간, 로그의 `worker_poll_failed`·`review_abandoned`. 동시 처리 자리가 모자란지(OpenAI 응답 시간 증가)와 배포 재시작 직후인지 확인 |
| `chalkak-prod-ai-review-lease-recovery` | 저장 쿼리 "이상 이벤트"의 `reason`·`errorCode`, 같은 시각 `type=error` 로그. `LEASE_EXHAUSTED`면 같은 게시물이 계속 중단된 것 |
| `chalkak-prod-ai-review-errors` | `event`가 `worker_poll_failed`면 DB 연결·Hikari, `result_notification_failed`면 알림 로그 그룹의 같은 시각 오류 |
| `chalkak-prod-ai-review-worker-stalled` | `chalkak-prod-app-heartbeat` 상태, 서버 `journalctl -u chalkak-backend`의 `ai-review-poll` 스레드 오류, `chalkak.ai-review.worker.enabled` 설정. 이어지는 15분에도 처리 로그가 나오는지 |

알람이 왔는데 Slack에 메시지가 없으면 Lambda `chalkak-alarm-notifier`의 로그 그룹에서 `alarm_notification_failed`를 검색한다.

## 11. 비용

CloudWatch 요금은 [공식 가격표](https://aws.amazon.com/cloudwatch/pricing/)에서 서울 리전 기준으로 확인한다. 아래는 대략적인 추정이다.

| 항목 | 증가 | 비고 |
| --- | --- | --- |
| 커스텀 메트릭 | +10 | 집계 4 + 운영 6. 메트릭 필터가 만든 메트릭도 커스텀 메트릭으로 과금된다 |
| 알람 | +5 (과금 단위 +6) | `worker-stalled`는 식에 쓴 지표 2개만큼 과금된다 |
| 저장 쿼리 | 없음 | |
| 대시보드 | 없음 | 기존 대시보드에 구역만 추가 |
| Logs Insights 조회 | 대시보드를 열 때마다 | 로그 위젯 3개가 prod 응용 로그 그룹을 조회한다. 저장 쿼리는 실행할 때만 |
| 로그 수집·저장 | 없음 | 새 로그 그룹이 없고 AI 리뷰 로그는 기존 그룹에 들어간다 |

- 단가를 지표 월 $0.30, 알람 지표 월 $0.10으로 가정하면 10 × $0.30 + 6 × $0.10 ≈ 월 $3.6 증가다(추정, 서울 리전 가격표로 확인할 것).
- [모니터링 구축 비용 점검](monitoring.md#비용-점검)의 일반 모니터링 수량(메트릭 21 → 31개, 알람 15 → 20개)에 더한 값이다. [알림 모니터링](notification-monitoring.md)의 환경별 지표·경보 10개씩이 따로 더해져 있어 월 $10 목표를 넘을 수 있다. 줄이려면 집계 지표 중 `AiReviewCompleted`·`AiReviewFailed`를 로그 필드로만 두는 방법(−2)이 있으나 이슈가 4개 모두 메트릭을 요구해 그대로 둔다.

## 공식 문서

- [메트릭 필터 패턴 문법](https://docs.aws.amazon.com/AmazonCloudWatch/latest/logs/FilterAndPatternSyntax.html)
- [test-metric-filter](https://docs.aws.amazon.com/cli/latest/reference/logs/test-metric-filter.html)
- [put-metric-alarm](https://docs.aws.amazon.com/cli/latest/reference/cloudwatch/put-metric-alarm.html)
- [CloudWatch 지표 수식 구문과 함수](https://docs.aws.amazon.com/AmazonCloudWatch/latest/monitoring/using-metric-math.html)
- [CloudWatch 요금](https://aws.amazon.com/cloudwatch/pricing/)
