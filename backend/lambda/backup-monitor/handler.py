"""팀 계정에서 전체 백업 완료 기록을 확인한다. 파일 수정·삭제·복구는 하지 않는다."""

import json
import logging
import os
import re
from datetime import datetime, timedelta, timezone

LOGGER = logging.getLogger()
LOGGER.setLevel(logging.INFO)
KST = timezone(timedelta(hours=9))
UTC = timezone.utc
NAMESPACE = "Chalkak/Backup"
RUN_PATTERN = re.compile(r"chalkak-backup-\d{8}T\d{6}Z-[A-Za-z0-9]{8}")
MAX_RECORD_BYTES = 4096
MAX_CANDIDATES = 1000
MONITOR_WINDOW_SECONDS = 3600


def backup_budget(runtime, margin):
    if not 1 <= runtime <= 42000 or not 0 <= margin <= 1800:
        raise ValueError("실행 상한·여유 시간을 확인하세요")
    # 예약 래퍼의 대기 상한 + 실행 상한 + 강제 종료 여유.
    return 2 * runtime + 10 + margin


def settings(environ):
    bucket = environ["BACKUP_BUCKET"]
    prefix = environ["RUN_PREFIX"]
    monitor = environ["MONITOR_NAME"]
    runtime = int(environ["BACKUP_MAX_RUNTIME_SECONDS"])
    margin = int(environ["COMPLETION_MARGIN_SECONDS"])
    if not re.fullmatch(r"[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]", bucket):
        raise ValueError("백업 버킷 이름을 확인하세요")
    if not re.fullmatch(r"[A-Za-z0-9_-]+(?:/[A-Za-z0-9_-]+)*/runs", prefix):
        raise ValueError("회차 경로는 prefix/runs 형식이어야 합니다")
    if not monitor:
        raise ValueError("감시 이름을 확인하세요")
    budget = backup_budget(runtime, margin)
    return bucket, prefix, monitor, runtime, budget


def expected_window(now, runtime, budget):
    if now.tzinfo is None:
        raise ValueError("시간대가 있는 시각이 필요합니다")
    now = now.astimezone(KST)
    scheduled = now.replace(hour=2, minute=0, second=0, microsecond=0)
    if now < scheduled + timedelta(seconds=budget):
        scheduled -= timedelta(days=1)
    deadline = scheduled + timedelta(seconds=budget)
    if not deadline <= now < deadline + timedelta(seconds=MONITOR_WINDOW_SECONDS):
        return None
    # 02시 직전 시작한 전체 수동 백업 때문에 예약이 생략된 경우도 완료되면 인정한다.
    return (scheduled - timedelta(seconds=runtime)).astimezone(UTC), scheduled.astimezone(UTC)


def read_record(s3, bucket, key):
    try:
        response = s3.get_object(Bucket=bucket, Key=key)
    except Exception as error:
        # SDK 오류 코드를 구분한다. 404는 불완전 회차, 접근 거부·통신 오류는 감시 실패다.
        code = getattr(error, "response", {}).get("Error", {}).get("Code")
        if code in {"NoSuchKey", "NotFound", "404"}:
            raise ValueError("완료 회차의 기록 없음") from None
        raise
    body = response["Body"]
    try:
        if response["ContentLength"] > MAX_RECORD_BYTES:
            raise ValueError("완료 기록 크기 초과")
        content = body.read(MAX_RECORD_BYTES + 1)
        if len(content) > MAX_RECORD_BYTES:
            raise ValueError("완료 기록 크기 초과")
    finally:
        body.close()
    result = {}
    for line in content.decode("utf-8").splitlines():
        name, separator, value = line.partition("=")
        if not separator or not name or name in result:
            raise ValueError("완료 기록 형식 오류")
        result[name] = value
    return result, response["LastModified"]


def parse_time(value):
    # 셸이 기록하는 UTC 형식만 허용한다. 현지 시각을 UTC로 추측하지 않는다.
    return datetime.strptime(value, "%Y-%m-%dT%H:%M:%SZ").replace(tzinfo=UTC)


