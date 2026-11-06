import copy
import importlib.util
import io
import sys
import types
from pathlib import Path
import unittest
from datetime import datetime, timedelta, timezone
from unittest.mock import Mock, patch

SPEC = importlib.util.spec_from_file_location("backup_monitor", Path(__file__).parents[1] / "handler.py")
monitor = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(monitor)
UTC = timezone.utc
KST = timezone(timedelta(hours=9))
ENV = {"BACKUP_BUCKET": "chalkak-backup-186101267768", "RUN_PREFIX": "chalkak/runs",
       "MONITOR_NAME": "chalkak-backup-monitor", "BACKUP_MAX_RUNTIME_SECONDS": "3600",
       "COMPLETION_MARGIN_SECONDS": "1800"}
# 02:00 + 대기 1시간 + 실행 1시간 + 종료 10초 + 여유 30분 = 04:30:10.
NOW = datetime(2026, 11, 5, 5, tzinfo=KST).astimezone(UTC)
START = datetime(2026, 11, 5, 2, tzinfo=KST).astimezone(UTC)
RUN = f"chalkak-backup-{START:%Y%m%dT%H%M%SZ}-Ab123456"
DIRECTORY = f"chalkak/runs/{RUN}"


def stamp(value):
    return value.strftime("%Y-%m-%dT%H:%M:%SZ")


def records(start=START, verified=None, selection="all", run=RUN):
    verified = verified or start + timedelta(minutes=20)
    complete = f"run={run}\nselection={selection}\nverified_at={stamp(verified)}\n"
    result = (f"format=chalkak-backup-v1\nrun={run}\nselection={selection}\n"
              f"started_at={stamp(start)}\nfinished_at={stamp(verified - timedelta(seconds=1))}\n"
              "db_exit=0\nimages_exit=0\ninterrupted=false\n")
    return {"COMPLETE": complete, "RESULT": result}, verified


class FakeS3:
    def __init__(self, data=None, uploaded=None, pages=None):
        self.data, self.uploaded = records() if data is None else (data, uploaded)
        self.pages = pages
        self.prefixes = []
        self.closed = []

    def get_paginator(self, operation):
        assert operation == "list_objects_v2"
        return self

    def paginate(self, Bucket, Prefix):
        self.prefixes.append(Prefix)
        if self.pages is not None:
            return self.pages
        if not DIRECTORY.startswith(Prefix):
            return [{}]
        return [{"Contents": [{"Key": DIRECTORY + "/COMPLETE", "LastModified": self.uploaded}]}]

    def get_object(self, Bucket, Key):
        name = Key.rsplit("/", 1)[1]
        body = io.BytesIO(self.data[name].encode())
        self.closed.append(body)
        return {"Body": body, "ContentLength": len(self.data[name].encode()), "LastModified": self.uploaded}


