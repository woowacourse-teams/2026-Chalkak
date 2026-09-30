# CloudWatch 모니터링 구축

이 문서는 개발·운영 EC2의 로그와 지표를 CloudWatch로 모으고, 알람을 Slack으로 전달하는 최초 구축 절차와 운영 점검 방법을 설명한다. 전체 배포 구조는 [배포 가이드](../README.md), 서버 준비는 [서버 구축](infrastructure-setup.md)을 먼저 확인한다.

## 개요

애플리케이션은 `/opt/chalkak/logs/application.log`에 JSON 한 줄씩 로그만 남긴다. 커스텀 메트릭은 모두 CloudWatch 메트릭 필터가 로그에서 만들고, 메모리·디스크 지표만 CloudWatch Agent가 수집한다.

```text
application.log
  → CloudWatch Agent (EC2)
  → 로그 그룹 /chalkak/{env}/application
  → 메트릭 필터
  → 메트릭 (Chalkak/{env})
  → 알람
  → SNS 토픽 chalkak-{env}-alarms
  → Lambda chalkak-alarm-notifier
  → Slack Incoming Webhook (환경별 채널)
```

이미지 처리 Lambda의 로그 그룹 `/aws/lambda/chalkak-image-processor`에도 메트릭 필터를 붙이고, 이 필터와 SQS·Lambda 기본 지표의 알람(`chalkak-shared-*`)은 prod 토픽으로 보낸다.

| 저장소 파일 | 사용처 |
| --- | --- |
| `backend/deploy/monitoring/cloudwatch-agent.dev.json` | dev EC2에 붙여 넣는 CloudWatch Agent 설정 (5단계) |
| `backend/deploy/monitoring/cloudwatch-agent.prod.json` | prod EC2에 붙여 넣는 CloudWatch Agent 설정 (5단계) |
| `backend/deploy/monitoring/dashboard.json` | 대시보드 `DASHBOARD-chalkak`의 소스 (9단계) |
| `backend/lambda/alarm-notifier/` | SNS 알람을 Slack으로 전달하는 Lambda 코드와 배포 절차 (3단계) |

공유 계정에는 CloudFormation과 AWS Chatbot이 허용되지 않고, IAM role도 새로 만들지 않는다. 그래서 스택이나 템플릿은 없고, 모든 리소스를 CloudWatch·SNS·Lambda 콘솔(리전 `ap-northeast-2`)에서 직접 만든다. 리소스 정의는 이 문서의 표와 `dashboard.json`이다. 공유 계정에는 CLI나 access key도 사용하지 않는다.

## 사전 확인

다음을 모두 끝낸 뒤에 구축을 시작한다.

- [ ] `#8기-기술-검토`가 `ec2-project` role에 `CloudWatchAgentServerPolicy`를 연결했거나 명시적인 서면 승인을 줬다. `ec2-project`는 회사 제공 role이므로 직접 수정하지 않는다.
- [ ] Lambda 생성 화면의 **기존 역할 사용**에서 회사 제공 Lambda 실행 역할을 고를 수 있고, 그 역할에 CloudWatch Logs 쓰기 권한(`logs:CreateLogGroup`, `logs:CreateLogStream`, `logs:PutLogEvents`)이 있으며, 이 용도로 써도 된다는 확인을 받았다. 기존 이미지 처리 Lambda를 만들 때 쓴 역할이 기준이다([이미지 처리 Lambda](../../lambda/image-processor/README.md)).
- [ ] Lambda 런타임에서 Python 3.14를 고를 수 있다.
- [ ] Lambda를 VPC 밖에 만들 수 있고, 인터넷의 `hooks.slack.com`으로 나갈 수 있다. VPC에 붙이면 NAT 없이는 Slack에 닿지 않는다.
- [ ] 로그 그룹 `/aws/lambda/chalkak-image-processor`가 존재한다(CloudWatch > 로그 > 로그 그룹). 없으면 메트릭 필터를 만들 수 없으므로 이미지 처리 Lambda를 먼저 배포한다.
- [ ] 다음 값을 모았다.

| 값 | 확인 위치 |
| --- | --- |
| dev, prod EC2 인스턴스 ID (`i-`로 시작) | EC2 콘솔, `Name=chalkak-dev-api`, `Name=chalkak-prod-api` |
| prod RDS DB 식별자 | RDS 콘솔 > 데이터베이스 > DB 식별자 |

정책 연결과 Lambda 역할 사용 여부는 `#8기-기술-검토`에 다음처럼 문의한다.

```text
[문의] 찰칵 CloudWatch 모니터링 구축 (#503)

1. ec2-project role에 AWS 관리형 정책 CloudWatchAgentServerPolicy를 연결해 주실 수 있나요?
   - 용도: dev·prod EC2의 CloudWatch Agent가 로그 그룹(/chalkak/{dev,prod}/application)과 지표를 전송
   - 다른 팀도 같은 role을 쓰면 권한이 함께 늘어납니다.
2. 새 Lambda(chalkak-alarm-notifier)에 회사 제공 Lambda 실행 역할을 사용해도 되나요?
   - 용도: CloudWatch 알람 SNS 메시지를 Slack webhook으로 전달 (VPC 밖, hooks.slack.com 호출)
   - IAM role은 새로 만들지 않습니다.
3. 콘솔에서 만든 CloudWatch 알람·로그 그룹·SNS 토픽에 삭제가 제한되는 것으로 아는데, 테스트용 알람 삭제가 막히면 요청드리겠습니다.
```

## Slack 준비

