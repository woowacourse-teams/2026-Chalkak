import base64
import io
import json
import unittest
import threading
from dataclasses import replace
from datetime import datetime, timedelta, timezone
from unittest.mock import Mock

from PIL import Image

from image_processor.jev import JevHttpClient, JevImageError, JevJobProcessor, create_jev_png
from tests.test_post import settings, webp_bytes, animated_webp_bytes


JOB_ID = "0198d999-ff00-7000-8000-000000000001"


def claim(action="INSPECT"):
    return dict(
        action=action, jobId=JOB_ID, callId=JOB_ID,
        deadline=(datetime.now(timezone.utc) + timedelta(seconds=100)).isoformat(),
        model="jev-1.14.0", topic="초록", category="subject",
        questions={name: dict(type="choice", instructions="test", criteria={})
                   for name in ("topic_fit", "image_safety", "image_expression")},
        storageKey=f"chalkak/posts/dev/original/{JOB_ID}.webp",
    )


class JevImageTest(unittest.TestCase):
    def test_keeps_ratio_and_short_side_300_without_padding(self):
        for source, expected in [((1600, 1200), (400, 300)),
                                 ((1200, 1600), (300, 400)),
                                 ((1200, 1200), (300, 300)),
                                 ((40, 30), (400, 300))]:
            with self.subTest(source=source):
                png = create_jev_png(webp_bytes(source, image_format="PNG"))
                with Image.open(io.BytesIO(png)) as result:
                    self.assertEqual(expected, result.size)
                    self.assertEqual("PNG", result.format)
                    self.assertEqual({}, result.info)
                    self.assertEqual(result.getpixel((0, 0)), result.getpixel((expected[0] - 1, 0)))

    def test_rejects_extreme_ratio_animation_corruption_and_pixel_limit(self):
        for image, limit in [(webp_bytes((2000, 300)), 25_000_000),
                             (animated_webp_bytes(), 25_000_000),
                             (b"invalid", 25_000_000),
                             (webp_bytes((40, 30)), 100)]:
            with self.subTest(limit=limit):
                with self.assertRaises(JevImageError):
                    create_jev_png(image, limit)

    def test_preserves_alpha_but_strips_exif(self):
        source = Image.new("RGBA", (400, 300), (10, 20, 30, 80))
        exif = Image.Exif()
        exif[0x010F] = "secret-camera"
        stream = io.BytesIO()
        source.save(stream, format="PNG", exif=exif)
        with Image.open(io.BytesIO(create_jev_png(stream.getvalue()))) as result:
            self.assertEqual("RGBA", result.mode)
            self.assertEqual(80, result.getpixel((0, 0))[3])
            self.assertEqual(0, len(result.getexif()))