def valid_completion(s3, bucket, directory, run, earliest, scheduled, now):
    try:
        complete, uploaded = read_record(s3, bucket, directory + "/COMPLETE")
        if complete.get("run") != run or complete.get("selection") != "all":
            return False
        result, _ = read_record(s3, bucket, directory + "/RESULT")
        if (result.get("format") != "chalkak-backup-v1" or result.get("run") != run
                or result.get("selection") != "all" or result.get("db_exit") != "0"
                or result.get("images_exit") != "0" or result.get("interrupted") != "false"):
            return False
        started = parse_time(result["started_at"])
        finished = parse_time(result["finished_at"])
        verified = parse_time(complete["verified_at"])
        return (earliest <= started <= finished <= verified <= uploaded <= now
                and verified >= scheduled)
    except (ValueError, KeyError, UnicodeError):
        # 불완전·잘못된 기록은 성공으로 인정하지 않고 다음 완료 회차를 찾는다.
        return False
    # S3 접근 거부·네트워크 오류는 누락과 다르다. 예외를 올려 감시 정상 지표를 남기지 않는다.


def find_completion(s3, bucket, prefix, earliest, scheduled, now):
    day = earliest.date()
    candidates = 0
    while day <= now.date():
        date_prefix = f"{prefix}/chalkak-backup-{day:%Y%m%d}T"
        pages = s3.get_paginator("list_objects_v2").paginate(Bucket=bucket, Prefix=date_prefix)
        for page in pages:
            for item in page.get("Contents", []):
                key = item["Key"]
                relative = key[len(prefix) + 1:]
                run, separator, filename = relative.partition("/")
                if not separator or filename != "COMPLETE" or not RUN_PATTERN.fullmatch(run):
                    continue
                if not scheduled <= item["LastModified"] <= now:
                    continue
                candidates += 1
                if candidates > MAX_CANDIDATES:
                    raise RuntimeError("감시 후보가 너무 많습니다. 회차 경로를 확인하세요")
                if valid_completion(s3, bucket, f"{prefix}/{run}", run, earliest, scheduled, now):
                    return run
        day += timedelta(days=1)
    return None


def check(s3, cloudwatch, environ, now):
    bucket, prefix, monitor, runtime, budget = settings(environ)
    if now.tzinfo is None:
        raise ValueError("시간대가 있는 시각이 필요합니다")
    now = now.astimezone(UTC)
    window = expected_window(now, runtime, budget)
    if window is None:
        outcome = {"event": "backup_monitor_skipped", "reason": "outside_monitor_window"}
        LOGGER.info(json.dumps(outcome))
        return outcome
    earliest, scheduled = window
    run = find_completion(s3, bucket, prefix, earliest, scheduled, now)
    dimensions = [{"Name": "Monitor", "Value": monitor}]
    # 동일한 요청에 두 지표를 묶는다. API 실패 시 감시 정상 기록도 성공으로 간주하지 않는다.
    cloudwatch.put_metric_data(Namespace=NAMESPACE, MetricData=[
        {"MetricName": "BackupOverdue", "Dimensions": dimensions, "Value": int(run is None),
         "Unit": "Count", "Timestamp": now},
        {"MetricName": "MonitorHealthy", "Dimensions": dimensions, "Value": 1,
         "Unit": "Count", "Timestamp": now},
    ])
    outcome = {"event": "backup_completion_checked", "overdue": run is None,
               "expected_schedule": scheduled.isoformat(), "run": run}
    LOGGER.info(json.dumps(outcome))
    return outcome


def lambda_handler(event, context):
    import boto3
    from botocore.config import Config

    config = Config(connect_timeout=3, read_timeout=5, retries={"total_max_attempts": 2})
    # 지연·재시도된 이벤트의 시각이 아니라 실제 실행 시각을 사용한다.
    return check(boto3.client("s3", config=config), boto3.client("cloudwatch", config=config),
                 os.environ, datetime.now(UTC))
