# 로깅·모니터링 설계

- 상태: 구현 중 (#500~#503 구현, #504 계획)
- 관련 이슈: [#499 로깅 및 모니터링 구축](https://github.com/woowacourse-teams/2026-Chalkak/issues/499)

찰칵 MVP의 로그 스키마, 모니터링 항목, 사용자 지표 정의, 알람 원칙을 한 곳에 정리한다. 구축 절차와 알람·메트릭 필터의 정확한 값은 [CloudWatch 모니터링 구축 런북](../../deploy/docs/monitoring.md)이 기준이며, 이 문서와 어긋나면 런북이 옳다.

## 1. 목표

- 장애를 빠르게 발견하고 원인을 추적한다.
- 핵심 기능과 운영 흐름이 정상인지 안다.
- 실제로 장애 대응·버그 수정·기능 개선에 쓸 항목만 수집한다.

## 2. 구성 원칙

- **앱은 로그만 남긴다.** JSON 파일 로그 → CloudWatch Agent → 로그 그룹 순으로 흐른다. 메트릭은 메트릭 필터가 로그에서 만들고, 즉석 분석과 사용자 분석은 Logs Insights로 한다. 앱에는 메트릭 SDK가 없다.
- **모니터링 전용 코드는 최소로 둔다.** 기존 데이터(DB, 접근 로그)를 먼저 쓰고, 부족하면 로그 호출 몇 줄을 더하고, 그래도 안 되면 새 클래스를 만든다.
- **공유 회사 계정의 제약을 따른다.** CloudFormation, AWS Chatbot, IAM role 생성이 막혀 있다. 그래서 런북대로 콘솔에서 직접 만들고, Slack 알림은 SNS → Lambda(`chalkak-alarm-notifier`) → Incoming Webhook으로 보낸다.
- **비용 목표는 월 약 $10이다.** 팀의 목표 예산이며 견적이 아니다.
- **환경별 범위가 다르다.**

| 구분 | 로그 보존 | 범위 |
| --- | --- | --- |
| dev | 7일 | 성능과 장애 |
| prod | 60일 | 성능과 장애에 더해 사용자 지표와 장기 집계 |

```text
application.log (JSON)
  → CloudWatch Agent
  → 로그 그룹 /chalkak/{env}/application
  → 메트릭 필터 → 메트릭 → 알람 → SNS → Lambda → Slack
  → Logs Insights (대시보드 위젯, 즉석 조회)
```

## 3. 로그 스키마

dev·prod 프로필에서만 `/opt/chalkak/logs/application.log`에 logstash 형식 JSON으로 남긴다. local·test는 콘솔 출력만 유지한다. 모든 줄의 `type`으로 종류를 구분한다.

| `type` | 남기는 곳 | 필드 | 비고 |
| --- | --- | --- | --- |
| `access` | 요청마다 1줄 (`RequestIdFilter`) | `method`, `route`, `status`, `durationMs`, `requestId`, `userId` | `route`는 `/api/v1/posts/{postId}` 같은 템플릿이고, 보안 필터에서 거절돼 매칭되지 않은 요청은 `UNMATCHED`다. 원본 URL과 쿼리 문자열은 남기지 않는다. `userId`는 회원 토큰 요청에만 있고 관리자·비로그인 요청에는 없다. `/actuator/**`는 남기지 않는다. |
| `error` | 예외 처리 시 (`GlobalExceptionHandler`, 401·403 핸들러) | `errorCode`, `status`, `exception`, `requestId` | 4xx는 WARN이고 스택 트레이스가 없다. 5xx는 ERROR이고 `stack_trace`가 붙으며 `exception` 필드는 없다(스택 트레이스의 첫 줄이 예외 종류다). 예외 메시지는 사용자 입력이 섞일 수 있어 남기지 않는다. |
| `runtime` | dev·prod에서 1분마다 (`RuntimeSnapshotLogger`) | `heapUsedBytes`, `heapMaxBytes`, `gcTimeMsTotal`, `threads`, `hikariActive`, `hikariPending` | 측정하지 못한 값은 0이 아니라 키를 뺀다. JVM 관리 빈과 Hikari 풀 관리 빈에서 직접 읽는다. |
| `moderation` | 검수 흐름 (`PostCommandService`, `AdminPostCommandService`) | `event`(`pending`, `approved`, `rejected`), `postId`, `waitSeconds` | `waitSeconds`는 `approved`·`rejected`에만 있고 게시물 제출(`created_at`)부터 처리(`moderated_at`)까지의 초다. 트랜잭션 안에서 남기므로 드물게 롤백돼도 로그가 남을 수 있다. |
| `aggregation` | 집계 성공 시 | WAU, W+4 리텐션 값 | **#504에서 추가할 계획이며 아직 없다.** |

### 요청 추적

- 요청마다 UUID `requestId`를 만들어 로그 문맥(MDC)에 넣는다. 그 요청의 모든 로그 줄에 같은 값이 붙는다.
- 같은 값을 응답 헤더 `X-Request-Id`로 돌려준다. 사용자가 제보한 응답 헤더로 원인 로그를 찾는다.
- 필터는 Security 필터보다 먼저 실행되어 401·403 요청에도 `requestId`가 붙는다.
- 관리자 감사 로그(DB)의 요청 식별자도 같은 `requestId`를 저장한다(`CurrentRequestId`). 감사 기록에서 그 요청의 접근·에러 로그로 이어진다. 요청 스레드 밖에서는 새 UUID를 만든다.

## 4. 모니터링 항목과 수집 방식

상태 표기: **구현**은 이미 동작하는 것, **계획 #504**는 이슈에만 있고 아직 없는 것, **보류**는 하지 않기로 한 것이다.

### 4.1 API·에러

| 항목 | 수집 방식 | 확인하는 곳 | 상태 |
| --- | --- | --- | --- |
| API 요청 수 | 메트릭 필터 `RequestCount` (`type=access`) | 대시보드 요청 수 위젯 | 구현 #503 |
| 5xx 건수 | 메트릭 필터 `Server5xx` | 알람 `chalkak-{env}-5xx`, 대시보드 | 구현 #503 |
| 5xx 원인 (`errorCode`, `requestId`, `stack_trace`) | Logs Insights 위젯 | 대시보드 원인 분석 섹션 | 구현 #503 |
| 401·403 건수와 라우트 | Logs Insights 위젯 (`type=access`, `status in [401,403]`) | 대시보드 원인 분석 섹션 | 구현 #503 |

### 4.2 성능

| 항목 | 수집 방식 | 확인하는 곳 | 상태 |
| --- | --- | --- | --- |
| API 응답시간 p95·p99 | 메트릭 필터 `LatencyMs` (prod만, 기본값 없음) | 대시보드 prod 상태 섹션 | 구현 #503 |
| 라우트별 p95 상위 10 | Logs Insights 위젯 | 대시보드 원인 분석 섹션 | 구현 #503 |
| JVM 힙·스레드, Hikari active·pending | Logs Insights 위젯 (`type=runtime`) | 대시보드 prod 운영·사용자 섹션 | 구현 #501, #503 |

### 4.3 인프라

| 항목 | 수집 방식 | 확인하는 곳 | 상태 |
| --- | --- | --- | --- |
| EC2 상태 검사 | AWS 지표 `StatusCheckFailed` | 알람 `chalkak-{env}-ec2-status-check` | 구현 #503 |
| EC2 CPU | AWS 지표 | 대시보드 | 구현 #503 |
| 디스크 사용률 (dev·prod) | CloudWatch Agent `disk_used_percent` | 알람 `chalkak-{env}-disk-used`, 대시보드 | 구현 #503 |
| 메모리 사용률 (prod) | CloudWatch Agent `mem_used_percent` | 알람 `chalkak-prod-memory-used`, 대시보드 | 구현 #503 |
| RDS CPU·연결 수 | AWS 지표 | prod 대시보드 | 구현 #503 |
| RDS 여유 스토리지 | AWS 지표 | 알람 `chalkak-prod-rds-free-storage`, 대시보드 | 구현 #503 |
| prod 앱 생존 (하트비트) | 메트릭 필터 `RuntimeHeartbeat` (`type=runtime` 로그, 1분마다 1줄, prod만, 기본값 0) | 알람 `chalkak-prod-app-heartbeat` | 구현 #503 |
| RDS 읽기·쓰기 지연 | | | 보류 (7절) |
| CloudFront 요청·오류율 | | | 보류 (7절) |

### 4.4 이미지 파이프라인

| 항목 | 수집 방식 | 확인하는 곳 | 상태 |
| --- | --- | --- | --- |
| SQS 가장 오래된 메시지 나이 | AWS 지표 | 알람 `chalkak-shared-image-queue-age`, 대시보드 | 구현 #503 |
| SQS 대기 메시지 수 | AWS 지표 | 대시보드 | 구현 #503 |
| Lambda 호출·오류·스로틀·실행 시간 | AWS 지표 | 알람 `chalkak-shared-image-lambda-errors`, 대시보드 | 구현 #503 |
| 이미지 처리 포기 | Lambda 로그의 `image_processing_abandoned`에 붙인 메트릭 필터 | 알람 `chalkak-shared-image-processing-abandoned`, 대시보드 | 구현 #503 |
| SQS DLQ 건수 | | | 보류 (7절) |
| 미처리 이미지 게시물 수 | | | 보류 (7절) |

### 4.5 핵심 비즈니스

| 항목 | 수집 방식 | 확인하는 곳 | 상태 |
| --- | --- | --- | --- |
| 오늘(요청한 날짜) 주제 행 없음 | 메트릭 필터 `TopicNotFound` (`GET /api/v1/topics` 404, 요청한 날짜의 주제가 없을 때. 앱이 오늘 날짜로 조회한다는 전제). 주제 등록 누락이나 삭제를 잡고, 주제가 있어도 BEFORE_OPEN인 경우는 잡지 못한다 | 알람 `chalkak-prod-no-open-topic` | 구현 #503 |
| 검수 소요 시간 (일별 평균·p95) | Logs Insights 위젯 (`type=moderation`, `waitSeconds`) | 대시보드 prod 운영·사용자 섹션 | 구현 #501, #503 |
| 검수 대기가 오래된 게시물 | Logs Insights 위젯 (`postId`별 마지막 `event`가 `pending`, 조회 기간 안에 접수된 것만. 정확한 현황은 관리자 페이지) | 대시보드 prod 운영·사용자 섹션 | 구현 #501, #503 |
| 검수 소요 시간 장기 추세 | DB SQL (`posts.created_at`, `moderated_at`) | 리포트 쿼리 | 계획 #504 |
| 게시물 제출 성공·실패 수 | | | 보류 (7절) |

### 4.6 사용자 행동

정의는 5절에 있다. 사용자 지표는 prod 전용이다. dev 접근 로그의 `userId`는 집계 쿼리 검증에만 쓴다.

| 항목 | 수집 방식 | 확인하는 곳 | 상태 |
| --- | --- | --- | --- |
| DAU (일별) | Logs Insights 위젯 (`countDistinct(userId)`) | 대시보드 prod 운영·사용자 섹션 | 구현 #503 |
| WAU, W+4 리텐션 | Logs Insights 집계, 결과를 DB 집계 테이블에 저장 | `type=aggregation` 로그의 메트릭 필터, 대시보드 추이 위젯, 집계 중단 알람 | 계획 #504 |
| 일별 게시물 수·총 좋아요 수 | DB SQL (`posts`, `post_likes`) | 리포트 쿼리 | 계획 #504 |
| 주제 조회 사용자 수 | Logs Insights (`GET /api/v1/topics` 200의 고유 `userId`) | 집계 테이블 | 계획 #504 |
| 주제별 피드 조회 수 | 피드 조회 접근 로그의 `topicDate` 필드 | 집계 테이블 | 계획 #504 |
| 참여 가능 사용자 수 | DB SQL (날짜별 스냅샷) | 리포트 쿼리 | 계획 #504 |
| API 요청 수·5xx 비율·평균·p95 일별 요약 | Logs Insights 집계 | 집계 테이블 | 계획 #504 |
| 주제 알림 발송 사용자 수 | | | 보류 (7절) |
| 주제별 게시물 상세 조회 수 | | | 보류 (7절) |

### 4.7 관리자 감사 로그

승인, 반려, 숨김, 삭제, 사용자 제재 같은 관리자 작업은 애플리케이션 로그가 아니라 DB의 관리자 감사 로그에 남는다. "누가 어떤 운영 작업을 했는가"를 확인하는 기록이며, `requestId`로 그 요청의 접근·에러 로그와 이어진다. 필드 정의는 비즈니스 규칙의 관리자 감사 로그 규칙을 따른다.

## 5. 사용자 지표 정의

- **DAU**: 그날 게시물을 작성(`POST /api/v1/posts` 201)하거나 좋아요를 누른(`PUT /api/v1/posts/{postId}/likes` 200) 고유 회원 수다. 팀 회의에서 정한 정의다. 접근 로그에서 세므로 좋아요를 눌렀다가 나중에 취소해도 그날 DAU에는 들어간다. 대시보드 DAU 위젯은 한국 시각(KST) 일 단위로 집계한다.
- **WAU**: 그 주(월요일 시작, KST)에 게시·좋아요를 한 고유 회원 수다. **#504에서 집계할 계획이다.**
- **W+4 리텐션**: W주 활성 회원 중 W+4주에도 활성인 비율이다. W+1~W+4를 모두 계산하고 주 단위로 집계한다. **#504에서 집계할 계획이다.**
- **주제 조회 사용자 수**: `GET /api/v1/topics` 200을 요청한 고유 로그인 회원 수다. 비로그인 조회는 `userId`가 없어 요청 수로만 잡힌다. **#504 계획.**
- **주제별 피드 조회 수**: 피드 조회(`GET /api/v1/posts`) 접근 로그에 `topicDate`를 더해 날짜(= 주제)별로 센다. 상세 조회는 세지 않는다. **#504 계획.**
- **참여 가능 사용자 수**: 정지·탈퇴가 아니고 사인 등록을 마친 회원 수다. 로그에 없으므로 DB에서 날짜별 스냅샷으로 센다. **#504 계획.**
- **총 좋아요 수**: DB(`post_likes`)에서 센다.
- **내부·테스트 계정 제외**: 제외할 회원 ID 목록을 설정으로 두고 Logs Insights 쿼리와 DB SQL 모두에 적용한다. **#504 계획이며 지금의 DAU 위젯에는 적용돼 있지 않다.**
- **`userId` 취급**: MVP에서는 가명화하지 않고 접근 로그에 원본 회원 ID를 남긴다. 로그 접근은 회사 AWS 계정 권한으로 통제한다. 로그 접근 범위가 넓어지거나 로그를 외부로 내보내게 되면 다시 검토한다.
- 사용자 단위 데이터는 집계 테이블에 저장하지 않고 결과 숫자만 저장한다(#504).

## 6. 알람 원칙

전체 알람 목록, 임계값, 평가 조건, 결측 데이터 처리는 런북의 8단계 표와 알람 설명이 기준이다. 여기서는 원칙만 적는다.

- **5xx는 1건부터 알린다.** 처음 요구사항 초안은 "오류율 + 최소 요청량" 조건이었다. 이 앱은 처리되지 않은 예외(`INTERNAL_ERROR`)일 때만 5xx를 반환하고 나머지 실패는 4xx로 응답하므로, 5xx는 1건이어도 버그다. 그래서 오류율 계산 없이 5분 안에 1건 이상이면 알린다.
- **알람은 대응이 필요한 것만 둔다.** dev 3개(5xx, EC2 상태 검사, 디스크), prod 7개(5xx, EC2 상태 검사, 디스크, 메모리, RDS 여유 스토리지, 요청한 날짜 주제 행 없음, 런타임 하트비트), 공유 3개(SQS 대기 시간, Lambda 오류, 이미지 처리 포기)다.
- **하트비트 알람은 prod에만 둔다.** prod EC2는 공개 주소로 직접 받고 로드밸런서가 없어 ALB 상태 검사 같은 앱 생존 신호가 없다. 그래서 앱이 1분마다 남기는 `type=runtime` 로그를 세어 10분 동안 끊기면 알린다. 앱 중단, CloudWatch Agent 중지, 로그 전송 문제를 한 알람으로 잡는다. 기본값 0으로 다른 로그만 들어오는 경우를, 누락 데이터 `breaching`으로 로그가 전혀 없는 경우를 잡는다. 지표와 알람이 하나씩 늘어 비용이 들기 때문에 dev에는 두지 않는다. prod 로그가 없는 릴리스 전에는 ALARM이 되므로 prod 릴리스 뒤에 만든다.
- **검수 대기 적체 알람은 없다.** 새 검수 대기 게시물은 기존 관리자 Slack 알림이 이미 알려 준다. 대기 시간은 대시보드 위젯으로 본다.
- **공유 자원 알람은 prod 토픽으로 보낸다.** SQS 큐와 이미지 처리 Lambda는 dev와 prod가 함께 쓰므로 환경별로 나눌 수 없다.
- **알림 경로는 앱과 독립이다.** 알람 Slack 채널과 webhook은 관리자 알림용과 별도로 두어 앱이나 EC2가 죽어도 알람이 나간다.
- 알람 설명 문구가 Slack 메시지의 "먼저 확인할 것"이 된다. 대응 절차는 런북의 알람 대응 표를 본다.
- **#504 계획**: 집계 성공 로그가 26시간 동안 0건이면 알리는 집계 중단 알람을 추가한다.

## 7. 보류·제외 항목

| 항목 | 이유 |
| --- | --- |
| CloudFront 요청·오류율 | MVP 범위에서 뺐다. 커스텀 메트릭·알람을 늘리지 않고 월 약 $10 예산 안에서 필요한 것부터 둔다. |
| RDS 읽기·쓰기 지연 | MVP 범위에서 뺐다. RDS는 CPU·연결 수·여유 스토리지만 보고, API 지연은 접근 로그의 p95로 본다. |
| SQS DLQ | DLQ가 없다. Lambda가 재시도를 스스로 제한하고, 포기한 메시지는 `image_processing_abandoned`로 남으므로 이 로그의 알람이 DLQ 알람을 대신한다. |
| 미처리 이미지 게시물 수 | 처리 지연은 SQS 나이 알람, 포기는 이미지 처리 포기 알람이 잡는다. 별도 집계 코드를 두지 않는다. |
| 주제 활성화 실패 | 잡지 않는다. `chalkak-prod-no-open-topic` 알람은 요청한 날짜의 주제 행이 없어 404가 날 때(주로 오늘 주제 등록 누락)만 잡고, 주제가 있어도 참여 단계(BEFORE_OPEN 등)가 열리지 않은 문제는 감지하지 못한다. |
| 게시물 제출 성공·실패 수 | 별도로 세지 않는다. 성공은 접근 로그의 `POST /api/v1/posts` 201로 볼 수 있고, 서버 오류는 5xx 알람이 잡는다. |
| 주제 알림 발송 사용자 수 | 주제 푸시 기능(#493)이 생긴 뒤에 다룬다. |
| 주제별 게시물 상세 조회 수 | 접근 로그에는 경로 템플릿만 남아 상세 조회는 주제를 알 수 없다. 피드 조회 수만 센다. |
| JVM·Hikari 메트릭화 | 알람 대상이 아니라 원인 분석용이다. 커스텀 메트릭 비용을 피하려고 `type=runtime` 로그를 Logs Insights 위젯으로 본다. |
| 가명화 | 로그 접근이 회사 AWS 계정 권한으로 통제되므로 MVP에서는 하지 않는다. 처음에는 HMAC 가명 ID와 이벤트 로그 3종을 계획했으나 접근 로그의 `userId` 한 필드로 줄였다. 로그 접근 범위가 넓어지거나 외부로 내보내게 되면 다시 검토한다. |

## 8. 로그에 남기지 않는 값

- `Authorization`/JWT, Refresh Token, OAuth Token, 비밀번호
- 이메일, Social Provider 식별자 원문
- Presigned URL 전체
- Cookie
- 이미지 binary, 민감 EXIF 정보
- 원본 URL과 쿼리 문자열 (접근 로그는 `route` 템플릿만 남긴다)
- 예외 메시지 (사용자 입력이 섞일 수 있어 예외 종류만 남긴다)
- Slack webhook URL (Lambda 환경 변수에만 입력하고 저장소·이슈·PR·채팅에 남기지 않는다)

## 9. 관련 문서

- [CloudWatch 모니터링 구축 런북](../../deploy/docs/monitoring.md): 구축 순서, 메트릭 필터·알람 표, 알람 대응, 비용 점검
- [알람 전달 Lambda](../../lambda/alarm-notifier/README.md): SNS 알람을 Slack으로 전달하는 코드와 배포 절차
- [대시보드 정의](../../deploy/monitoring/dashboard.json): 대시보드 `DASHBOARD-chalkak`의 소스
- CloudWatch Agent 설정: [dev](../../deploy/monitoring/cloudwatch-agent.dev.json), [prod](../../deploy/monitoring/cloudwatch-agent.prod.json)
