import json
import unittest
import urllib.error
from unittest.mock import MagicMock, patch

import handler

DEV_URL = "https://hooks.slack.com/services/T000/B000/dev-secret"
PROD_URL = "https://hooks.slack.com/services/T000/B000/prod-secret"
ENVIRONMENT = {"DEV_SLACK_WEBHOOK_URL": DEV_URL, "PROD_SLACK_WEBHOOK_URL": PROD_URL}

DEV_TOPIC = "arn:aws:sns:ap-northeast-2:123456789012:chalkak-dev-alarms"
PROD_TOPIC = "arn:aws:sns:ap-northeast-2:123456789012:chalkak-prod-alarms"


def alarm_message(**overrides) -> dict:
    message = {
        "AlarmName": "chalkak-prod-ec2-cpu high",
        "AlarmDescription": "CPU 80% 초과. 먼저 확인: 트래픽 급증 여부",
        "AWSAccountId": "123456789012",
        "NewStateValue": "ALARM",
        "NewStateReason": "Threshold Crossed: 1 datapoint [91.0] was greater than 80.0",
        "StateChangeTime": "2026-09-30T12:00:00.000+0000",
        "Region": "Asia Pacific (Seoul)",
        "Trigger": {
            "MetricName": "CPUUtilization",
            "Namespace": "AWS/EC2",
        },
    }
    message.update(overrides)
    return message


def sns_event(topic_arn: str, message: str, subject: str = "subject") -> dict:
    return {
        "Records": [
            {
                "Sns": {
                    "TopicArn": topic_arn,
                    "Message": message,
                    "Subject": subject,
                    "Timestamp": "2026-09-30T12:00:01.000Z",
                }
            }
        ]
    }


def ok_response(status: int = 200) -> MagicMock:
    response = MagicMock()
    response.status = status
    response.__enter__.return_value = response
    return response


def posted(urlopen: MagicMock) -> tuple[str, dict]:
    request = urlopen.call_args.args[0]
    return request.full_url, json.loads(request.data.decode("utf-8"))