1. 알람 채널을 dev용, prod용으로 따로 만든다(예: `#chalkak-alarm-dev`, `#chalkak-alarm-prod`).
2. 채널마다 Incoming Webhook을 하나씩 만든다. 기존 관리자 알림용 webhook(`ADMIN_SLACK_WEBHOOK_URL`)은 재사용하지 않는다. 앱이나 EC2가 죽어도 알람이 나가야 하므로 앱과 독립된 경로로 둔다.
3. webhook URL(`https://hooks.slack.com/...`)은 비밀이다. 저장소, 이슈, PR, 채팅에 남기지 않고 Lambda 환경 변수에만 입력한다.

## 구축 순서

순서에는 이유가 있으므로 바꾸지 않는다. 콘솔에서 만드는 모든 리소스에는 태그 입력란이 있으면 다음 세 개를 붙인다.

```text
Service=techcourse
Role=techcourse-etc
ProjectTeam=chalkak
```

### 언제 하는가

알람은 지표가 존재해야 콘솔에서 만들 수 있으므로(7단계), 각 알람은 자기 지표가 생긴 뒤에 만든다.

| 구분 | 시점 | 이유 |
| --- | --- | --- |
| SNS 토픽 2개, Lambda(webhook 2개 모두), Lambda 테스트 | 이 PR 병합 직후 (dev 단계) | dev와 prod 전달 경로를 한 번에 확인할 수 있다 |
| dev 로그 그룹, dev 메트릭 필터, dev Agent, dev 알람 | 이 PR 병합 직후 (dev 단계) | dev는 #500~#502가 이미 실행 중이라 로그가 바로 들어온다 |
| 공유 메트릭 필터, 공유 알람, 대시보드 | 이 PR 병합 직후 (dev 단계) | 이미지 파이프라인은 dev·prod가 같이 쓴다. 공유 알람은 dev에서 이미지를 한 장 처리해 지표가 생긴 뒤에 만든다 |
| prod 로그 그룹 | 언제든 | 보존 기간만 정해 두면 되므로 릴리스 전에 만들어도 된다 |
| prod 메트릭 필터, prod Agent, prod 알람 | 릴리스 당일, 릴리스 뒤 Agent가 실행 중일 때 | prod 로그는 백엔드 릴리스 뒤에야 들어온다. 그 전에는 지표가 없어 알람을 만들 수 없다 |
| #504 항목 | 이후 | 아래 [다음 단계](#다음-단계-504) 참고 |

prod 백엔드는 [배포 운영 런북](operations.md)의 일반 릴리스로 반영된다.

### 1. 로그 그룹

CloudWatch > 로그 > 로그 그룹 > 로그 그룹 생성으로 두 개를 만든다.

| 이름 | 보존 기간 |
| --- | --- |
| `/chalkak/dev/application` | 7일 |
| `/chalkak/prod/application` | 60일 |

Agent보다 먼저 만드는 이유는 Agent가 로그 그룹을 먼저 만들면 보존 기간이 무기한이 되기 때문이다. 생성 화면에서 태그도 붙인다.

### 2. SNS 토픽

SNS 콘솔 > 주제 > 주제 생성에서 유형은 **표준**으로 두 개를 만든다.

- `chalkak-dev-alarms`
- `chalkak-prod-alarms`

Lambda 트리거와 알람 알림이 이 토픽을 참조하므로 3단계와 8단계보다 먼저 만든다.

### 3. 알람 전달 Lambda

`backend/lambda/alarm-notifier/README.md`의 콘솔 배포 절차를 그대로 따른다.

1. Lambda 콘솔 > 함수 생성 > 새로 작성. 이름 `chalkak-alarm-notifier`, 런타임 Python 3.14, 실행 역할은 **기존 역할 사용**에서 회사 제공 역할, 아키텍처와 VPC는 기본값(VPC 없음)이다.
2. 코드 탭의 `lambda_function.py` 내용을 `backend/lambda/alarm-notifier/handler.py`로 바꿔 붙여 넣고 **Deploy**한다. 파일 이름은 바꾸지 않는다.
3. 구성 > 일반 구성에서 제한 시간을 10초로 바꾼다. 핸들러는 기본값 `lambda_function.lambda_handler`를 그대로 둔다.
4. 구성 > 환경 변수에 `DEV_SLACK_WEBHOOK_URL`, `PROD_SLACK_WEBHOOK_URL`을 입력한다. 값은 Slack 준비에서 만든 채널별 webhook URL이다.
5. 구성 > 트리거 추가 > SNS로 `chalkak-dev-alarms`와 `chalkak-prod-alarms`를 각각 추가한다.
6. 구성 > 태그에 위 세 태그를 붙인다.

두 가지로 동작을 확인한다. Slack에 dev, prod 채널 모두 메시지가 도착해야 한다.

1. Lambda **테스트** 탭에서 README의 테스트 이벤트를 실행한다. `TopicArn`의 마지막 조각이 `chalkak-dev-alarms`이면 dev 채널, `chalkak-prod-alarms`이면 prod 채널로 간다. 두 번 실행한다.
2. SNS 콘솔에서 각 토픽을 열고 **메시지 게시**로 제목과 일반 텍스트 본문을 보낸다. SNS 구독과 Lambda 호출까지 이어지는지 확인하는 단계이며 채널에 일반 텍스트 형식으로 도착한다.

이 단계가 4~8단계보다 앞서는 이유는 알람을 만들기 전에 SNS에서 Slack까지의 전달 경로를 먼저 확인하기 위해서다. 실제 알람 JSON 서식은 10단계에서 확인한다. Lambda 콘솔 테스트와 SNS 게시는 실제 채널로 메시지를 보내므로 채널 참여자에게 미리 알린다. 환경 변수는 콘솔 읽기 권한이 있는 사람에게 값이 그대로 보이므로 webhook URL이 노출된다는 점을 염두에 둔다.

