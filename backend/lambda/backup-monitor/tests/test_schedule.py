"""실행 상한을 바꿔도 예약과 경보가 동일한 1시간 구간을 사용하는지 검증한다."""

import importlib.util
import json
from pathlib import Path
import subprocess
import sys
import unittest
from datetime import datetime, timedelta, timezone
from unittest.mock import patch


ROOT = Path(__file__).parents[1]
with patch.object(sys, "path", [str(ROOT), *sys.path]):
    SPEC = importlib.util.spec_from_file_location("monitor_configure", ROOT / "configure.py")
    configure = importlib.util.module_from_spec(SPEC)
    SPEC.loader.exec_module(configure)
    import handler

KST = timezone(timedelta(hours=9))


class ScheduleTest(unittest.TestCase):
    def settings(self, runtime=3600, margin=1800):
        return {entry["ParameterKey"]: entry["ParameterValue"]
                for entry in configure.parameters(runtime, margin)}

    def slots(self, values):
        result = []
        for index in range(1, 5):
            minute, hour, *daily = values[f"CheckSchedule{index}"][5:-1].split()
            self.assertEqual(daily, ["*", "*", "?", "*"])
            result.append(int(hour) * 60 + int(minute))
        return result

    def test_one_hour_example_has_four_checks_after_deadline(self):
        values = self.settings()
        self.assertEqual(self.slots(values), [271, 286, 301, 316])
        self.assertEqual(values["MaxRuntimeSeconds"], "3600")
        self.assertEqual(values["CompletionMarginSeconds"], "1800")

    def test_all_checks_stay_in_window_and_fifteen_minutes_apart(self):
        for runtime in [1, 25, 26, 3600, 12345, 35000, 42000]:
            for margin in [0, 1800]:
                with self.subTest(runtime=runtime, margin=margin):
                    start = datetime(2026, 12, 31, 2, tzinfo=KST)
                    budget = handler.backup_budget(runtime, margin)
                    deadline = start + timedelta(seconds=budget)
                    previous = None
                    for minute in self.slots(self.settings(runtime, margin)):
                        slot = deadline.replace(hour=minute // 60, minute=minute % 60, second=0)
                        if slot < deadline:
                            slot += timedelta(days=1)
                        self.assertLessEqual(deadline, slot)
                        self.assertLess(slot, deadline + timedelta(hours=1))
                        # 예약의 분 내 전달과 2분 Lambda 실행을 고려해도 대상 구간 안이다.
                        latest = slot + timedelta(minutes=3)
                        self.assertEqual(handler.expected_window(latest, runtime, budget)[1], start.astimezone(timezone.utc))
                        if previous is not None:
                            self.assertEqual(slot - previous, timedelta(minutes=15))
                        previous = slot

    def test_health_alarm_only_evaluates_four_expected_utc_periods(self):
        for runtime, margin in [(3600, 1800), (12345, 0), (42000, 1800), (13000, 0)]:
            values = self.settings(runtime, margin)
            slots = self.slots(values)
            expected = {((minute - 540) % 1440) // 15 * 15 for minute in slots}
            expression = values["HealthWindowExpression"]
            condition = expression[3:expression.index(",FILL")]
            actual = set()
            for minute in range(0, 1440, 15):
                formula = condition.replace("(HOUR(healthy)*60+MINUTE(healthy))", str(minute))
                formula = formula.replace("AND", "and").replace("OR", "or")
                # 생성한 상수·비교식만 평가하며 외부 입력은 사용하지 않는다.
                if eval(formula, {"__builtins__": {}}):
                    actual.add(minute)
            self.assertEqual(actual, expected)
            self.assertTrue(expression.endswith(",FILL(healthy,0),1)"))

    def test_utc_midnight_wrap_uses_or(self):
        self.assertIn(" OR ", self.settings(12345, 0)["HealthWindowExpression"])

    def test_cli_prints_deploy_parameter_json_without_aws_calls(self):
        completed = subprocess.run([sys.executable, str(ROOT / "configure.py"),
                                    "--max-runtime-seconds", "3600", "--code-key", "monitor-code/test.zip"],
                                   capture_output=True, text=True, check=True)
        values = json.loads(completed.stdout)
        self.assertEqual(values[:7], configure.parameters(3600, 1800))
        self.assertEqual(values[-1], {"ParameterKey": "EnableNotifications", "ParameterValue": "false"})

    def test_cli_rejects_notification_without_destination(self):
        completed = subprocess.run([sys.executable, str(ROOT / "configure.py"),
                                    "--max-runtime-seconds", "3600", "--code-key", "monitor-code/test.zip",
                                    "--enable-notifications"], capture_output=True, text=True)
        self.assertNotEqual(completed.returncode, 0)
        self.assertEqual(completed.stdout, "")

    def test_cli_with_verified_destination_enables_notifications(self):
        topic = "arn:aws:sns:ap-northeast-2:186101267768:chalkak-prod-alarms"
        completed = subprocess.run([sys.executable, str(ROOT / "configure.py"),
                                    "--max-runtime-seconds", "3600", "--code-key", "monitor-code/test.zip",
                                    "--enable-notifications", "--alarm-topic-arn", topic],
                                   capture_output=True, text=True, check=True)
        values = {entry["ParameterKey"]: entry["ParameterValue"] for entry in json.loads(completed.stdout)}
        self.assertEqual(values["AlarmTopicArn"], topic)
        self.assertEqual(values["EnableNotifications"], "true")

    def test_invalid_values_do_not_generate_partial_parameters(self):
        for runtime, margin in [(0, 0), (42001, 0), (3600, -1), (3600, 1801)]:
            with self.subTest(runtime=runtime, margin=margin), self.assertRaises(ValueError):
                configure.parameters(runtime, margin)


if __name__ == "__main__":
    unittest.main()