class LambdaHandlerTest(unittest.TestCase):
    def setUp(self) -> None:
        patcher = patch.dict("os.environ", ENVIRONMENT, clear=True)
        patcher.start()
        self.addCleanup(patcher.stop)

    @patch("handler.urllib.request.urlopen")
    def test_alarm_on_prod_topic_posts_to_prod_webhook(self, urlopen) -> None:
        urlopen.return_value = ok_response()

        handler.lambda_handler(sns_event(PROD_TOPIC, json.dumps(alarm_message())), None)

        url, payload = posted(urlopen)
        self.assertEqual(PROD_URL, url)
        text = payload["blocks"][0]["text"]["text"]
        self.assertIn("🚨 ALARM", payload["text"])
        self.assertIn("[prod]", payload["text"])
        self.assertIn("chalkak-prod-ec2-cpu high", text)
        self.assertIn("먼저 확인: 트래픽 급증 여부", text)
        self.assertIn("Threshold Crossed", text)
        self.assertIn("2026-09-30 21:00:00 KST", text)
        self.assertIn("AWS/EC2/CPUUtilization", text)
        self.assertIn(
            "https://ap-northeast-2.console.aws.amazon.com/cloudwatch/home"
            "?region=ap-northeast-2#alarmsV2:alarm/chalkak-prod-ec2-cpu%20high",
            text,
        )
        self.assertEqual(5, urlopen.call_args.kwargs["timeout"])

    @patch("handler.urllib.request.urlopen")
    def test_ok_on_dev_topic_posts_to_dev_webhook_with_ok_prefix(self, urlopen) -> None:
        urlopen.return_value = ok_response()
        message = alarm_message(NewStateValue="OK")

        handler.lambda_handler(sns_event(DEV_TOPIC, json.dumps(message)), None)

        url, payload = posted(urlopen)
        self.assertEqual(DEV_URL, url)
        self.assertTrue(payload["text"].startswith("✅ OK"))
        self.assertIn("[dev]", payload["text"])

    @patch("handler.urllib.request.urlopen")
    def test_unknown_topic_raises(self, urlopen) -> None:
        topic = "arn:aws:sns:ap-northeast-2:123456789012:other-topic"

        with self.assertLogs(level="ERROR") as logs:
            with self.assertRaises(ValueError):
                handler.lambda_handler(sns_event(topic, "{}"), None)

        self.assertIn("unknown_topic", logs.output[0])
        urlopen.assert_not_called()

    @patch("handler.urllib.request.urlopen")
    def test_missing_webhook_env_raises_with_clear_message(self, urlopen) -> None:
        with patch.dict("os.environ", {}, clear=True):
            with self.assertRaisesRegex(ValueError, "PROD_SLACK_WEBHOOK_URL must start with"):
                handler.lambda_handler(sns_event(PROD_TOPIC, json.dumps(alarm_message())), None)
        urlopen.assert_not_called()

    @patch("handler.urllib.request.urlopen")
    def test_non_slack_webhook_env_raises(self, urlopen) -> None:
        with patch.dict("os.environ", {"DEV_SLACK_WEBHOOK_URL": "https://example.com/hook"}):
            with self.assertRaisesRegex(ValueError, "DEV_SLACK_WEBHOOK_URL must start with"):
                handler.lambda_handler(sns_event(DEV_TOPIC, "hello"), None)
        urlopen.assert_not_called()

    @patch("handler.urllib.request.urlopen")
    def test_non_json_message_is_sent_as_plain_text(self, urlopen) -> None:
        urlopen.return_value = ok_response()

        handler.lambda_handler(sns_event(DEV_TOPIC, "테스트 메시지", subject="ping"), None)

        url, payload = posted(urlopen)
        self.assertEqual(DEV_URL, url)
        self.assertEqual({"text": "[dev] ping\n테스트 메시지"}, payload)

    @patch("handler.urllib.request.urlopen")
    def test_plain_message_escapes_slack_mentions(self, urlopen) -> None:
        urlopen.return_value = ok_response()

        handler.lambda_handler(sns_event(DEV_TOPIC, "<!channel> a & b", subject="<!here>"), None)

        self.assertEqual(
            {"text": "[dev] &lt;!here&gt;\n&lt;!channel&gt; a &amp; b"}, posted(urlopen)[1]
        )

    @patch("handler.urllib.request.urlopen")
    def test_alarm_name_is_escaped_in_top_level_text(self, urlopen) -> None:
        urlopen.return_value = ok_response()
        message = alarm_message(AlarmName="<!channel> cpu")

        handler.lambda_handler(sns_event(DEV_TOPIC, json.dumps(message)), None)

        self.assertNotIn("<!channel>", posted(urlopen)[1]["text"])

    @patch("handler.urllib.request.urlopen")
    def test_long_reason_is_truncated_below_section_limit(self, urlopen) -> None:
        urlopen.return_value = ok_response()
        message = alarm_message(NewStateReason="x" * 10000)

        handler.lambda_handler(sns_event(DEV_TOPIC, json.dumps(message)), None)

        text = posted(urlopen)[1]["blocks"][0]["text"]["text"]
        self.assertLess(len(text), 3000)
        self.assertIn("…", text)
        self.assertIn("CloudWatch 콘솔에서 보기", text)

    @patch("handler.urllib.request.urlopen")
    def test_slack_http_error_raises_and_logs_never_contain_url(self, urlopen) -> None:
        urlopen.side_effect = urllib.error.HTTPError(PROD_URL, 500, "error", {}, None)

        with self.assertLogs(level="INFO") as logs:
            with self.assertRaises(RuntimeError) as raised:
                handler.lambda_handler(sns_event(PROD_TOPIC, json.dumps(alarm_message())), None)

        output = "\n".join(logs.output) + str(raised.exception)
        self.assertIn("alarm_notification_failed", output)
        self.assertIn("500", output)
        self.assertNotIn("prod-secret", output)
        self.assertNotIn("hooks.slack.com", output)

    @patch("handler.urllib.request.urlopen")
    def test_network_error_raises(self, urlopen) -> None:
        urlopen.side_effect = urllib.error.URLError("timed out")

        with self.assertLogs(level="ERROR"):
            with self.assertRaises(RuntimeError):
                handler.lambda_handler(sns_event(DEV_TOPIC, json.dumps(alarm_message())), None)

    @patch("handler.urllib.request.urlopen")
    def test_non_2xx_response_raises(self, urlopen) -> None:
        urlopen.return_value = ok_response(status=302)

        with self.assertLogs(level="ERROR"):
            with self.assertRaises(RuntimeError):
                handler.lambda_handler(sns_event(DEV_TOPIC, json.dumps(alarm_message())), None)

    @patch("handler.urllib.request.urlopen")
    def test_metric_math_trigger_does_not_crash(self, urlopen) -> None:
        urlopen.return_value = ok_response()
        trigger = {
            "Metrics": [
                {"Id": "e1", "Expression": "m1/m2", "Label": "error rate", "ReturnData": True},
                {"Id": "m1", "ReturnData": False, "MetricStat": {}},
            ]
        }

        handler.lambda_handler(
            sns_event(DEV_TOPIC, json.dumps(alarm_message(Trigger=trigger))), None
        )

        self.assertIn("error rate", posted(urlopen)[1]["blocks"][0]["text"]["text"])

    @patch("handler.urllib.request.urlopen")
    def test_success_logs_sent_event_and_processes_every_record(self, urlopen) -> None:
        urlopen.return_value = ok_response()
        event = sns_event(DEV_TOPIC, json.dumps(alarm_message()))
        event["Records"] += sns_event(PROD_TOPIC, json.dumps(alarm_message()))["Records"]

        with self.assertLogs(level="INFO") as logs:
            result = handler.lambda_handler(event, None)

        self.assertEqual(2, urlopen.call_count)
        self.assertEqual(2, result["sentCount"])
        sent = json.loads(logs.records[0].getMessage())
        self.assertEqual("alarm_notification_sent", sent["event"])
        self.assertEqual("dev", sent["environment"])
        self.assertEqual("ALARM", sent["state"])
        self.assertNotIn("hooks.slack.com", "\n".join(logs.output))


if __name__ == "__main__":
    unittest.main()
