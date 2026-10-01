"""
CloudWatch 알람을 Slack Incoming Webhook으로 전달하는 Lambda.

CloudWatch 알람 → SNS 토픽(chalkak-dev-alarms / chalkak-prod-alarms) → 이 함수 → Slack.
Lambda 콘솔 편집기에 그대로 붙여넣어 배포하므로 파일 하나와 표준 라이브러리만 사용한다.
"""

import json
import logging
import os
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timedelta, timezone
from typing import Any

LOGGER = logging.getLogger()
LOGGER.setLevel(logging.INFO)

SLACK_WEBHOOK_PREFIX = "https://hooks.slack.com/"
REQUEST_TIMEOUT_SECONDS = 5
PLAIN_MESSAGE_MAX_LENGTH = 2000
# Slack section 블록 텍스트 상한이 3000자다. 초과하면 400이 돌아와 알람이 유실되므로 링크 앞까지를 자른다.
SECTION_BODY_MAX_LENGTH = 2800

# 토픽 이름(ARN의 마지막 조각) → (환경, webhook 환경변수 이름)
TOPICS = {
    "chalkak-dev-alarms": ("dev", "DEV_SLACK_WEBHOOK_URL"),
    "chalkak-prod-alarms": ("prod", "PROD_SLACK_WEBHOOK_URL"),
}

STATE_PREFIXES = {
    "ALARM": "🚨 ALARM",
    "OK": "✅ OK",
    "INSUFFICIENT_DATA": "⚠️ INSUFFICIENT_DATA",
}

KST = timezone(timedelta(hours=9), "KST")


def lambda_handler(event: dict[str, Any], context: Any) -> dict[str, Any]:
    # 한 레코드라도 실패하면 예외를 그대로 올려 SNS가 인보케이션을 재시도하게 한다.
    # 재시도되면 앞서 성공한 레코드가 중복 전송될 수 있지만 알람 누락보다 낫다.
    sent_count = 0
    for record in event.get("Records", []):
        _process_record(record.get("Sns", {}))
        sent_count += 1
    return {"sentCount": sent_count}


def _process_record(sns: dict[str, Any]) -> None:
    topic_arn = sns.get("TopicArn", "")
    topic_name = topic_arn.rsplit(":", 1)[-1]
    if topic_name not in TOPICS:
        LOGGER.error(
            json.dumps(
                {
                    "event": "alarm_notification_failed",
                    "reason": "unknown_topic",
                    "topicArn": topic_arn,
                }
            )
        )
        raise ValueError(f"unknown SNS topic: {topic_arn}")

    environment, webhook_env_name = TOPICS[topic_name]
    webhook_url = _webhook_url(webhook_env_name)

    alarm = _parse_alarm(sns.get("Message", ""))
    if alarm is None:
        payload = _plain_payload(environment, sns)
        alarm_name = sns.get("Subject") or "plain-message"
        state = "PLAIN"
    else:
        payload = _alarm_payload(environment, alarm, topic_arn, sns)
        alarm_name = alarm["AlarmName"]
        state = alarm.get("NewStateValue", "UNKNOWN")

    _post(webhook_url, payload, alarm_name, environment)
    LOGGER.info(
        json.dumps(
            {
                "event": "alarm_notification_sent",
                "alarmName": alarm_name,
                "environment": environment,
                "state": state,
            }
        )
    )


def _webhook_url(name: str) -> str:
    # 모듈 로드 시점이 아니라 사용 시점에 검사한다. 한쪽 환경만 설정돼 있어도 다른 환경 알람은 나가야 한다.
    value = os.environ.get(name, "").strip()
    if not value.startswith(SLACK_WEBHOOK_PREFIX):
        raise ValueError(f"{name} must start with {SLACK_WEBHOOK_PREFIX}")
    return value


def _parse_alarm(message: str) -> dict[str, Any] | None:
    """CloudWatch 알람 JSON이 아니면(SNS 콘솔 테스트 메시지 등) None을 돌려준다."""
    try:
        parsed = json.loads(message)
    except (TypeError, ValueError):
        return None
    if isinstance(parsed, dict) and parsed.get("AlarmName"):
        return parsed
    return None


