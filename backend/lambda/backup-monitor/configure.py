"""AWS 호출 없이 실행 상한에서 예약 4개와 감시 중단 경보 시간대를 함께 계산한다."""

import argparse
import json
import sys

from handler import backup_budget


def parameters(runtime, margin):
    budget = backup_budget(runtime, margin)
    # Scheduler는 분 단위다. 마감 이전에는 실행하지 않도록 올림한다.
    first_minute = (120 + (budget + 59) // 60) % 1440
    result = {"MaxRuntimeSeconds": str(runtime), "CompletionMarginSeconds": str(margin)}
    for index in range(4):
        minute = (first_minute + 15 * index) % 1440
        result[f"CheckSchedule{index + 1}"] = f"cron({minute % 60} {minute // 60} * * ? *)"

    # CloudWatch의 15분 집계 구간과 예약의 UTC 시각을 맞춘다.
    start = ((first_minute - 540) % 1440) // 15 * 15
    end = (start + 60) % 1440
    clock = "(HOUR(healthy)*60+MINUTE(healthy))"
    operator = "AND" if start < end else "OR"
    condition = f"({clock}>={start}) {operator} ({clock}<{end})"
    result["HealthWindowExpression"] = f"IF({condition},FILL(healthy,0),1)"
    return [{"ParameterKey": key, "ParameterValue": value} for key, value in result.items()]


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--max-runtime-seconds", required=True, type=int)
    parser.add_argument("--completion-margin-seconds", default=1800, type=int)
    parser.add_argument("--code-key", required=True)
    parser.add_argument("--alarm-topic-arn", default="")
    parser.add_argument("--enable-notifications", action="store_true")
    args = parser.parse_args()
    if args.enable_notifications and not args.alarm_topic_arn:
        parser.error("알림 활성화에는 검증한 SNS 토픽 ARN이 필요합니다")
    try:
        values = parameters(args.max_runtime_seconds, args.completion_margin_seconds)
        values.extend([
            {"ParameterKey": "CodeKey", "ParameterValue": args.code_key},
            {"ParameterKey": "AlarmTopicArn", "ParameterValue": args.alarm_topic_arn},
            {"ParameterKey": "EnableNotifications", "ParameterValue": str(args.enable_notifications).lower()},
        ])
        json.dump(values, sys.stdout, indent=2)
        print()
    except ValueError as error:
        parser.error(str(error))