### 4. 메트릭 필터

콘솔에서 CloudWatch > 로그 > 로그 그룹 > 대상 로그 그룹을 선택하고 **작업 > 지표 필터 생성**으로 만든다(로그 그룹의 **지표 필터** 탭에서 만들어도 된다). 패턴을 입력하고 다음으로 넘어가 필터 이름을 입력한 뒤 "지표 세부 정보" 단계에서 네임스페이스, 지표 이름, 지표 값, 단위와 "기본값(선택 사항)"을 입력한다. 콘솔 표시 이름은 조금 다를 수 있다. 태그는 지표 필터에 붙일 수 없다.

| 로그 그룹 | 필터 이름 | 패턴 | 네임스페이스 | 지표 이름 | 값 | 기본값 | 단위 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `/chalkak/dev/application` | `RequestCount` | `{ $.type = "access" }` | `Chalkak/dev` | `RequestCount` | `1` | `0` | Count |
| `/chalkak/dev/application` | `Server5xx` | `{ $.type = "access" && $.status >= 500 }` | `Chalkak/dev` | `Server5xx` | `1` | `0` | Count |
| `/chalkak/prod/application` | `RequestCount` | `{ $.type = "access" }` | `Chalkak/prod` | `RequestCount` | `1` | `0` | Count |
| `/chalkak/prod/application` | `Server5xx` | `{ $.type = "access" && $.status >= 500 }` | `Chalkak/prod` | `Server5xx` | `1` | `0` | Count |
| `/chalkak/prod/application` | `LatencyMs` | `{ $.type = "access" }` | `Chalkak/prod` | `LatencyMs` | `$.durationMs` | 없음 | Milliseconds |
| `/chalkak/prod/application` | `TopicNotFound` | `{ $.type = "access" && $.method = "GET" && $.route = "/api/v1/topics" && $.status = 404 }` | `Chalkak/prod` | `TopicNotFound` | `1` | `0` | Count |
| `/aws/lambda/chalkak-image-processor` | `ImageProcessingAbandoned` | `"image_processing_abandoned"` | `Chalkak/shared` | `ImageProcessingAbandoned` | `1` | `0` | Count |

- `LatencyMs`에는 기본값을 넣지 않는다. 기본값 0을 두면 요청이 없는 구간에도 0ms 샘플이 가짜로 발행되어 p95·p99가 왜곡된다.
- 접근 로그 한 줄 예시는 `{"type":"access","method":"GET","route":"/api/v1/topics","status":200,"durationMs":42,...}`다.
- `TopicNotFound`는 `GET /api/v1/topics`가 오늘 날짜의 주제가 없을 때 404를 반환한다는 점을 이용한다. 이 경로의 404가 반복되면 요청한 날짜(앱은 오늘, KST)의 주제 행이 없다는 뜻이다. 주제의 참여 단계(BEFORE_OPEN, OPEN, CLOSED)는 확인하지 않으므로 주제가 있지만 아직 열리지 않은 경우는 잡지 못한다. 알람은 5분 합계가 3건 이상인 구간이 10분 연속일 때만 울려 일시적인 404를 거른다.
- `ImageProcessingAbandoned`의 패턴은 따옴표를 포함한 텍스트 검색어다. Lambda가 Python logging으로 `[ERROR]<TAB><시각><TAB><요청 ID><TAB>{json}` 형태의 줄을 남기므로 순수 JSON이 아니어서 JSON 패턴으로 매칭할 수 없다. 따옴표로 묶은 검색어는 정확히 그 문구만 매칭하므로 `image_processing_abandon_failed`는 매칭되지 않는다.
- 필터는 만든 뒤에 들어오는 로그만 지표로 만든다.

### 5. CloudWatch Agent 설치 (dev, prod EC2 각각)

사전 확인의 정책 연결이 끝나 있어야 하고, 1단계 로그 그룹이 만들어져 있어야 한다. prod는 릴리스 당일에 진행한다.