class CompletionTest(unittest.TestCase):
    def check(self, s3=None, now=NOW, env=None):
        cw = Mock()
        result = monitor.check(s3 or FakeS3(), cw, env or ENV, now)
        metrics = cw.put_metric_data.call_args.kwargs
        self.assertEqual(metrics["Namespace"], "Chalkak/Backup")
        self.assertEqual(metrics["MetricData"][0]["Value"], int(result["overdue"]))
        self.assertEqual(metrics["MetricData"][1]["Value"], 1)
        self.assertEqual(metrics["MetricData"][0]["Dimensions"],
                         [{"Name": "Monitor", "Value": ENV["MONITOR_NAME"]}])
        return result

    def test_full_completion_and_utc_date_prefix(self):
        s3 = FakeS3()
        result = self.check(s3)
        self.assertFalse(result["overdue"])
        self.assertEqual(result["run"], RUN)
        self.assertEqual(s3.prefixes, ["chalkak/runs/chalkak-backup-20261104T"])
        self.assertTrue(all(body.closed for body in s3.closed))

    def test_outside_window_skips_s3_and_metrics(self):
        deadline = START + timedelta(seconds=9010)
        for now in [START, deadline - timedelta(microseconds=1),
                    deadline + timedelta(hours=1), START + timedelta(hours=20)]:
            with self.subTest(now=now):
                s3, cw = Mock(), Mock()
                result = monitor.check(s3, cw, ENV, now)
                self.assertEqual(result["reason"], "outside_monitor_window")
                s3.get_paginator.assert_not_called()
                s3.get_object.assert_not_called()
                cw.put_metric_data.assert_not_called()

    def test_deadline_inclusive_and_end_exclusive(self):
        deadline = START + timedelta(seconds=9010)
        self.assertEqual(monitor.expected_window(deadline, 3600, 9010)[1], START)
        self.assertFalse(self.check(now=deadline)["overdue"])
        self.assertFalse(self.check(now=deadline + timedelta(hours=1, microseconds=-1))["overdue"])
        self.assertIsNone(monitor.expected_window(deadline + timedelta(hours=1), 3600, 9010))

    def test_window_crosses_midnight_and_year(self):
        # 실행 상한이 길면 전날 02시 예약의 마감과 확인 구간이 다음 날에 온다.
        now = datetime(2027, 1, 1, 2, 10, tzinfo=KST)
        budget = monitor.backup_budget(42000, 1800)
        earliest, scheduled = monitor.expected_window(now, 42000, budget)
        self.assertEqual(scheduled.astimezone(KST).strftime("%Y-%m-%d %H:%M"), "2026-12-31 02:00")
        self.assertEqual(earliest, scheduled - timedelta(seconds=42000))
        self.assertIsNone(monitor.expected_window(now + timedelta(hours=1), 42000, budget))

    def test_missing_and_partial_do_not_count(self):
        self.assertTrue(self.check(FakeS3(pages=[{}]))["overdue"])
        for selection in ["db", "images"]:
            with self.subTest(selection=selection):
                data, uploaded = records(selection=selection)
                self.assertTrue(self.check(FakeS3(data, uploaded))["overdue"])

    def test_errors_and_inconsistent_records_do_not_count(self):
        data, uploaded = records()
        mutations = [
            ("COMPLETE", "selection=all", "selection=db"),
            ("COMPLETE", f"run={RUN}", "run=other"),
            ("RESULT", "db_exit=0", "db_exit=1"),
            ("RESULT", "images_exit=0", "images_exit=143"),
            ("RESULT", "interrupted=false", "interrupted=true"),
            ("RESULT", "format=chalkak-backup-v1", "format=other"),
            ("RESULT", "selection=all", "selection=images"),
            ("COMPLETE", "verified_at=", "bad_time="),
            ("RESULT", "db_exit=0", "db_exit=0\ndb_exit=0"),
        ]
        for name, old, new in mutations:
            with self.subTest(new=new):
                changed = copy.copy(data)
                changed[name] = changed[name].replace(old, new)
                self.assertTrue(self.check(FakeS3(changed, uploaded))["overdue"])

    def test_old_future_and_reversed_times_do_not_count(self):
        for started, verified, uploaded in [
            (START - timedelta(hours=2), START + timedelta(minutes=20), START + timedelta(minutes=20)),
            (START, NOW + timedelta(seconds=1), NOW + timedelta(seconds=1)),
            (START, START - timedelta(seconds=1), START),
            (START, START + timedelta(minutes=20), START + timedelta(minutes=10)),
        ]:
            with self.subTest(started=started, verified=verified):
                data, _ = records(started, verified)
                self.assertTrue(self.check(FakeS3(data, uploaded))["overdue"])

    def test_running_full_at_two_oclock_counts_when_it_finishes(self):
        data, uploaded = records(START - timedelta(minutes=10), START + timedelta(minutes=10))
        self.assertFalse(self.check(FakeS3(data, uploaded))["overdue"])

    def test_completion_before_two_oclock_does_not_count(self):
        data, uploaded = records(START - timedelta(minutes=30), START - timedelta(minutes=10))
        self.assertTrue(self.check(FakeS3(data, uploaded))["overdue"])

    def test_listing_pagination_continues_after_invalid_record(self):
        pages = [{"Contents": [{"Key": DIRECTORY + "/RESULT", "LastModified": START}]},
                 {"Contents": [{"Key": DIRECTORY + "/COMPLETE", "LastModified": START + timedelta(minutes=20)}]}]
        self.assertFalse(self.check(FakeS3(pages=pages))["overdue"])

    def test_unexpected_paths_are_not_read(self):
        for suffix in ["other/COMPLETE", RUN + "/nested/COMPLETE", RUN + "/image.log"]:
            s3 = FakeS3(pages=[{"Contents": [{"Key": "chalkak/runs/" + suffix, "LastModified": START}]}])
            s3.get_object = Mock(side_effect=AssertionError("이 경로는 읽으면 안 됩니다"))
            self.assertTrue(self.check(s3)["overdue"])

    def test_api_failures_do_not_emit_healthy_metric(self):
        for operation in ["get_paginator", "get_object"]:
            with self.subTest(operation=operation):
                s3, cw = FakeS3(), Mock()
                setattr(s3, operation, Mock(side_effect=RuntimeError("접근 거부 또는 네트워크 오류")))
                with self.assertRaises(RuntimeError):
                    monitor.check(s3, cw, ENV, NOW)
                cw.put_metric_data.assert_not_called()

    def test_s3_missing_key_is_overdue_but_access_denied_is_monitor_failure(self):
        class S3Error(Exception):
            def __init__(self, code):
                self.response = {"Error": {"Code": code}}

        for code in ["NoSuchKey", "NotFound", "404"]:
            with self.subTest(code=code):
                s3 = FakeS3()
                s3.get_object = Mock(side_effect=S3Error(code))
                self.assertTrue(self.check(s3)["overdue"])
        s3, cw = FakeS3(), Mock()
        s3.get_object = Mock(side_effect=S3Error("AccessDenied"))
        with self.assertRaises(S3Error):
            monitor.check(s3, cw, ENV, NOW)
        cw.put_metric_data.assert_not_called()

    def test_metric_api_failure_propagates(self):
        cw = Mock()
        cw.put_metric_data.side_effect = RuntimeError("지표 쓰기 실패")
        with self.assertRaises(RuntimeError):
            monitor.check(FakeS3(), cw, ENV, NOW)

    def test_oversized_record_is_closed_and_rejected(self):
        data, uploaded = records()
        data["COMPLETE"] = "x" * 4097
        s3 = FakeS3(data, uploaded)
        self.assertTrue(self.check(s3)["overdue"])
        self.assertTrue(all(body.closed for body in s3.closed))

    def test_candidate_limit_fails_instead_of_silent_success(self):
        pages = [{"Contents": [{"Key": DIRECTORY + "/COMPLETE", "LastModified": START}] * 2}]
        data, uploaded = records(selection="db")
        with patch.object(monitor, "MAX_CANDIDATES", 1), self.assertRaises(RuntimeError):
            self.check(FakeS3(data, uploaded, pages))

    def test_malformed_settings_fail_before_s3_access(self):
        for key, value in [("RUN_PREFIX", "../runs"), ("BACKUP_BUCKET", "INVALID"),
                           ("BACKUP_MAX_RUNTIME_SECONDS", "0"), ("BACKUP_MAX_RUNTIME_SECONDS", "43000"),
                           ("COMPLETION_MARGIN_SECONDS", "-1"), ("MONITOR_NAME", "")]:
            with self.subTest(key=key, value=value):
                env = dict(ENV, **{key: value})
                s3, cw = Mock(), Mock()
                with self.assertRaises(ValueError):
                    monitor.check(s3, cw, env, NOW)
                s3.get_paginator.assert_not_called()
                cw.put_metric_data.assert_not_called()

    def test_old_record_copied_now_does_not_count(self):
        data, _ = records(START - timedelta(days=1))
        self.assertTrue(self.check(FakeS3(data, NOW))["overdue"])

    def test_missing_result_is_not_success(self):
        data, uploaded = records()
        del data["RESULT"]
        self.assertTrue(self.check(FakeS3(data, uploaded))["overdue"])

    def test_query_covers_utc_midnight(self):
        s3 = FakeS3(pages=[{}])
        late_now = datetime(2026, 11, 5, 23, tzinfo=KST).astimezone(UTC)
        self.assertIsNone(monitor.find_completion(s3, ENV["BACKUP_BUCKET"], ENV["RUN_PREFIX"],
                                                   START - timedelta(hours=1), START, late_now))
        self.assertEqual(s3.prefixes, ["chalkak/runs/chalkak-backup-20261104T",
                                      "chalkak/runs/chalkak-backup-20261105T"])

    def test_template_maximum_budget_remains_under_one_day(self):
        env = dict(ENV, BACKUP_MAX_RUNTIME_SECONDS="42000")
        self.assertEqual(monitor.settings(env)[4], 85810)

    def test_handler_uses_execution_time_not_input_time(self):
        boto3 = types.ModuleType("boto3")
        boto3.client = Mock(side_effect=[Mock(), Mock()])
        botocore = types.ModuleType("botocore.config")
        botocore.Config = Mock()
        modules = {"boto3": boto3, "botocore": types.ModuleType("botocore"), "botocore.config": botocore}
        before = datetime.now(UTC)
        with patch.dict(sys.modules, modules), patch.object(monitor, "check", return_value={}) as check:
            monitor.lambda_handler({"time": "2000-01-01T00:00:00Z"}, None)
        self.assertLessEqual(before, check.call_args.args[3])
        self.assertLessEqual(check.call_args.args[3], datetime.now(UTC))
        self.assertEqual(boto3.client.call_args_list[0].args[0], "s3")
        self.assertEqual(boto3.client.call_args_list[1].args[0], "cloudwatch")

    def test_naive_time_rejected(self):
        with self.assertRaises(ValueError):
            self.check(now=datetime(2026, 11, 5))


if __name__ == "__main__":
    unittest.main()
