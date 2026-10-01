# Chalkak alarm notifier Lambda

CloudWatch 알람을 Slack으로 전달하는 Lambda다. 파일 하나(`handler.py`)와 Python 표준 라이브러리만
사용하므로 빌드 파이프라인 없이 Lambda 콘솔 편집기에 붙여넣어 배포한다.
런타임은 image-processor와 같은 Python 3.14를 사용한다. `+0000` 형식의 시각을
`datetime.fromisoformat`으로 파싱하므로 Python 3.11 이상이 필요하다.

## 처리 흐름

```text
CloudWatch 알람
  → SNS 토픽 chalkak-dev-alarms / chalkak-prod-alarms
  → Lambda chalkak-alarm-notifier (두 토픽을 모두 구독)
  → Slack Incoming Webhook (환경별 URL)
```

토픽 이름(ARN의 마지막 조각)으로 환경과 webhook을 고른다. 알 수 없는 토픽은 설정 오류이므로 로그를
남기고 예외를 던진다. 알람 JSON이 아닌 메시지(SNS 콘솔의 "메시지 게시" 테스트 등)는 환경과 제목을 붙인
일반 텍스트로 전송한다.

Slack 메시지에는 상태(🚨 ALARM / ✅ OK / ⚠️ INSUFFICIENT_DATA), 환경, 알람 이름, 알람 설명(먼저
확인할 것), 사유, KST 변경 시각, CloudWatch 콘솔 링크가 담긴다.

## 왜 이 구성인가

- **AWS Chatbot과 CloudFormation을 쓰지 않는다.** 공용 AWS 계정에서 허용되지 않는다. IAM 역할도 새로
  만들 수 없어 회사가 제공한 기존 Lambda 실행 역할을 사용한다.
- **앱의 Slack 발송 코드를 재사용하지 않는다.** 앱이나 EC2가 죽었을 때도 알람이 나가야 하므로 앱과
  독립된 경로여야 한다.

## 환경 변수

| 이름 | 필수 | 설명 |
| --- | --- | --- |
| `DEV_SLACK_WEBHOOK_URL` | dev 알람을 받으려면 | dev 채널 Incoming Webhook URL |
| `PROD_SLACK_WEBHOOK_URL` | prod 알람을 받으려면 | prod 채널 Incoming Webhook URL |

값은 `https://hooks.slack.com/`으로 시작해야 하며, 아니거나 비어 있으면 해당 환경의 알람을 처리하는
시점에 예외가 발생한다. webhook URL은 비밀이므로 로그와 저장소에 남기지 않는다.

## 콘솔 배포

1. Lambda 콘솔에서 **함수 생성 → 새로 작성**을 선택한다. 이름은 `chalkak-alarm-notifier`, 런타임은
   Python 3.14로 한다.
2. 실행 역할은 **기존 역할 사용**에서 회사가 제공한 Lambda 실행 역할을 고른다.
3. 코드 탭의 기본 `lambda_function.py` 내용을 이 디렉터리의 `handler.py`로 바꿔 붙여넣고
   **Deploy**한다. 파일 이름은 바꾸지 않는다.
4. **구성 → 일반 구성**에서 핸들러는 기본값 `lambda_function.lambda_handler`를 그대로 두고, 제한
   시간을 10초로 설정한다. 함수 이름(`lambda_handler`)이 같으므로 파일 이름만 맞으면 된다.
5. **구성 → 환경 변수**에 `DEV_SLACK_WEBHOOK_URL`, `PROD_SLACK_WEBHOOK_URL`을 추가한다.
6. **구성 → 트리거 추가 → SNS**로 `chalkak-dev-alarms`와 `chalkak-prod-alarms`를 각각 추가한다.
   구독과 함수 리소스 정책은 콘솔이 만들어 준다.
7. 태그를 붙인다: `Service=techcourse`, `Role=techcourse-etc`, `ProjectTeam=chalkak`.

실행 역할에는 CloudWatch Logs 쓰기 권한(`logs:CreateLogGroup`, `logs:CreateLogStream`,
`logs:PutLogEvents`)이 있어야 로그를 볼 수 있다.

## 실패와 재시도

Slack이 2xx가 아닌 응답을 주거나 네트워크 오류가 나면 `alarm_notification_failed`를 남기고 예외를
던진다. SNS는 Lambda를 비동기로 호출하므로 실패한 호출은 Lambda 비동기 호출 정책에 따라 재시도된다
(기본 2회 재시도). SNS는 호출 하나에 레코드 하나를 전달하므로 실패한 레코드만 다시 시도된다. 코드는
레코드가 여러 개인 이벤트도 순서대로 처리하지만, 그 경우 재시도 시 앞서 성공한 레코드가 다시 전송될 수
있다.

## 테스트

로컬 단위 테스트(네트워크 없음).

```bash
cd backend/lambda/alarm-notifier
python3 -m unittest discover -s tests -v
```

### Lambda 콘솔 테스트 이벤트

**테스트** 탭에서 아래 JSON으로 새 이벤트를 만들어 실행한다. `TopicArn`의 마지막 조각을 바꾸면 dev/prod를
고를 수 있다. 실제 webhook으로 전송되므로 대상 채널에 메시지가 나타난다.

```json
{
  "Records": [
    {
      "EventSource": "aws:sns",
      "Sns": {
        "TopicArn": "arn:aws:sns:ap-northeast-2:123456789012:chalkak-dev-alarms",
        "Subject": "ALARM: \"chalkak-dev-ec2-cpu-high\" in Asia Pacific (Seoul)",
        "Message": "{\"AlarmName\":\"chalkak-dev-ec2-cpu-high\",\"AlarmDescription\":\"CPU 80% 초과. 먼저 확인: 트래픽 급증, 배치 작업 여부\",\"AWSAccountId\":\"123456789012\",\"NewStateValue\":\"ALARM\",\"NewStateReason\":\"Threshold Crossed: 1 datapoint [91.0] was greater than the threshold (80.0).\",\"StateChangeTime\":\"2026-09-30T12:00:00.000+0000\",\"Region\":\"Asia Pacific (Seoul)\",\"Trigger\":{\"MetricName\":\"CPUUtilization\",\"Namespace\":\"AWS/EC2\"}}",
        "Timestamp": "2026-09-30T12:00:01.000Z"
      }
    }
  ]
}
```

### SNS 경로 테스트

SNS 콘솔에서 토픽을 열고 **메시지 게시**로 제목과 본문(일반 텍스트)을 보낸다. SNS → Lambda → Slack
경로가 끝까지 이어지는지 확인하는 용도이며, 일반 텍스트 형식으로 채널에 도착해야 한다.

## 로그 필드

JSON 한 줄로 남기며 webhook URL은 포함하지 않는다.

| `event` | 필드 |
| --- | --- |
| `alarm_notification_sent` | `alarmName`, `environment`, `state` |
| `alarm_notification_failed` | `alarmName`, `environment`, `status`(HTTP 상태, 네트워크 오류면 `null`), `error` |
| `alarm_notification_failed` (알 수 없는 토픽) | `reason=unknown_topic`, `topicArn` |

콘솔 알람 링크(`#alarmsV2:alarm/...`) 형식은 공식 문서에 명시돼 있지 않다. 런북을 따라 배포한 사람은
Slack 메시지의 링크를 한 번 눌러 알람 화면이 열리는지 확인한다.

전체 모니터링 운영 절차는 [`backend/deploy/docs/monitoring.md`](../../deploy/docs/monitoring.md)에서
다룬다.