def _alarm_payload(
    environment: str,
    alarm: dict[str, Any],
    topic_arn: str,
    sns: dict[str, Any],
) -> dict[str, Any]:
    state = alarm.get("NewStateValue", "UNKNOWN")
    prefix = STATE_PREFIXES.get(state, f"❔ {state}")
    alarm_name = alarm["AlarmName"]
    link = _console_link(_region_code(alarm, topic_arn), alarm_name)
    changed_at = _to_kst(alarm.get("StateChangeTime") or sns.get("Timestamp"))

    lines = [f"*{prefix}* [{environment}] {_escape(alarm_name)}"]
    description = alarm.get("AlarmDescription")
    if description:
        lines.append(_escape(description))
    lines.append(f"*사유*: {_escape(alarm.get('NewStateReason', '-'))}")
    metric = _metric_label(alarm.get("Trigger"))
    if metric:
        lines.append(f"*지표*: {_escape(metric)}")
    lines.append(f"*시각*: {changed_at}")
    body = _truncate("\n".join(lines), SECTION_BODY_MAX_LENGTH)
    section_text = f"{body}\n<{link}|CloudWatch 콘솔에서 보기>"

    return {
        "text": f"{prefix} [{environment}] {_escape(alarm_name)}",
        "blocks": [
            {"type": "section", "text": {"type": "mrkdwn", "text": section_text}}
        ],
    }


def _plain_payload(environment: str, sns: dict[str, Any]) -> dict[str, Any]:
    subject = _escape(sns.get("Subject") or "(제목 없음)")
    message = _escape(_truncate(str(sns.get("Message", "")), PLAIN_MESSAGE_MAX_LENGTH))
    return {"text": f"[{environment}] {subject}\n{message}"}


def _region_code(alarm: dict[str, Any], topic_arn: str) -> str:
    # 알람 JSON의 Region은 "Asia Pacific (Seoul)" 같은 표시 이름이라 링크에 쓸 수 없다.
    # AlarmArn 또는 토픽 ARN(arn:aws:sns:{region}:...)에서 코드를 얻는다.
    for arn in (alarm.get("AlarmArn", ""), topic_arn):
        parts = arn.split(":")
        if len(parts) > 3 and parts[3]:
            return parts[3]
    return "ap-northeast-2"


def _console_link(region: str, alarm_name: str) -> str:
    encoded = urllib.parse.quote(alarm_name, safe="")
    return (
        f"https://{region}.console.aws.amazon.com/cloudwatch/home"
        f"?region={region}#alarmsV2:alarm/{encoded}"
    )


def _to_kst(timestamp: str | None) -> str:
    if not timestamp:
        return "-"
    try:
        parsed = datetime.fromisoformat(timestamp)
    except ValueError:
        return timestamp
    if parsed.tzinfo is None:
        parsed = parsed.replace(tzinfo=timezone.utc)
    return parsed.astimezone(KST).strftime("%Y-%m-%d %H:%M:%S KST")


def _metric_label(trigger: Any) -> str | None:
    if not isinstance(trigger, dict):
        return None
    if trigger.get("MetricName"):
        return f"{trigger.get('Namespace', '-')}/{trigger['MetricName']}"
    # 메트릭 수학식 알람은 MetricName 대신 Metrics 배열을 가진다.
    metrics = trigger.get("Metrics")
    if isinstance(metrics, list):
        labels = [
            metric.get("Label") or metric.get("Id")
            for metric in metrics
            if isinstance(metric, dict) and metric.get("ReturnData", True)
        ]
        labels = [label for label in labels if label]
        if labels:
            return ", ".join(labels)
    return None


def _truncate(text: str, limit: int) -> str:
    return text if len(text) <= limit else text[: limit - 1] + "…"


def _escape(text: str) -> str:
    # Slack mrkdwn이 해석하는 세 문자만 이스케이프한다.
    return str(text).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")


def _post(
    webhook_url: str,
    payload: dict[str, Any],
    alarm_name: str,
    environment: str,
) -> None:
    request = urllib.request.Request(
        webhook_url,
        data=json.dumps(payload).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    status: int | None = None
    error: str | None = None
    try:
        with urllib.request.urlopen(request, timeout=REQUEST_TIMEOUT_SECONDS) as response:
            status = response.status
    except urllib.error.HTTPError as exception:
        status = exception.code
        error = "http_error"
    except (urllib.error.URLError, OSError) as exception:
        # 예외 문자열에 URL이 섞일 수 있어 타입 이름만 남긴다.
        error = type(exception).__name__

    if error is None and status is not None and 200 <= status < 300:
        return

    LOGGER.error(
        json.dumps(
            {
                "event": "alarm_notification_failed",
                "alarmName": alarm_name,
                "environment": environment,
                "status": status,
                "error": error,
            }
        )
    )
    raise RuntimeError(f"Slack webhook request failed: status={status} error={error}")