1. [공식 문서](https://docs.aws.amazon.com/AmazonCloudWatch/latest/monitoring/download-CloudWatch-Agent-on-EC2-Instance-commandline-first.html)에서 Ubuntu용 deb 다운로드 URL을 확인해 설치한다. 버전이 바뀌므로 URL을 이 문서에 고정하지 않는다.

   ```bash
   wget <공식 문서의 Ubuntu deb URL> -O amazon-cloudwatch-agent.deb
   sudo dpkg -i -E ./amazon-cloudwatch-agent.deb
   ```

   다운로드 전에 `uname -m`으로 아키텍처를 확인한다. `x86_64`는 amd64, `aarch64`는 arm64이며 deb 경로가 다르므로 위 공식 문서에서 맞는 URL을 고른다.

2. 저장소의 설정 파일 내용을 서버에 붙여 넣는다. dev 서버는 `cloudwatch-agent.dev.json`, prod 서버는 `cloudwatch-agent.prod.json`을 저장소에서 열어 복사한다. 서버에는 SSM 콘솔 또는 SSH로 접속하고 다음을 실행한다.

   ```bash
   sudo mkdir -p /opt/aws/amazon-cloudwatch-agent/etc
   sudoedit /opt/aws/amazon-cloudwatch-agent/etc/chalkak.json
   python3 -m json.tool /opt/aws/amazon-cloudwatch-agent/etc/chalkak.json
   ```

   `json.tool`이 오류 없이 JSON을 출력하면 붙여 넣기가 정상이다.

3. 에이전트는 `cwagent` 사용자로 실행된다. 로그 디렉터리 `/opt/chalkak/logs`는 `chalkak:chalkak`, mode `0750`이고 앱은 `UMask=0027`로 파일을 만들므로 `chalkak` 그룹 권한이 있어야 읽을 수 있다.

   ```bash
   sudo usermod -aG chalkak cwagent
   ```

4. 설정을 적용하고 에이전트를 시작한다. 그룹 변경을 반영하려고 한 번 재시작한다.

   ```bash
   sudo /opt/aws/amazon-cloudwatch-agent/bin/amazon-cloudwatch-agent-ctl \
     -a fetch-config -m ec2 -s \
     -c file:/opt/aws/amazon-cloudwatch-agent/etc/chalkak.json
   sudo systemctl restart amazon-cloudwatch-agent
   ```

5. 상태와 권한을 확인한다.

   ```bash
   sudo /opt/aws/amazon-cloudwatch-agent/bin/amazon-cloudwatch-agent-ctl -m ec2 -a status
   sudo -u cwagent head -n 1 /opt/chalkak/logs/application.log
   sudo tail -n 100 /opt/aws/amazon-cloudwatch-agent/logs/amazon-cloudwatch-agent.log
   ```

   `status`가 `running`이고, `head`가 `Permission denied` 없이 로그 한 줄을 출력해야 한다. 에이전트 로그의 `AccessDenied`는 정책 연결, `permission denied`는 3번 그룹 설정 문제다.

### 6. 디스크 지표 디멘션 확인

에이전트가 지표를 보낸 뒤(약 5분) 콘솔 `CloudWatch > 지표 > 모든 지표 > CWAgent > disk_used_percent`에서 디멘션이 `InstanceId`, `path`, `fstype`인지, `fstype` 값이 `ext4`인지 확인한다. 값이 다르면 8단계 디스크 알람의 `fstype` 디멘션과 9단계 `dashboard.json`의 `"ext4"`를 실제 값으로 바꾼다. 값이 다르면 알람과 위젯이 데이터를 찾지 못한다. 이 확인이 알람 생성 앞에 있는 이유는 알람의 디멘션이 실제 지표와 정확히 같아야 하기 때문이다.

### 7. 지표 존재 확인

콘솔에서는 데이터 포인트가 한 번도 없는 지표로 알람을 만들 수 없다("지표는 데이터 포인트가 생기기 전에는 보이지 않는다", 메트릭 필터 문서). 존재하지 않는 지표의 알람은 API나 CLI로만 만들 수 있다. 그래서 알람은 지표가 생긴 뒤에 만든다. `CloudWatch > 지표 > 모든 지표`에서 지표를 확인한다. 검색창에 지표 이름(예: `Server5xx`)을 입력하면 네임스페이스와 관계없이 찾을 수 있다.

| 지표 | 생기는 때 | 조치 |
| --- | --- | --- |
| `Chalkak/{env}` `RequestCount`, `Server5xx`, `TopicNotFound` | 기본값 0이 있어 로그가 한 줄이라도 들어오면 발행된다 | Agent가 로그를 보낸 뒤 기다린다 |
| `Chalkak/prod` `LatencyMs` | 기본값이 없어 첫 접근 로그가 들어와야 생긴다 | 접근 로그가 들어온 뒤 기다린다 |
| `CWAgent` `disk_used_percent`, `mem_used_percent` | Agent가 실행되면 약 5분 뒤 | 5단계와 6단계 이후 확인한다 |
| `Chalkak/shared` `ImageProcessingAbandoned` | 기본값 0이 있지만 이미지 Lambda가 로그를 한 줄이라도 남겨야 발행된다 | dev 앱에서 사인이나 포스트 이미지를 한 장 업로드해 처리시킨 뒤 확인한다 |
| `AWS/SQS` `ApproximateAgeOfOldestMessage` | 유휴 큐에는 없을 수 있다 | 이미지를 한 장 처리한 뒤 확인한다 |
| `AWS/EC2`, `AWS/RDS`, `AWS/Lambda` 지표 | 이미 존재한다 | 없음 |

prod 알람은 prod 릴리스가 끝나고 prod Agent가 실행 중이어서 위 지표가 모두 보일 때 만든다.

### 8. 알람

알람은 dev 3개, prod 6개, 공유 3개다. 콘솔에서는 CloudWatch > 경보 > 모든 경보 > **경보 생성** 흐름을 모든 알람에 똑같이 쓴다. 콘솔 표시 이름은 조금 다를 수 있다.

1. **지표 선택**에서 표의 네임스페이스, 지표 이름, 디멘션을 고르고 통계와 기간을 입력한다.
2. 조건에서 정적 임계값과 비교 연산자, 임계값을 입력한다. **추가 구성**을 펼쳐 "경보를 발생시킬 데이터 포인트"(표의 N of M)를 입력하고 **누락된 데이터 처리**를 표의 값으로 바꾼다. 콘솔의 기본값은 `missing`에 해당하는 "누락으로 처리"다.
3. 알림에서 **경보 상태**(ALARM)에 해당 SNS 토픽을 선택하고, **알림 추가**를 눌러 **정상(OK)** 상태 알림도 같은 토픽으로 추가한다. 이렇게 해야 복구 때도 OK 메시지가 온다.
4. 이름과 설명을 입력한다. 설명은 Slack 메시지의 "먼저 확인할 것"으로 표시되므로 아래 문구를 그대로 쓴다.
5. 태그를 붙이고 생성한다.

표의 "평가 (N of M)"는 경보를 발생시킬 데이터 포인트 수 N / 평가 기간 M이다. 표의 "누락 데이터" 값은 API 문자열이며 콘솔 선택지는 다음과 같다. 정확한 한국어 표시 이름은 조금 다를 수 있다.

| 표의 값 | 콘솔 선택지 |
| --- | --- |
| `notBreaching` | 누락 데이터를 양호(임계값 위반 아님)로 처리 |
| `breaching` | 누락 데이터를 불량(임계값 위반)으로 처리 |
| `missing` | 누락 데이터를 누락으로 처리 |
| `ignore` | 현재 경보 상태 유지 (이 문서에서는 쓰지 않는다) |

#### dev 알람 (토픽 `chalkak-dev-alarms`)

| 이름 | 지표 (네임스페이스 / 이름 / 디멘션) | 통계 | 기간 | 조건 | 평가 (N of M) | 누락 데이터 |
| --- | --- | --- | --- | --- | --- | --- |
| `chalkak-dev-5xx` | `Chalkak/dev` / `Server5xx` / 없음 | Sum | 300초 | `>= 1` | 1 of 1 | `notBreaching` |
| `chalkak-dev-ec2-status-check` | `AWS/EC2` / `StatusCheckFailed` / `InstanceId=`dev 인스턴스 ID | Maximum | 60초 | `>= 1` | 2 of 2 | `breaching` |
| `chalkak-dev-disk-used` | `CWAgent` / `disk_used_percent` / `InstanceId=`dev 인스턴스 ID, `path=/`, `fstype=ext4` | Average | 300초 | `> 85` | 2 of 2 | `missing` |

#### prod 알람 (토픽 `chalkak-prod-alarms`)

| 이름 | 지표 (네임스페이스 / 이름 / 디멘션) | 통계 | 기간 | 조건 | 평가 (N of M) | 누락 데이터 |
| --- | --- | --- | --- | --- | --- | --- |
| `chalkak-prod-5xx` | `Chalkak/prod` / `Server5xx` / 없음 | Sum | 300초 | `>= 1` | 1 of 1 | `notBreaching` |
| `chalkak-prod-ec2-status-check` | `AWS/EC2` / `StatusCheckFailed` / `InstanceId=`prod 인스턴스 ID | Maximum | 60초 | `>= 1` | 2 of 2 | `breaching` |
| `chalkak-prod-disk-used` | `CWAgent` / `disk_used_percent` / `InstanceId=`prod 인스턴스 ID, `path=/`, `fstype=ext4` | Average | 300초 | `> 85` | 2 of 2 | `missing` |
| `chalkak-prod-memory-used` | `CWAgent` / `mem_used_percent` / `InstanceId=`prod 인스턴스 ID | Average | 300초 | `> 85` | 2 of 2 | `missing` |
| `chalkak-prod-rds-free-storage` | `AWS/RDS` / `FreeStorageSpace` / `DBInstanceIdentifier=`prod RDS 식별자 | Minimum | 300초 | `< 2147483648` (2 GiB) | 1 of 1 | `missing` |
| `chalkak-prod-no-open-topic` | `Chalkak/prod` / `TopicNotFound` / 없음 | Sum | 300초 | `>= 3` | 2 of 2 | `notBreaching` |

#### 공유 알람 (토픽 `chalkak-prod-alarms`)

SQS 큐와 Lambda는 dev와 prod가 함께 쓰므로 환경별로 나눌 수 없다. 알람은 모두 prod 토픽으로 보내고 prod 채널로 전달된다.

| 이름 | 지표 (네임스페이스 / 이름 / 디멘션) | 통계 | 기간 | 조건 | 평가 (N of M) | 누락 데이터 |
| --- | --- | --- | --- | --- | --- | --- |
| `chalkak-shared-image-queue-age` | `AWS/SQS` / `ApproximateAgeOfOldestMessage` / `QueueName=chalkak-image-processing` | Maximum | 300초 | `> 600` | 1 of 1 | `notBreaching` |
| `chalkak-shared-image-lambda-errors` | `AWS/Lambda` / `Errors` / `FunctionName=chalkak-image-processor` | Sum | 300초 | `>= 5` | 1 of 1 | `notBreaching` |
| `chalkak-shared-image-processing-abandoned` | `Chalkak/shared` / `ImageProcessingAbandoned` / 없음 | Sum | 300초 | `>= 1` | 1 of 1 | `notBreaching` |

#### 알람 설명

`{env}` 표기가 있는 알람은 dev와 prod가 같은 문구를 쓴다. 디스크 알람만 prod에 Docker가 없어 환경별로 다르다.

- `chalkak-{env}-5xx`: `5분 안에 5xx 응답이 1건 이상 발생했습니다. 먼저 애플리케이션 로그 그룹에서 type=error, errorCode=INTERNAL_ERROR 로그의 requestId와 stack_trace를 확인하세요.`
- `chalkak-{env}-ec2-status-check`: `EC2 상태 검사가 2분 연속 실패했습니다. 먼저 EC2 콘솔에서 인스턴스 상태 검사(시스템/인스턴스)와 재부팅·중지 여부를 확인하세요.`
- `chalkak-dev-disk-used`: `루트 디스크 사용률이 10분 연속 85%를 넘었습니다. 먼저 서버에서 df -h 로 용량을 확인하고 /opt/chalkak/logs 크기와 journalctl --disk-usage 를 점검한 뒤, docker system df 로 Docker 이미지·볼륨·로그 크기도 확인하세요.`
- `chalkak-prod-disk-used`: `루트 디스크 사용률이 10분 연속 85%를 넘었습니다. 먼저 서버에서 df -h 로 용량을 확인하고 /opt/chalkak/logs 크기와 journalctl --disk-usage 를 점검하세요.`
- `chalkak-prod-memory-used`: `메모리 사용률이 10분 연속 85%를 넘었습니다. 먼저 서버에서 free -m, ps aux --sort=-%mem | head, systemctl status chalkak-backend 로 메모리를 많이 쓰는 프로세스와 서비스 상태를 확인하고, journalctl -u chalkak-backend 로 OOM 또는 재시작 여부를 점검하세요.`
- `chalkak-prod-rds-free-storage`: `RDS 여유 스토리지가 2GiB 미만입니다. 먼저 RDS 콘솔에서 스토리지 사용량과 자동 확장 설정을 확인하고 빠르게 늘어난 테이블이 있는지 점검하세요.`
- `chalkak-prod-no-open-topic`: `요청한 날짜의 주제가 없다는 404(GET /api/v1/topics)가 10분 연속 5분마다 3건 이상 발생했습니다. 오늘(KST) 주제가 등록되지 않았거나 삭제됐을 가능성이 큽니다. 먼저 관리자 페이지에서 오늘(KST) 날짜의 주제가 있는지 확인하세요. 주제가 있어도 BEFORE_OPEN 상태인 경우는 이 알람이 감지하지 못합니다.`
- `chalkak-shared-image-queue-age`: `이미지 처리 대기열에서 가장 오래된 메시지가 10분 넘게 처리되지 않았습니다. 먼저 이미지 처리 Lambda의 Errors·Throttles 지표와 로그 그룹을 확인하세요.`
- `chalkak-shared-image-lambda-errors`: `이미지 처리 Lambda 오류가 5분 안에 5건 이상 발생했습니다. 먼저 Lambda 로그 그룹에서 [ERROR] 로그와 실패한 S3 key를 확인하세요.`
- `chalkak-shared-image-processing-abandoned`: `재시도 한도를 넘어 이미지 처리를 포기한 메시지가 있습니다. 먼저 Lambda 로그 그룹에서 image_processing_abandoned 를 검색해 대상 S3 key를 확인하세요.`

5xx는 처리되지 않은 예외(`INTERNAL_ERROR`)일 때만 나오므로 1건이어도 버그다.

### 9. 대시보드

1. CloudWatch > 대시보드 > 대시보드 생성에서 이름을 `DASHBOARD-chalkak`으로 만든다. 위젯 추가 창이 나오면 닫는다.
2. 대시보드 **작업 > 소스 보기/편집**을 연다.
3. `backend/deploy/monitoring/dashboard.json`의 내용을 붙여 넣기 전에 플레이스홀더를 바꾼다.

   | 플레이스홀더 | 개수 | 넣을 값 |
   | --- | --- | --- |
   | `PROD_INSTANCE_ID` | 3 | prod EC2 인스턴스 ID |
   | `DEV_INSTANCE_ID` | 2 | dev EC2 인스턴스 ID |
   | `PROD_RDS_INSTANCE_ID` | 3 | prod RDS DB 식별자 |
   | `"ext4"` | 2 | 6단계에서 확인한 `fstype`. 다르지 않으면 그대로 둔다 |

4. 저장하고 위젯에 데이터가 표시되는지 본다. 알람 상태 위젯은 8단계의 알람 이름을 참조하므로 알람이 모두 만들어진 뒤에 확인한다. 저장소의 `dashboard.json`에는 실제 ID를 커밋하지 않는다. 대시보드에는 태그를 붙일 수 없다.

### 10. 알람 전달 종단 확인

콘솔에서는 알람 상태를 임의로 바꿀 수 없다(CLI가 필요하다). 대신 항상 ALARM이 되는 임시 알람을 만든다.

1. 8단계 흐름으로 알람을 만든다. 이름 `chalkak-dev-test-alarm`, 지표 `Chalkak/dev` / `RequestCount`, 통계 Sum, 기간 1분, 조건 `>= 0`, 누락 데이터 `breaching`, 알림은 `chalkak-dev-alarms`다. 설명에는 `Slack 전달 확인용 테스트 알람입니다. 확인 후 삭제하세요.`를 쓴다.
2. 몇 분 안에 dev 채널에 ALARM 메시지가 오는지 확인한다.
3. 확인 후 **삭제**한다. 공유 계정은 삭제를 제한하므로 막히면 경보를 선택해 **작업 > 경보 작업 > 비활성화**로 알림을 끄고 `#8기-기술-검토`에 삭제를 요청한다. 삭제 대신 임계값을 바꿔(예: `>= 0`을 `> 100000000`으로) 알람이 OK로 돌아오게 해도 된다. 이때도 OK 메시지가 온다. 콘솔 표시 이름은 조금 다를 수 있다.
4. prod 경로가 의심될 때만 같은 방식으로 `chalkak-prod-test-alarm`을 `Chalkak/prod`와 `chalkak-prod-alarms`로 만든다. 3단계에서 prod 채널 전달을 이미 확인했으므로 기본은 생략한다.

테스트 알람은 삭제하면 OK 메시지가 오지 않는다.

## 검증 체크리스트

- [ ] 로그 그룹 `/chalkak/dev/application`, `/chalkak/prod/application`에 로그 이벤트가 들어오고, Logs Insights에서 JSON 필드(`type`, `route`, `status`, `userId`)가 자동 인식된다.
- [ ] `type=runtime` 로그가 1분마다 들어온다.
- [ ] 콘솔 `CloudWatch > 지표`의 네임스페이스별 메트릭 수가 맞다: `Chalkak/dev` 2, `Chalkak/prod` 4, `Chalkak/shared` 1, `CWAgent` prod 2·dev 1.
- [ ] 알람이 `OK` 또는 예상한 `INSUFFICIENT_DATA` 상태다. `chalkak-*-ec2-status-check`, `chalkak-*-5xx`가 `ALARM`이면 즉시 확인한다.
- [ ] 대시보드 `DASHBOARD-chalkak`의 위젯에 데이터가 표시된다.
- [ ] 로그 rotation 후에도 전송이 이어진다. logback은 매일과 50MB마다 `application.log.<날짜>.<n>.gz`로 롤링한다. 배포 다음 날 서버에서 `ls -l /opt/chalkak/logs`로 롤링된 파일을 확인하고, Logs Insights에서 한국 시각 자정 이후 이벤트가 들어오는지 조회한다. 롤링을 강제로 일으킬 필요는 없다.
- [ ] `image_processing_abandoned` 로그가 실제로 발생했을 때 `Chalkak/shared`의 `ImageProcessingAbandoned`가 1 이상이 된다. 평소에는 발생하지 않으므로 처음 발생할 때 확인한다.
- [ ] 에이전트 시작 후에도 로그 그룹 보존 기간이 유지된다(prod 60일, dev 7일).
- [ ] Slack 메시지의 CloudWatch 콘솔 링크를 한 번 눌러 알람 화면이 열린다(링크 형식이 공식 문서에 명시돼 있지 않다).

Logs Insights(`CloudWatch > 로그 > Logs Insights`) 확인용 쿼리 예시다. 로그 그룹을 선택하고 조회 기간을 짧게(예: 1시간) 잡는다.

```text
fields type, route, status, userId
| sort @timestamp desc
| limit 20
```

```text
filter type = "runtime"
| stats count(*) by bin(1m)
```

```text
filter type = "access"
| stats count(*) as requests, pct(durationMs, 95) as p95 by route
| sort requests desc
| limit 10
```

## 변경 관리

템플릿이 없으므로 이 문서의 표(로그 그룹, 메트릭 필터, 알람과 설명)와 `backend/deploy/monitoring/dashboard.json`이 정의다.

- 콘솔에서 알람이나 메트릭 필터를 바꾸면 같은 PR에서 이 문서의 표와 설명을 함께 수정한다.
- 콘솔에서 대시보드를 수정하면 **작업 > 소스 보기/편집**의 JSON을 복사해 `dashboard.json`에 반영한다. 이때 실제 인스턴스 ID와 RDS 식별자는 다시 플레이스홀더로 바꿔 커밋한다.
- 알람 상태 위젯은 알람을 직접 지정하므로 알람을 추가하면 위젯에도 수동으로 추가한다. Logs Insights 위젯은 대시보드 기간(기본 24시간)을 따른다.
- 콘솔 변경과 문서가 어긋나면 문서가 아니라 콘솔이 실제 상태다. 어긋난 것을 발견하면 문서를 콘솔에 맞춘다.

## 알람 대응

| 알람 | 먼저 확인할 것 |
| --- | --- |
| `chalkak-{env}-5xx` | 로그 그룹에서 `type=error`, `errorCode=INTERNAL_ERROR` 로그의 `requestId`와 `stack_trace` |
| `chalkak-{env}-ec2-status-check` | EC2 콘솔의 상태 검사, 재부팅·중지 여부 |
| `chalkak-dev-disk-used` | 서버 `df -h`, `/opt/chalkak/logs` 크기, `journalctl --disk-usage`, `docker system df`(Docker 이미지·볼륨·로그) |
| `chalkak-prod-disk-used` | 서버 `df -h`, `/opt/chalkak/logs` 크기, `journalctl --disk-usage` |
| `chalkak-prod-memory-used` | 서버 `free -m`, `ps aux --sort=-%mem \| head`, `systemctl status chalkak-backend`, `journalctl -u chalkak-backend`의 OOM·재시작 여부 |
| `chalkak-prod-rds-free-storage` | RDS 콘솔의 스토리지 사용량과 자동 확장 설정, 빠르게 커진 테이블 |
| `chalkak-prod-no-open-topic` | 관리자 페이지에 오늘(KST) 날짜의 주제가 있는지(등록 누락·삭제). BEFORE_OPEN 상태인 주제는 이 알람이 잡지 못한다 |
| `chalkak-shared-image-queue-age` | Lambda `Errors`·`Throttles` 지표와 Lambda 로그 그룹 |
| `chalkak-shared-image-lambda-errors` | Lambda 로그의 `[ERROR]`와 실패한 S3 key |
| `chalkak-shared-image-processing-abandoned` | Lambda 로그에서 `image_processing_abandoned` 검색, 대상 S3 key |

알람이 왔는데 Slack에 메시지가 없으면 Lambda `chalkak-alarm-notifier`의 로그 그룹에서 `alarm_notification_failed`를 검색한다.

## 비용 점검

CloudWatch 요금은 [공식 가격표](https://aws.amazon.com/cloudwatch/pricing/)에서 서울 리전(`ap-northeast-2`) 기준으로 확인한다. 아래는 대략적인 추정이며 실제 청구액과 다를 수 있다.

| 항목 | 수량 | 비고 |
| --- | --- | --- |
| 커스텀 메트릭 | 10개 | dev 2 + prod 4 + shared 1 + CWAgent 3(prod 2, dev 1). 메트릭 필터가 만든 메트릭도 커스텀 메트릭으로 과금된다 |
| 알람 | 12개 | dev 3 + prod 6 + shared 3. 테스트 알람을 켜 둔 동안 1개씩 추가 |
| 대시보드 | 1개 | 무료 한도는 가격표에서 확인한다 |
| 로그 수집·저장 | 트래픽 비례 | 수집량(GB)에 과금, 보존은 prod 60일·dev 7일. 접근 로그 트래픽이 늘면 가장 크게 변한다 |
| Logs Insights 조회 | 조회한 로그 양(GB) 비례 | 대시보드의 Logs Insights 위젯이 열릴 때마다 조회한다 |
| Lambda·SNS | 알람 횟수 비례 | 알람 메시지 수가 적어 이 규모에서는 무시할 수 있다. [Lambda 요금](https://aws.amazon.com/lambda/pricing/), [SNS 요금](https://aws.amazon.com/sns/pricing/) |

월 약 $10은 팀의 목표 예산이며 추정치일 뿐 가격 견적이 아니다. #504 계획(메트릭 3개, 알람 1개 추가)까지 포함해 잡은 값이다. 정확한 단가와 무료 한도는 서울 리전 가격표로 계산한다.

점검 방법은 다음과 같다.

- 콘솔 `Billing and Cost Management > Bills`에서 CloudWatch 항목을 서비스별로 본다. 공용 계정이라 권한이 없으면 `#8기-기술-검토`에 문의한다.
- `CloudWatch > 지표 > 모든 지표`에서 네임스페이스별 메트릭 수가 체크리스트의 수와 같은지 확인한다. 예상보다 많으면 에이전트 설정이나 디멘션이 의도와 다르게 늘어난 것이다.
- `CloudWatch > 로그 > 로그 그룹`에서 저장된 바이트와 보존 기간을 확인한다.

Logs Insights 비용에 주의한다. 대시보드는 기본 조회 기간을 24시간으로 두어 조회량을 줄였다. DAU 추이나 검수 소요 시간 추이를 보려고 기간을 8주로 늘리면 조회할 로그가 크게 늘고, 위젯마다 그만큼 조회하므로 비용이 함께 늘어난다. 확인이 끝나면 기간을 24시간으로 되돌린다.

## 되돌리기

공유 계정은 삭제 권한이 제한되므로 콘솔에서 막히면 `#8기-기술-검토`에 요청한다. 다음 순서로 의존하는 것부터 지운다.

주의할 점이다.

- prod 알람, prod 토픽, Lambda를 삭제하면 운영 알림이 끊긴다.
- prod Agent를 멈추거나 제거하면 로그와 지표가 끊기고 알람이 `INSUFFICIENT_DATA`가 된다.
- 공유 알람(`chalkak-shared-*`)은 prod 토픽에 의존하므로 prod 토픽보다 먼저 지운다.


1. 대시보드 `DASHBOARD-chalkak`을 삭제한다.
2. 알람을 삭제한다(경보 > 선택 > 작업 > 삭제). 공유 알람을 먼저 지운다. 알람이 SNS 토픽을 참조하므로 토픽보다 먼저 지운다. 삭제가 안 되면 알람 작업만이라도 비활성화한다.
3. 메트릭 필터를 삭제한다(로그 그룹 > 지표 필터 탭). 지표 데이터는 만료될 때까지 남는다.
4. Lambda `chalkak-alarm-notifier`의 SNS 트리거를 지우고 함수를 삭제한다. 그다음 SNS 토픽 `chalkak-dev-alarms`, `chalkak-prod-alarms`를 삭제한다.
5. 서버에서 에이전트를 멈춘다.

   ```bash
   sudo /opt/aws/amazon-cloudwatch-agent/bin/amazon-cloudwatch-agent-ctl -m ec2 -a stop
   ```

   제거하려면 `sudo apt remove amazon-cloudwatch-agent`를 실행하고, 필요하면 `sudo gpasswd -d cwagent chalkak`으로 그룹에서 뺀다. 에이전트를 멈추면 로그와 CWAgent 지표가 끊겨 `disk-used`, `memory-used` 알람은 `INSUFFICIENT_DATA`가 된다.

6. `ec2-project` role에 붙인 `CloudWatchAgentServerPolicy`의 분리는 `#8기-기술-검토`와 조율한다.
7. 로그 그룹 `/chalkak/{env}/application`은 로그가 들어 있으므로 합의한 경우에만 삭제한다. 삭제하지 않아도 보존 기간이 지나면 데이터가 만료된다.

## 다음 단계 (#504)

#504에서 다음을 이 문서에 추가할 계획이다.

- prod EC2 role의 Logs Insights 조회 권한(`logs:StartQuery`, `logs:GetQueryResults`, `logs:StopQuery`). `#8기-기술-검토`를 통해 요청한다.
- 집계용 메트릭 필터와 알람. 이 문서의 4단계와 8단계 표에 추가한다.
- WAU, W+4 리텐션 위젯. `dashboard.json`에 추가한다.
- 내부 계정 제외 목록과 `topicDate` 필드. 이슈 #504에 이미 기록돼 있다.

## 공식 문서

- [CloudWatch Agent 설치](https://docs.aws.amazon.com/AmazonCloudWatch/latest/monitoring/download-CloudWatch-Agent-on-EC2-Instance-commandline-first.html)
- [CloudWatch 요금](https://aws.amazon.com/cloudwatch/pricing/)
- [메트릭 필터 생성](https://docs.aws.amazon.com/AmazonCloudWatch/latest/logs/CreateMetricFilterProcedure.html)
- [SNS 메시지 게시](https://docs.aws.amazon.com/sns/latest/dg/sns-publishing.html)
- [CloudWatch Logs 메트릭 필터 패턴](https://docs.aws.amazon.com/AmazonCloudWatch/latest/logs/FilterAndPatternSyntax.html)
- [CloudWatch 알람 생성](https://docs.aws.amazon.com/AmazonCloudWatch/latest/monitoring/ConsoleAlarms.html)
- [Lambda와 SNS 연동](https://docs.aws.amazon.com/lambda/latest/dg/with-sns.html)
- [Lambda 요금](https://aws.amazon.com/lambda/pricing/)
- [SNS 요금](https://aws.amazon.com/sns/pricing/)