class JevWorkerTest(unittest.TestCase):
    def setUp(self):
        self.settings = replace(settings(), jev_work_queue_url="https://sqs.test/queue")
        self.s3 = Mock()
        self.s3.get_object.return_value = {"Body": io.BytesIO(webp_bytes((1600, 1200)))}
        self.sqs = Mock()
        self.callback = Mock()
        self.callback.claim_jev_job.side_effect = [claim(), {"action": "STOP"}]
        self.http = Mock()
        self.http.execute.return_value = dict(rawResponse='{"model":"jev-1.14.0"}',
                                             errorCode=None, httpStatus=200, latencyMs=123,
                                             retryAfterSeconds=None)
        self.worker = JevJobProcessor(self.settings, self.s3, self.sqs, self.callback, self.http)
        self.message = dict(type="JEV_MODERATION", environment="dev", jobId=JOB_ID)

    def test_sends_three_questions_in_one_call_and_persists_result_delivery(self):
        self.worker.process(self.message)
        self.http.execute.assert_called_once()
        body, timeout = self.http.execute.call_args.args
        self.assertEqual(3, len(body["questions"]))
        self.assertNotIn("title", body["state"])
        self.assertNotIn("body", body["state"])
        self.assertEqual(30, timeout)
        ref = body["state"]["photo"]
        with Image.open(io.BytesIO(base64.b64decode(body["assets"][ref]["data_base64"]))) as png:
            self.assertEqual((400, 300), png.size)
        message = json.loads(self.sqs.send_message.call_args.kwargs["MessageBody"])
        self.assertEqual("JEV_RESULT", message["type"])
        self.assertEqual(123, message["result"]["latencyMs"])
        self.callback.complete_jev_call.assert_called_once()

    def test_wait_does_not_sleep_download_or_call_provider(self):
        self.callback.claim_jev_job.side_effect = [{"action": "WAIT"}]
        self.worker.process(self.message)
        self.s3.get_object.assert_not_called()
        self.http.execute.assert_not_called()

    def test_failed_callback_replays_stored_result_without_provider_recall(self):
        self.callback.complete_jev_call.side_effect = OSError("unavailable")
        self.worker.process(self.message)
        durable = json.loads(self.sqs.send_message.call_args.kwargs["MessageBody"])
        self.callback.complete_jev_call.side_effect = None
        self.worker.process(durable)
        self.http.execute.assert_called_once()
        self.callback.complete_jev_call.assert_called_with("dev", JOB_ID, durable["result"])

    def test_final_file_read_failure_is_technical_result_not_image_failure_callback(self):
        self.s3.get_object.side_effect = OSError("missing")
        self.worker.process(self.message)
        self.http.execute.assert_not_called()
        self.callback.failed.assert_not_called()
        result = self.callback.complete_jev_call.call_args.args[2]
        self.assertEqual("IMAGE_READ_ERROR", result["errorCode"])
        self.assertIsNone(result["latencyMs"])

    def test_wrong_environment_storage_key_cannot_be_downloaded(self):
        wrong = claim()
        wrong["storageKey"] = wrong["storageKey"].replace("/dev/", "/prod/")
        self.callback.claim_jev_job.side_effect = [wrong, {"action": "STOP"}]
        self.worker.process(self.message)
        self.s3.get_object.assert_not_called()
        self.http.execute.assert_not_called()

    def test_queue_failure_still_delivers_result_to_backend(self):
        self.sqs.send_message.side_effect = OSError("queue down")
        self.worker.process(self.message)
        self.http.execute.assert_called_once()
        self.callback.complete_jev_call.assert_called_once()


class JevHttpTest(unittest.TestCase):
    def setUp(self):
        self.connection = Mock()
        self.connection.getresponse.return_value.status = 200
        self.connection.getresponse.return_value.read.return_value = b'{"answers":{}}'
        self.connection.getresponse.return_value.getheader.return_value = None
        self.factory = Mock(return_value=self.connection)
        self.client = JevHttpClient("test-key", self.factory)

    def test_fixed_endpoint_image_header_and_bounded_response(self):
        result = self.client.execute({"assets": {"unique": {}}}, 30)
        self.factory.assert_called_once_with("api.typesafe.ai", timeout=3.0)
        args, kwargs = self.connection.request.call_args
        self.assertEqual(("POST", "/v1/systemone"), args)
        self.assertEqual("true", kwargs["headers"]["TypeSafe-Danger-Zone-Images"])
        self.connection.getresponse.return_value.read.assert_called_once_with(65_537)
        self.assertEqual(200, result["httpStatus"])
        self.assertIsNone(result["errorCode"])

    def test_429_keeps_retry_after_for_server_budget(self):
        self.connection.getresponse.return_value.status = 429
        self.connection.getresponse.return_value.getheader.return_value = "10"
        result = self.client.execute({}, 30)
        self.assertEqual("HTTP_ERROR", result["errorCode"])
        self.assertEqual(10, result["retryAfterSeconds"])

    def test_missing_key_makes_no_network_request(self):
        result = JevHttpClient("", self.factory).execute({}, 30)
        self.factory.assert_not_called()
        self.assertEqual("CONFIGURATION_ERROR", result["errorCode"])
        self.assertIsNone(result["latencyMs"])

    def test_timeout_and_oversized_response_have_distinct_failure_codes(self):
        self.connection.connect.side_effect = TimeoutError()
        self.assertEqual("TIMEOUT", self.client.execute({}, 30)["errorCode"])
        self.connection.connect.side_effect = None
        self.connection.getresponse.return_value.read.return_value = b"x" * 65_537
        result = self.client.execute({}, 30)
        self.assertEqual("RESPONSE_TOO_LARGE", result["errorCode"])
        self.assertIsNone(result["rawResponse"])

    def test_total_deadline_interrupts_a_blocked_response_read(self):
        self.connection.getresponse.return_value.read.side_effect = lambda _limit: threading.Event().wait(1)
        result = self.client.execute({}, 0.01)
        self.assertEqual("TIMEOUT", result["errorCode"])
        self.connection.close.assert_called_once()


if __name__ == "__main__":
    unittest.main()
